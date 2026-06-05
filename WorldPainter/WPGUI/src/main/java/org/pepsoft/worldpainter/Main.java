/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import com.jidesoft.plaf.LookAndFeelFactory;
import com.jidesoft.utils.Lm;
import org.intellij.lang.annotations.Language;
import org.pepsoft.util.*;
import org.pepsoft.util.plugins.PluginManager;
import org.pepsoft.worldpainter.biomeschemes.BiomeSchemeManager;
import org.pepsoft.worldpainter.layers.renderers.VoidRenderer;
import org.pepsoft.worldpainter.operations.MouseOrTabletOperation;
import org.pepsoft.worldpainter.plugins.PlatformManager;
import org.pepsoft.worldpainter.plugins.Plugin;
import org.pepsoft.worldpainter.plugins.WPPluginManager;
import org.pepsoft.worldpainter.util.BetterAction;
import org.pepsoft.worldpainter.vo.EventVO;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static org.pepsoft.util.GUIUtils.getUIScale;
import static org.pepsoft.util.swing.MessageUtils.*;
import static org.pepsoft.worldpainter.Constants.ATTRIBUTE_KEY_PLUGINS;
import static org.pepsoft.worldpainter.Constants.ATTRIBUTE_KEY_SAFE_MODE;
import static org.pepsoft.worldpainter.plugins.WPPluginManager.DESCRIPTOR_PATH;

/**
 *
 * @author pepijn
 */
public class Main {
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // Force language to English for now. TODO: remove this once the first translations are implemented
        Locale.setDefault(Locale.US);

        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());

        // Set some hardcoded system properties we always want set:
        if (SystemUtils.isMac()) {
            // Use the Mac style top of screen menu bar
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }
        // Work around a bug in the JIDE Docking Framework which otherwise causes duplicate mouse events on focus
        // switches resulting in uncommanded edits
        System.setProperty("docking.focusWorkaround1", "true");
        // Disable Java2D's automatic UI scaling, as it does not do a good job with the editor view; we want to do it
        // ourselves
        System.setProperty("sun.java2d.uiScale.enabled", "false");
        // Propagate a few system properties to libraries
        final String devMode = System.getProperty("org.pepsoft.worldpainter.devMode");
        if (devMode != null) {
            System.setProperty("org.pepsoft.devMode", devMode);
        }
        boolean safeMode = "true".equalsIgnoreCase(System.getProperty("org.pepsoft.worldpainter.safeMode"));
        for (String arg: args) {
            if (arg.trim().equalsIgnoreCase("--safe")) {
                safeMode = true;
            }
        }
        if (safeMode) {
            logger.info("TalePainter running in safe mode");
            System.setProperty("org.pepsoft.worldpainter.safeMode", "true");
            System.setProperty("org.pepsoft.util.GUIUtils.disableScaling", "true");
        }
        if (Version.isSnapshot()) {
            System.setProperty("org.pepsoft.snapshotVersion", "true");
        }

        // Check if we need to re-launch with a different heap size (configured in Preferences UI)
        if (! "true".equals(System.getProperty(HEAP_CONFIGURED_PROPERTY))) {
            final boolean snapshotForHeap = Version.isSnapshot();
            Preferences heapPrefs = Preferences.userNodeForPackage(Main.class);
            int configuredHeapMB = heapPrefs.getInt((snapshotForHeap ? "snapshot." : "") + "maxHeapSizeMB", 0);
            if (configuredHeapMB > 0) {
                long currentMaxMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
                // Re-launch if current heap differs by more than 10% from configured
                if (Math.abs(currentMaxMB - configuredHeapMB) > configuredHeapMB * 0.1) {
                    try {
                        relaunchWithHeap(configuredHeapMB, args);
                        System.exit(0);
                        return;
                    } catch (Exception e) {
                        System.err.println("Failed to re-launch with configured heap size (" + configuredHeapMB + " MB): " + e.getMessage());
                        // Continue with current heap size
                    }
                }
            }
        }

        // Use a file lock to make sure only one instance is running with autosave enabled
        File configDir = Configuration.getConfigDir();
        if (! configDir.isDirectory()) {
            configDir.mkdirs();
        }
        Path lockFilePath = new File(configDir, "wpsession.lock").toPath();
        try {
            Files.createFile(lockFilePath);
        } catch (FileAlreadyExistsException e) {
            // We can't yet conclude another instance is running, because it may have crashed and left the lock file
            // behind
        }
        FileChannel lockFileChannel = FileChannel.open(lockFilePath, StandardOpenOption.WRITE);
        FileLock lock = lockFileChannel.tryLock();
        boolean autosaveInhibited;
        if (lock == null) {
            lockFileChannel.close();
            autosaveInhibited = true;
        } else {
            Runtime.getRuntime().addShutdownHook(new Thread("Lock File Eraser") {
                @Override
                public void run() {
                    try {
                        lock.release();
                        lockFileChannel.close();
                        Files.delete(lockFilePath);
                    } catch (IOException e) {
                        logger.error("Could not delete lock file " + lockFilePath, e);
                    }
                }
            });
            autosaveInhibited = false;
        }

        // Configure logging
        String logLevel;
        if ("true".equalsIgnoreCase(System.getProperty("org.pepsoft.worldpainter.debugLogging"))) {
            logLevel = "DEBUG";
        } else if ("extra".equalsIgnoreCase(System.getProperty("org.pepsoft.worldpainter.debugLogging"))) {
            logLevel = "TRACE";
        } else {
            logLevel = "INFO";
        }
        LoggerContext logContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(logContext);
            logContext.reset();
            System.setProperty("org.pepsoft.worldpainter.configDir", configDir.getAbsolutePath());
            System.setProperty("org.pepsoft.worldpainter.logLevel", logLevel);
            configurator.doConfigure(ClassLoader.getSystemResourceAsStream("logback-main.xml"));
        } catch (JoranException e) {
            // StatusPrinter will handle this
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings(logContext);
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        logger.info("Starting TalePainter " + Version.VERSION + " (" + Version.BUILD + ")");
        logger.info("Running on {} version {}; architecture: {}", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        logger.info("Running on {} Java version {}; maximum heap size: {} MB", System.getProperty("java.vendor"), System.getProperty("java.specification.version"), Runtime.getRuntime().maxMemory() / 1000000);
        if (autosaveInhibited) {
            logger.warn("Another instance of TalePainter is already running; disabling autosave");
        }

        // Parse the command line
        File myFile = null;
        for (String arg: args) {
            if (new File(arg).isFile() && (myFile == null)) {
                myFile = new File(arg);
            } else {
                throw new IllegalArgumentException("Unrecognised or invalid command line option, or file does not exist: " + arg);
            }
        }
        final File file = myFile;

        // If the config file does not exist, also reset the persistent settings that are not stored in that, since the
        // user may be trying to reset the configuration
        final boolean snapshot = Version.isSnapshot();
        if (! Configuration.getConfigFile().isFile()) {
            try {
                Preferences prefs = Preferences.userNodeForPackage(Main.class);
                prefs.remove((snapshot ? "snapshot." : "") + "accelerationType");
                prefs.flush();
                prefs = Preferences.userNodeForPackage(GUIUtils.class);
                prefs.remove((snapshot ? "snapshot." : "") + "manualUIScale");
                prefs.flush();
            } catch (BackingStoreException e) {
                logger.error("Error resetting user preferences", e);
            }
        }

        // Set the acceleration mode. For some reason we don't fully understand, loading the Configuration from disk
        // initialises Java2D, so we have to do this *before* then.
        AccelerationType accelerationType;
        String accelTypeName = Preferences.userNodeForPackage(Main.class).get((snapshot ? "snapshot." : "") + "accelerationType", null);
        if (accelTypeName != null) {
            accelerationType = AccelerationType.valueOf(accelTypeName);
        } else {
            accelerationType = AccelerationType.DEFAULT;
            // TODO: Experiment with which ones work well and use them by default!
        }
        if (! safeMode) {
            switch (accelerationType) {
                case UNACCELERATED:
                    // Try to disable all accelerated pipelines we know of:
                    System.setProperty("sun.java2d.d3d", "false");
                    System.setProperty("sun.java2d.opengl", "false");
                    System.setProperty("sun.java2d.xrender", "false");
                    System.setProperty("apple.awt.graphics.UseQuartz", "false");
                    logger.info("Hardware acceleration method: unaccelerated");
                    break;
                case DIRECT3D:
                    // Direct3D should already be the default on Windows, but enable a few things which are off by
                    // default:
                    System.setProperty("sun.java2d.translaccel", "true");
                    System.setProperty("sun.java2d.ddscale", "true");
                    logger.info("Hardware acceleration method: Direct3D");
                    break;
                case OPENGL:
                    System.setProperty("sun.java2d.opengl", "True");
                    logger.info("Hardware acceleration method: OpenGL");
                    break;
                case XRENDER:
                    System.setProperty("sun.java2d.xrender", "True");
                    logger.info("Hardware acceleration method: XRender");
                    break;
                case QUARTZ:
                    System.setProperty("apple.awt.graphics.UseQuartz", "true");
                    logger.info("Hardware acceleration method: Quartz");
                    break;
                default:
                    if (SystemUtils.isWindows()) {
                        // TP-47: disable the Direct3D pipeline so screen-capture tools
                        // (Win+Shift+S, Snipping Tool, Win+PrtScn, OBS) can actually
                        // capture the TalePainter window. With D3D enabled (Java's
                        // default on Windows) the window appears black/empty in
                        // screenshots because rendering happens directly to a D3D
                        // surface the OS capture API can't read. Users wanting maximum
                        // 2D performance can still pick Direct3D explicitly via
                        // Preferences → Performance.
                        System.setProperty("sun.java2d.d3d", "false");
                        logger.info("Hardware acceleration method: default (Direct3D disabled on Windows for screenshot support — see TP-47)");
                    } else {
                        logger.info("Hardware acceleration method: default");
                    }
                    break;
            }
        } else {
            logger.info("[SAFE MODE] Hardware acceleration method: default");
        }

        // Load the default platform descriptors so that they don't get blocked by older versions of them which might be
        // contained in the configuration. Do this by loading and initialising (but not instantiating) the DefaultPlugin
        // class
        try {
            Class.forName("org.pepsoft.worldpainter.DefaultPlugin");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // Load or initialise configuration
        Configuration config = null;
        try {
            config = Configuration.load(); // This will migrate the configuration directory if necessary
        } catch (IOException | Error | RuntimeException | ClassNotFoundException e) {
            configError(e);
        }
        if (config == null) {
            if (! logger.isDebugEnabled()) {
                // If debug logging is on, the Configuration constructor will already log this
                logger.info("Creating new configuration");
            }
            config = new Configuration();
        }
        // Load the transient settings into the config object
        config.setSafeMode(safeMode);
        config.setAutosaveInhibited(autosaveInhibited);
        Configuration.setInstance(config);
        logger.info("Installation ID: " + config.getUuid());

        if (config.getPreviousVersion() >= 0) {
            // Perform legacy migration actions
            if (config.getPreviousVersion() < 18) {
                // The dynmap data may have been copied from Minecraft 1.13, in which case it doesn't work, so delete it
                // if it exists
                File dynmapDir = new File(Configuration.getConfigDir(), "dynmap");
                if (dynmapDir.isDirectory()) {
                    FileUtils.deleteDir(dynmapDir);
                }
            }
        }

        if (config.isAutosaveEnabled() && autosaveInhibited) {
            StartupMessages.addWarning("Another instance of TalePainter is already running.\nAutosave will therefore be disabled in this instance of TalePainter!");
        }

        // Store the acceleration type in the config object so the Preferences dialog can edit it
        config.setAccelerationType(accelerationType);

        // Start background scan for Minecraft jars
        BiomeSchemeManager.initialiseInBackground();
        
        // Load and install trusted WorldPainter root certificate
        X509Certificate trustedCert = null;
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            trustedCert = (X509Certificate) certificateFactory.generateCertificate(Main.class.getResourceAsStream("/wproot.pem"));
        } catch (CertificateException e) {
            logger.error("Certificate exception while loading trusted root certificate", e);
        }

        // Load the plugins, checking for updates
        if (! safeMode) {
            if (trustedCert != null) {
                PluginManager.loadPlugins(new File(configDir, "plugins"), trustedCert.getPublicKey(), DESCRIPTOR_PATH, Version.VERSION_OBJ, true);
            } else {
                logger.error("Trusted root certificate not available; not loading plugins");
            }
        } else {
            logger.info("[SAFE MODE] Not loading plugins");
        }
        WPPluginManager.initialise(config.getUuid(), WPContext.INSTANCE);
        // Load all the platform descriptors to ensure that when worlds containing older versions of them are loaded
        // later they are replaced with the current versions, rather than the other way around
        for (Platform platform : PlatformManager.getInstance().getAllPlatforms()) {
            logger.info("Available platform: {}", platform.displayName);
        }
        String httpAgent = "TalePainter " + Version.VERSION + "; " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") + ";";
        System.setProperty("http.agent", httpAgent);

        // Load the private context, if any, which provides services which we only want the official distribution of
        // WorldPainter to perform, such as check for updates and submit usage data
        for (PrivateContext aPrivateContextLoader: ServiceLoader.load(PrivateContext.class)) {
            if (privateContext == null) {
                privateContext = aPrivateContextLoader;
            } else {
                throw new IllegalStateException("More than one private context found on classpath");
            }
        }
        if (privateContext == null) {
            logger.debug("No private context found on classpath; update checks and usage data submission disabled");
            config.setPingAllowed(false);
        }

        // Update checking disabled for TalePainter fork

        final long start = System.currentTimeMillis();
        config.setLaunchCount(config.getLaunchCount() + 1);
        Runtime.getRuntime().addShutdownHook(new Thread("Configuration Saver") {
            @Override
            public void run() {
                try {
                    Configuration config = Configuration.getInstance();
                    MouseOrTabletOperation.flushEvents(config);
                    BetterAction.flushEvents(config);
                    EventVO sessionEvent = new EventVO("worldpainter.session").setAttribute(EventVO.ATTRIBUTE_TIMESTAMP, new Date(start)).duration(System.currentTimeMillis() - start);
                    StringBuilder sb = new StringBuilder();
                    List<Plugin> plugins = WPPluginManager.getInstance().getAllPlugins();
                    plugins.stream()
                            .filter(plugin -> ! plugin.getClass().getName().startsWith("org.pepsoft.worldpainter"))
                            .forEach(plugin -> {
                        if (sb.length() > 0) {
                            sb.append(',');
                        }
                        sb.append("{name=");
                        sb.append(plugin.getName().replaceAll("[ \\t\\n\\x0B\\f\\r\\.]", ""));
                        sb.append(",version=");
                        sb.append(plugin.getVersion());
                        sb.append('}');
                    });
                    if (sb.length() > 0) {
                        sessionEvent.setAttribute(ATTRIBUTE_KEY_PLUGINS, sb.toString());
                    }
                    sessionEvent.setAttribute(ATTRIBUTE_KEY_SAFE_MODE, config.isSafeMode());
                    config.logEvent(sessionEvent);
                    config.save();

                    // Store the acceleration type and manual GUI scale separately, because we need them before we can
                    // load the config:
                    Preferences prefs = Preferences.userNodeForPackage(Main.class);
                    prefs.put((snapshot ? "snapshot." : "") + "accelerationType", config.getAccelerationType().name());
                    prefs.flush();
                    prefs = Preferences.userNodeForPackage(GUIUtils.class);
                    prefs.putFloat((snapshot ? "snapshot." : "") + "manualUIScale", config.getUiScale());
                    prefs.flush();
                } catch (IOException e) {
                    logger.error("I/O error saving configuration", e);
                } catch (BackingStoreException e) {
                    logger.error("Backing store exception saving acceleration type", e);
                }
                logger.info("Shutting down TalePainter");
            }
        });
        
        // Make the "action:" URLs used in various places work:
        URL.setURLStreamHandlerFactory(protocol -> {
            switch (protocol) {
                case "action":
                    return new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL u) throws IOException {
                            throw new UnsupportedOperationException("Not supported");
                        }
                    };
                default:
                    return null;
            }
        });

        final World2 world;
        final File autosaveFile = new File(configDir, "autosave.world");
        final boolean willRecoverAutosave = (! autosaveInhibited) && config.isAutosaveEnabled() && autosaveFile.isFile();

        // TP-48: on a clean startup with no CLI file and no autosave to recover,
        // auto-open the most-recently-worked-on map from the recent-files list
        // instead of creating a fresh blank default world.
        final File recentMap;
        if ((file == null) && (! willRecoverAutosave)) {
            final List<File> recents = config.getRecentFiles();
            final File candidate = ((recents != null) && (! recents.isEmpty())) ? recents.get(0) : null;
            recentMap = ((candidate != null) && candidate.isFile()) ? candidate : null;
        } else {
            recentMap = null;
        }

        if ((file == null) && (! willRecoverAutosave) && (recentMap == null)) {
            if (! safeMode) {
                world = WorldFactory.createDefaultWorld(config, new Random().nextLong());
//                world = WorldFactory.createFancyWorld(config, new Random().nextLong());
            } else {
                logger.info("[SAFE MODE] Using default configuration for default world");
                world = WorldFactory.createDefaultWorld(new Configuration(), new Random().nextLong());
            }
        } else {
            world = null;
        }

        // Install JIDE licence, if present
        InputStream in = ClassLoader.getSystemResourceAsStream("jide_licence.properties");
        if (in != null) {
            try {
                Properties jideLicenceProps = new Properties();
                jideLicenceProps.load(in);
                Lm.verifyLicense(jideLicenceProps.getProperty("companyName"), jideLicenceProps.getProperty("projectName"), jideLicenceProps.getProperty("licenceKey"));
            } finally {
                in.close();
            }
        }

        final Configuration.LookAndFeel lookAndFeel = (config.getLookAndFeel() != null) ? config.getLookAndFeel() : Configuration.LookAndFeel.SYSTEM;
        SwingUtilities.invokeLater(() -> {
            Configuration myConfig = Configuration.getInstance();
            if (myConfig.isSafeMode()) {
                GUIUtils.setUIScale(1.0f);
                logger.info("[SAFE MODE] Not installing visual theme");
            } else {
                // Install configured look and feel
                try {
                    String laf;
                    switch (lookAndFeel) {
                        case SYSTEM:
                            laf = UIManager.getSystemLookAndFeelClassName();
                            break;
                        case METAL:
                            laf = "javax.swing.plaf.metal.MetalLookAndFeel";
                            break;
                        case NIMBUS:
                            laf = "javax.swing.plaf.nimbus.NimbusLookAndFeel";
                            break;
                        case DARK_METAL:
                            laf = "org.netbeans.swing.laf.dark.DarkMetalLookAndFeel";
                            IconUtils.setTheme("dark_metal");
                            break;
                        case DARK_NIMBUS:
                            laf = "org.netbeans.swing.laf.dark.DarkNimbusLookAndFeel";
                            IconUtils.setTheme("dark_nimbus");
                            break;
                        default:
                            throw new InternalError();
                    }
                    logger.debug("Installing look and feel: " + laf);
                    UIManager.setLookAndFeel(laf);
                    LookAndFeelFactory.installJideExtension();
                    if (((lookAndFeel == Configuration.LookAndFeel.DARK_METAL)
                            || (lookAndFeel == Configuration.LookAndFeel.DARK_NIMBUS))) {
                        // Patch some things to make dark themes look better
                        VoidRenderer.setColour(UIManager.getColor("Panel.background").getRGB());
                        if (lookAndFeel == Configuration.LookAndFeel.DARK_METAL) {
                            UIManager.put("ContentContainer.background", UIManager.getColor("desktop"));
                            UIManager.put("JideTabbedPane.foreground", new Color(222, 222, 222));
                        }
                    }
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                    logger.warn("Could not install selected look and feel", e);
                }

                if (getUIScale() != 1.0f) {
                    // Scale the look and feel to the UI
                    GUIUtils.scaleLookAndFeel(getUIScale());
                }
            }

            // Don't paint values above sliders in GTK look and feel
            UIManager.put("Slider.paintValue", Boolean.FALSE);

            final App app = App.getInstance();
            app.setVisible(true);
            // Swing quirk:
            if (myConfig.isMaximised() && (System.getProperty("org.pepsoft.worldpainter.size") == null)) {
                app.setExtendedState(Frame.MAXIMIZED_BOTH);
            }

            // Do this later to give the app the chance to properly set itself up
            SwingUtilities.invokeLater(() -> {
                if (Version.isSnapshot() && ! myConfig.isMessageDisplayed(SNAPSHOT_MESSAGE_KEY)) {
                    String result = JOptionPane.showInputDialog(app, SNAPSHOT_MESSAGE, "Snapshot Release", WARNING_MESSAGE);
                    if (result == null) {
                        // Cancel was pressed
                        System.exit(0);
                    }
                    while (! result.toLowerCase().replace(" ", "").equals("iunderstand")) {
                        DesktopUtils.beep();
                        result = JOptionPane.showInputDialog(app, SNAPSHOT_MESSAGE, "Snapshot Release", WARNING_MESSAGE);
                        if (result == null) {
                            // Cancel was pressed
                            System.exit(0);
                        }
                    }
                    myConfig.setMessageDisplayed(SNAPSHOT_MESSAGE_KEY);
                }

                if (world != null) {
                    // On a Mac we may be doing this unnecessarily because we may be opening a .world file, but it has
                    // proven difficult to detect that. TODO
                    app.setWorld(world, true);
                } else if ((! autosaveInhibited) && myConfig.isAutosaveEnabled() && autosaveFile.isFile()) {
                    logger.info("Recovering autosaved world");
                    app.open(autosaveFile);
                    StartupMessages.addWarning("TalePainter was not shut down correctly.\nYour world has been recovered from the most recent autosave.\nMake sure to Save it if you want to keep it!");
                } else if (file != null) {
                    app.open(file);
                } else if (recentMap != null) {
                    // TP-48: reopen the most-recently-worked-on map on clean startup
                    logger.info("Auto-opening most recently used map: {}", recentMap);
                    app.open(recentMap);
                }
                for (String error: StartupMessages.getErrors()) {
                    beepAndShowError(app, error, "Startup Error");
                }
                for (String warning: StartupMessages.getWarnings()) {
                    beepAndShowWarning(app, warning, "Startup Warning");
                }
                for (String message: StartupMessages.getMessages()) {
                    showInfo(app, message, "Startup Message");
                }
                // Donation and merch dialogs removed for TalePainter fork
            });
        });
    }

    private static void configError(Throwable e) {
        // Try to preserve the config file
        File configFile = Configuration.getConfigFile();
        if (configFile.isFile() && configFile.canRead()) {
            File backupConfigFile = new File(configFile.getParentFile(), configFile.getName() + ".old");
            try {
                FileUtils.copyFileToFile(configFile, backupConfigFile, true);
            } catch (IOException e1) {
                logger.error("I/O error while trying to preserve faulty config file", e1);
            }
        }

        // Report the error
        logger.error("Exception while initialising configuration", e);
        StartupMessages.addError("Could not read configuration file! Configuration was reset.\n\nException type: " + e.getClass().getSimpleName() + "\nMessage: " + e.getMessage());
    }

    @Language("HTML")
    private static final String SNAPSHOT_MESSAGE = "<html><h1>Warning: Snapshot Release</h1>" +
            "<p>This is a snapshot release of TalePainter. It is for testing <em>only</em>!" +
            "<p>Any worlds you edit with this version <strong>may not be loadable</strong> by the next production version<br>when that is released and <strong>will not be loadable</strong> by the current production version!" +
            "<p><strong>Make backups</strong> of any existing worlds you wish to test with this release, in a safe location." +
            "<p>Any or all work you do with this test release may be lost, and if you don't create backups,<br>you may lose your current worlds." +
            "<p>Please report bugs on GitHub: https://github.com/Captain-Chaos/TalePainter" +
            "<p>Type \"I understand\" below to proceed with testing the next release of TalePainter:</p></html>";
    private static final String SNAPSHOT_MESSAGE_KEY = "org.pepsoft.worldpainter.snapshotWarning";

    /**
     * System property used as a guard so a JVM that was re-launched with the configured heap size does not itself try
     * to re-launch again.
     */
    private static final String HEAP_CONFIGURED_PROPERTY = "org.pepsoft.worldpainter.heapConfigured";

    /** System properties propagated to a freshly launched JVM when re-launching with a configured heap size. */
    private static final String[] PROPAGATED_PROPERTIES = {
            "org.pepsoft.worldpainter.devMode",
            "org.pepsoft.worldpainter.safeMode",
            "org.pepsoft.worldpainter.configDir",
            "org.pepsoft.worldpainter.classifier",
            "org.pepsoft.worldpainter.threads"
    };

    private static void relaunchWithHeap(int heapSizeMB, String[] args) throws Exception {
        final RelaunchSpec spec = buildRelaunchSpec(
                heapSizeMB, args,
                System.getProperty("jpackage.app-path"),
                System.getProperty("java.home"),
                System.getProperty("java.class.path"),
                ManagementFactory.getRuntimeMXBean().getInputArguments());

        final ProcessBuilder pb = new ProcessBuilder(spec.command);
        pb.environment().putAll(spec.environment);
        pb.inheritIO();
        pb.start();
    }

    /**
     * Work out how to re-launch TalePainter with a different maximum heap size. Pure (no process is started and no
     * filesystem is touched) so it can be unit tested; {@link #relaunchWithHeap} supplies the live arguments and runs
     * the result.
     *
     * <p>When running from a jpackage app-image ({@code appPath} is the path to the native launcher, taken from the
     * {@code jpackage.app-path} system property) the bundled runtime contains no {@code java}/{@code javaw} executable,
     * so we cannot start a fresh JVM directly. Instead we re-launch the native launcher, which rebuilds its JVM options
     * from {@code TalePainter.cfg} (including the hardcoded {@code -Xmx}), and override the heap through the
     * {@code _JAVA_OPTIONS} environment variable. The JVM applies {@code _JAVA_OPTIONS} last, so it wins over the
     * {@code .cfg}'s {@code -Xmx}.
     *
     * <p>Otherwise (running from a JAR or the IDE) we start a fresh JVM from {@code <java.home>/bin/java} with the
     * configured heap as a {@code -Xmx} argument.
     *
     * @param appPath path to the jpackage native launcher, or {@code null} when not running from a jpackage app-image.
     */
    static RelaunchSpec buildRelaunchSpec(int heapSizeMB, String[] args, String appPath, String javaHome, String classpath, List<String> jvmInputArgs) {
        final List<String> command = new ArrayList<>();
        final Map<String, String> environment = new HashMap<>();
        if (appPath != null) {
            // jpackage app-image: re-launch the native launcher and override the .cfg's -Xmx via _JAVA_OPTIONS.
            command.add(appPath);
            command.addAll(Arrays.asList(args));
            environment.put("_JAVA_OPTIONS", "-Xmx" + heapSizeMB + "m -D" + HEAP_CONFIGURED_PROPERTY + "=true");
        } else {
            final String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            command.add(javaBin);
            command.add("-Xmx" + heapSizeMB + "m");
            command.add("-D" + HEAP_CONFIGURED_PROPERTY + "=true");

            // Propagate existing system properties that matter
            for (String prop : PROPAGATED_PROPERTIES) {
                final String value = System.getProperty(prop);
                if (value != null) {
                    command.add("-D" + prop + "=" + value);
                }
            }

            // Propagate --add-opens JVM arguments
            for (String inputArg : jvmInputArgs) {
                if (inputArg.startsWith("--add-opens")) {
                    command.add(inputArg);
                }
            }

            // Detect if running from a JAR (-jar) or classpath
            if (classpath.endsWith(".jar") && (! classpath.contains(File.pathSeparator))) {
                command.add("-jar");
                command.add(classpath);
            } else {
                command.add("-cp");
                command.add(classpath);
                command.add(Main.class.getName());
            }
            command.addAll(Arrays.asList(args));
        }
        return new RelaunchSpec(command, environment);
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Main.class);

    static PrivateContext privateContext;

    /**
     * The result of {@link #buildRelaunchSpec}: the process {@link #command} to run to re-launch TalePainter with a
     * different maximum heap size, plus any environment variable {@link #environment} overrides to apply on top of the
     * inherited environment.
     */
    static final class RelaunchSpec {
        final List<String> command;
        final Map<String, String> environment;

        RelaunchSpec(List<String> command, Map<String, String> environment) {
            this.command = command;
            this.environment = environment;
        }
    }
}