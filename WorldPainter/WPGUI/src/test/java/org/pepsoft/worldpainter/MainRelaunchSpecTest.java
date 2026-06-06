package org.pepsoft.worldpainter;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Regression tests for TP-123: the "Maximum heap size" preference was ignored in
 * the installed (jpackage) application, which always launched with the hardcoded
 * {@code -Xmx4G} from {@code TalePainter.cfg}.
 *
 * <p>Root cause: {@link Main#relaunchWithHeap} re-launched {@code <java.home>/bin/java},
 * but a jpackage app-image strips the {@code java}/{@code javaw} executables from its
 * bundled runtime (only the native {@code TalePainter.exe} launcher is present). The
 * resulting {@code IOException} was swallowed, so the app fell back to the 4&nbsp;GB heap
 * baked into the {@code .cfg}.
 *
 * <p>{@link Main#buildRelaunchSpec} is the pure, process-free seam that decides how to
 * re-launch. In a jpackage image it relaunches the native launcher and overrides the heap
 * through the {@code _JAVA_OPTIONS} environment variable (which the JVM applies last, so it
 * wins over the {@code .cfg}'s {@code -Xmx}). Elsewhere it keeps the original
 * {@code java -Xmx ... -jar/-cp} behaviour.
 */
public class MainRelaunchSpecTest {

    private static final String HEAP_CONFIGURED_PROPERTY = "org.pepsoft.worldpainter.heapConfigured";

    @Test
    public void jpackageImageRelaunchesNativeLauncherAndOverridesHeapViaEnvironment() {
        final String appPath = "C:\\Program Files\\TalePainter\\TalePainter.exe";
        final String[] args = { "C:\\maps\\big.world" };

        final Main.RelaunchSpec spec = Main.buildRelaunchSpec(
                8192, args, appPath,
                "C:\\Program Files\\TalePainter\\runtime", // java.home — no java(.exe) inside
                "C:\\Program Files\\TalePainter\\app\\WPGUI-full.jar",
                Collections.emptyList());

        // Re-launch the native launcher itself, forwarding the file-to-open argument.
        assertEquals(appPath, spec.command.get(0));
        assertEquals(Arrays.asList(appPath, args[0]), spec.command);

        // The launcher rebuilds JVM options from the .cfg, so the heap must NOT be a process
        // argument; it is injected via _JAVA_OPTIONS, which the JVM applies last and therefore
        // overrides the .cfg's -Xmx4G.
        assertFalse("heap must not be passed as a command-line argument",
                spec.command.stream().anyMatch(arg -> arg.startsWith("-Xmx")));
        final String javaOptions = spec.environment.get("_JAVA_OPTIONS");
        assertNotNull("_JAVA_OPTIONS must be set for a jpackage relaunch", javaOptions);
        assertTrue("configured heap must be in _JAVA_OPTIONS", javaOptions.contains("-Xmx8192m"));
        assertTrue("relaunch guard must be in _JAVA_OPTIONS",
                javaOptions.contains("-D" + HEAP_CONFIGURED_PROPERTY + "=true"));
    }

    @Test
    public void fatJarRelaunchUsesJavaBinaryWithJarFlag() {
        final Main.RelaunchSpec spec = Main.buildRelaunchSpec(
                6144, new String[0], null,
                "/opt/jdk17",
                "/opt/app/WPGUI-full.jar",
                Collections.emptyList());

        final String expectedJavaBin = "/opt/jdk17" + File.separator + "bin" + File.separator + "java";
        assertEquals(expectedJavaBin, spec.command.get(0));
        assertTrue("heap is a JVM argument when launching java directly",
                spec.command.contains("-Xmx6144m"));
        assertTrue(spec.command.contains("-D" + HEAP_CONFIGURED_PROPERTY + "=true"));

        final int jarIdx = spec.command.indexOf("-jar");
        assertTrue("-jar must be present for a single-jar classpath", jarIdx >= 0);
        assertEquals("/opt/app/WPGUI-full.jar", spec.command.get(jarIdx + 1));

        assertTrue("no environment override needed when re-launching java directly",
                spec.environment.isEmpty());
    }

    @Test
    public void multiEntryClasspathRelaunchUsesCpAndMainClass() {
        final String classpath = "/opt/app/a.jar" + File.pathSeparator + "/opt/app/b.jar";
        final Main.RelaunchSpec spec = Main.buildRelaunchSpec(
                4096, new String[0], null, "/opt/jdk17", classpath, Collections.emptyList());

        final int cpIdx = spec.command.indexOf("-cp");
        assertTrue("-cp must be present for a multi-entry classpath", cpIdx >= 0);
        assertEquals(classpath, spec.command.get(cpIdx + 1));
        assertEquals(Main.class.getName(), spec.command.get(cpIdx + 2));
        assertFalse("-jar must not be used in classpath mode", spec.command.contains("-jar"));
    }

    @Test
    public void javaBinaryRelaunchPropagatesAddOpensButNotUnrelatedJvmArgs() {
        final List<String> jvmInputArgs = Arrays.asList("--add-opens=java.base/java.lang=ALL-UNNAMED", "-Xss2m");
        final Main.RelaunchSpec spec = Main.buildRelaunchSpec(
                4096, new String[0], null, "/opt/jdk17", "/opt/app/x.jar", jvmInputArgs);

        assertTrue("--add-opens must be forwarded",
                spec.command.contains("--add-opens=java.base/java.lang=ALL-UNNAMED"));
        assertFalse("unrelated JVM args must not be forwarded", spec.command.contains("-Xss2m"));
    }
}
