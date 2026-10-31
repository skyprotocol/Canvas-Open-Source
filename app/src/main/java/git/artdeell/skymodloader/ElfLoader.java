package git.artdeell.skymodloader;

import android.util.Log;

import androidx.collection.ArrayMap;

import net.fornwall.jelf.ElfDynamicSection;
import net.fornwall.jelf.ElfFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ElfLoader {
    private final Set<String> loadedPaths = new HashSet<>();
    private final Set<String> mappedPaths = new HashSet<>();
    private final Set<String> mappedNames = new HashSet<>();
    private final List<ArrayMap<String, File>> dirCache = new ArrayList<>();

    public ElfLoader(String libraryPaths) throws IOException {
        this(libraryPaths, new FileReader("/proc/self/maps"));
    }

    protected ElfLoader(String libraryPaths, Reader processMaps) throws IOException {
        try (BufferedReader maps = new BufferedReader(processMaps)) {
            for (String path : libraryPaths.split(File.pathSeparator)) {
                ArrayMap<String, File> cache = new ArrayMap<>();
                for (File file : listRequiredFiles(new File(path), "SEARCH_PATH_UNAVAILABLE",
                        File::isFile)) {
                    cache.put(file.getName(), file);
                }
                dirCache.add(cache);
            }
            String line;
            while ((line = maps.readLine()) != null) {
                String[] fields = line.trim().split("\\s+", 6);
                if (fields.length == 6 && fields[5].endsWith(".so")) {
                    File library = new File(fields[5]);
                    mappedPaths.add(library.getCanonicalPath());
                    mappedNames.add(library.getName());
                }
            }
        }
    }

    protected static File[] listRequiredFiles(File directory, String stage, FileFilter filter)
            throws IOException {
        File[] files = directory.listFiles(filter);
        if (files == null) {
            Log.e("ElfLoader", stage + ": " + directory.getAbsolutePath());
            throw new IOException("Cannot list directory: " + directory.getAbsolutePath());
        }
        return files;
    }

    /** Explicit requests must resolve; only transitive linker dependencies may be deferred. */
    public void loadLib(String name) throws IOException {
        File library = getLibrary(name);
        if (library == null) {
            Log.e("ElfLoader", "LIB_NOT_IN_SEARCH_PATHS: " + name);
            throw new IOException("Library not found: " + name);
        }
        loadLib(library);
    }

    public void loadLib(File library) throws IOException {
        File resolved = library.getCanonicalFile();
        if (!resolved.isFile()) throw new IOException("Library not found: " + resolved);
        String path = resolved.getPath();
        if (loadedPaths.contains(path) || isMappedLibrary(resolved)) return;
        for (String needed : readDependencies(resolved)) {
            File dependency = getLibrary(needed);
            // Keep linker SONAME handling for preloaded game/platform dependencies.
            // Mapped mods still need Canvas registration.
            if (mappedNames.contains(needed) && (dependency == null || !isModLibrary(dependency))) {
                continue;
            }
            if (dependency == null) {
                Log.d("ElfLoader", "DEPENDENCY_DEFERRED_TO_LINKER: " + needed + " from " + path);
            } else {
                loadLib(dependency);
            }
        }
        Log.i("ElfLoader", "Loading library " + path);
        loadNative(path, resolved.getName());
        loadedPaths.add(path);
    }

    protected List<String> readDependencies(File library) throws IOException {
        ElfDynamicSection section = ElfFile.from(library).firstSectionByType(ElfDynamicSection.class);
        return section == null ? java.util.Collections.emptyList() : section.getNeededLibraries();
    }

    protected boolean isModLibrary(File library) throws IOException {
        return false;
    }

    protected boolean isMappedLibrary(File library) throws IOException {
        return mappedPaths.contains(library.getCanonicalPath());
    }

    public void loadNative(String absolutePath, String name) {
        System.load(absolutePath);
    }

    public File getLibrary(String libName) {
        for(ArrayMap<String, File> fileCache : dirCache) {
            if(fileCache.containsKey(libName))
                return fileCache.get(libName);
        }
        return null;
    }
}
