package git.artdeell.skymodloader.elfmod;

import android.util.ArrayMap;

import net.fornwall.jelf.ElfFile;
import net.fornwall.jelf.ElfSectionHeader;
import net.fornwall.jelf.ElfStringTable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import git.artdeell.skymodloader.ElfLoader;
import git.artdeell.skymodloader.LibrarySelectorListener;

public class ElfRefcountLoader extends ElfLoader{
    private final List<ElfFileReference> elfFileReferences = new ArrayList<>();
    private final Map<String, ElfModMetadata> metadataByName = new ArrayMap<>();
    private final Map<String, File> filesByName = new ArrayMap<>();
    private final File modsFolder;
    public ElfRefcountLoader(String defaultPaths, File modsFolder) throws IOException {
        this(preparePaths(defaultPaths, modsFolder), modsFolder, new FileReader("/proc/self/maps"));
    }

    ElfRefcountLoader(String preparedPaths, File modsFolder, Reader processMaps) throws IOException {
        super(preparedPaths, processMaps);
        this.modsFolder = modsFolder.getCanonicalFile();
    }

    static String preparePaths(String defaultPaths, File modsFolder) throws IOException {
        if (!modsFolder.isDirectory() && !modsFolder.mkdirs()) {
            throw new IOException("Cannot create mod directory: " + modsFolder);
        }
        return defaultPaths + File.pathSeparator + modsFolder.getAbsolutePath();
    }

    public void load() throws Exception {
        File[] modFiles = listRequiredFiles(modsFolder, "MOD_ENUMERATION_FAILED",
                new SharedObjectFileFilter());
        for(File f : modFiles) {
            try {
                if (f.getName().endsWith(".so")&&(!new File(f.getPath() + "_invalid.txt").exists())) {
                    addElf(f);
                }
            } catch (InvalidModException  e) {
                throw new InvalidModException("Failed to load mod" + f.getName());
            }
        }
        scanDeps();
        Collections.sort(elfFileReferences);
        for(ElfFileReference ref : elfFileReferences) {
            loadLib(filesByName.get(ref.modMeta.name));
        }
    }

    @Override
    public File getLibrary(String name) {
        File mod = filesByName.get(name);
        return mod != null ? mod : super.getLibrary(name);
    }

    @Override
    protected boolean isModLibrary(File library) throws IOException {
        return modsFolder.equals(library.getCanonicalFile().getParentFile());
    }

    @Override
    protected boolean isMappedLibrary(File library) throws IOException {
        return !isModLibrary(library) && super.isMappedLibrary(library);
    }

    @Override
    public void loadNative(String absolutePath, String name) {
        if (modsFolder.equals(new File(absolutePath).getParentFile())) {
            ElfModMetadata metadata = metadataByName.get(name);
            if (metadata == null) {
                throw new IllegalStateException("WTF? No saved metadata for mod library " + name);
            }

            LibrarySelectorListener.onModLibrary(
                    absolutePath,
                    metadata.displaysUI,
                    Optional.ofNullable(metadata.displayName).orElse(metadata.name),
                    Optional.ofNullable(metadata.author).orElse(""),
                    Optional.ofNullable(metadata.description).orElse(""),
                    metadata.majorVersion + "." + metadata.minorVersion + "." + metadata.patchVersion,
                    metadata.selfManagedUI
            );
        } else {
            super.loadNative(absolutePath, name);
        }
    }

    public void addElf(File file) throws Exception {
        ElfModMetadata metadata = loadMetadata(file);
        elfFileReferences.add(new ElfFileReference(metadata));
        metadataByName.put(metadata.name, metadata);
        filesByName.put(metadata.name, file);
    }

    public static ElfModMetadata loadMetadata(File file) throws Exception {
        ElfFile elf = ElfFile.from(file);
        RandomAccessFile raf = new RandomAccessFile(file,"r");
        ElfStringTable shstrtab = elf.getSectionNameStringTable();
        long secoff_config=-1; long secsz_config = -1;
        for(int i = 0; i < elf.e_shnum; i++) {
            ElfSectionHeader shdr = elf.getSection(i).header;
            String shname = shstrtab.get(shdr.sh_name);
            if(".config".equals(shname)) {
                secoff_config = shdr.sh_offset;
                secsz_config = shdr.sh_size;
            }
        }
        if(secoff_config == -1 || secsz_config == -1) {
            raf.close();
            throw new InvalidModException("no SEC_CONFIG in "+file.getName());
        }
        if(secsz_config < 0 || secsz_config > Integer.MAX_VALUE) {
            raf.close();
            throw new InvalidModException("SECSZ exceeds integer limit");
        }
        byte[] config = new byte[(int) secsz_config];
        raf.seek(secoff_config);
        raf.readFully(config);
        JSONObject jsonConfig = new JSONObject(new String(config));
        ElfModMetadata modMetadata = new ElfModMetadata();
        modMetadata.name = jsonConfig.getString("name");
        modMetadata.author = jsonConfig.optString("author");
        modMetadata.description = jsonConfig.getString("description");
        modMetadata.majorVersion = jsonConfig.getInt("majorVersion");
        modMetadata.minorVersion = jsonConfig.getInt("minorVersion");
        modMetadata.patchVersion = jsonConfig.getInt("patchVersion");
        modMetadata.displayName = jsonConfig.optString("displayName");
        modMetadata.displaysUI = jsonConfig.optBoolean("displaysUI");
        modMetadata.selfManagedUI = jsonConfig.optBoolean("selfManagedUI");
        JSONArray jdeps = jsonConfig.getJSONArray("dependencies");
        ElfModMetadata[] dependencies = new ElfModMetadata[jdeps.length()];
        for(int i = 0; i < dependencies.length; i++) {
            JSONObject jsonDependency = jdeps.getJSONObject(i);
            ElfModMetadata dependency = new ElfModMetadata();
            dependency.modIsValid = true;
            dependency.name = jsonDependency.getString("name");
            dependency.author = jsonDependency.optString("author");
            dependency.majorVersion = jsonDependency.getInt("majorVersion");
            dependency.minorVersion = jsonDependency.getInt("minorVersion");
            dependency.patchVersion = jsonDependency.getInt("patchVersion");
            dependencies[i] = dependency;
        }
        modMetadata.dependencies = dependencies;
        modMetadata.modIsValid = true;
        raf.close();
        return modMetadata;
    }

    public void scanDeps() {
        ElfFileReference dummyReference = new ElfFileReference(null);
        for(ElfFileReference reference : elfFileReferences) {
            for(ElfModMetadata deps : reference.modMeta.dependencies) {
                dummyReference.modMeta = deps;
                int index = elfFileReferences.indexOf(dummyReference);
                if(index == -1) throw new IllegalStateException("Can't find dependency "+deps.name);
                else {
                    ElfFileReference target = elfFileReferences.get(index);
                    ElfModMetadata metadata = target.modMeta;
                    if(metadata.majorVersion != deps.majorVersion) throw new IllegalStateException(metadata.name+ ": Amajor "+metadata.majorVersion+" != Dmajor "+deps.majorVersion);
                    if(metadata.minorVersion < deps.minorVersion) throw new IllegalStateException(metadata.name+": Aminor "+metadata.minorVersion+" < Dminor "+deps.minorVersion);
                    target.referenceCount++;
                }
            }
        }
    }
}
