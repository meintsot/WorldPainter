package org.pepsoft.worldpainter.plugins;

import com.google.common.collect.ImmutableMap;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Created by Pepijn on 9-3-2017.
 */
public class CustomObjectManager extends AbstractProviderManager<String, CustomObjectProvider> {
    public CustomObjectManager() {
        super(CustomObjectProvider.class);
        Map<String, CustomObjectProvider> tmpMap = new HashMap<>();
        getImplementations().forEach(provider -> {
            for (String extension: provider.getSupportedExtensions()) {
                tmpMap.put(extension.trim().toLowerCase(), provider);
            }
        });
        providersByExtension = ImmutableMap.copyOf(tmpMap);
        extensionsByPriority = providersByExtension.keySet().stream()
                .sorted((extension1, extension2) -> Integer.compare(extension2.length(), extension1.length()))
                .collect(toList());
    }

    public List<String> getAllSupportedExtensions() {
        return getImplementations().stream()
            .flatMap(provider -> provider.getSupportedExtensions().stream())
            .collect(toList());
    }

    public WPObject loadObject(File file) throws IOException {
        CustomObjectProvider provider = findProvider(file.getName());
        if (provider == null) {
            throw new IllegalArgumentException("No provider found for file " + file.getName());
        }
        return provider.loadObject(file);
    }

    /**
     * Get a universal file filter (implementing {@link FileFilter},
     * {@link java.io.FileFilter} and {@link FilenameFilter}) which will select
     * all supported custom object extensions.
     *
     * @return A universal file filter which will elect all supported custom
     * object extensions.
     */
    public UniversalFileFilter getFileFilter() {
        List<String> extensions = getAllSupportedExtensions();
        String description = "Custom Object Files(" + extensions.stream().map(extension -> "*." + extension).collect(joining(", ")) + ")";
        return new UniversalFileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                } else {
                    return isSupportedFileName(f.getName());
                }
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public boolean accept(File dir, String name) {
                return isSupportedFileName(name);
            }
        };
    }

    public static CustomObjectManager getInstance() {
        return INSTANCE;
    }

    private CustomObjectProvider findProvider(String fileName) {
        final String lowerCaseFileName = fileName.toLowerCase(Locale.ROOT);
        for (String extension: extensionsByPriority) {
            if (matchesExtension(lowerCaseFileName, extension)) {
                return providersByExtension.get(extension);
            }
        }
        return null;
    }

    private boolean isSupportedFileName(String fileName) {
        final String lowerCaseFileName = fileName.toLowerCase(Locale.ROOT);
        for (String extension: extensionsByPriority) {
            if (matchesExtension(lowerCaseFileName, extension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesExtension(String fileName, String extension) {
        return fileName.equals(extension) || fileName.endsWith("." + extension);
    }

    private final Map<String, CustomObjectProvider> providersByExtension;
    private final List<String> extensionsByPriority;

    private static final CustomObjectManager INSTANCE = new CustomObjectManager();

    public abstract class UniversalFileFilter extends FileFilter implements java.io.FileFilter, FilenameFilter {}
}
