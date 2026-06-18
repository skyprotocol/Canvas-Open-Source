package git.artdeell.skymodloader.elfmod;

import java.util.Objects;
import java.io.File;

public class ElfModMetadata {
    public File modFile;
    public boolean modIsValid = false;
    public String name;
    public String displayName;

    public String author;
    public int majorVersion;
    public int minorVersion;
    public int patchVersion;

    public String description;
    public ElfModMetadata[] dependencies;
    public boolean displaysUI;
    public boolean selfManagedUI;
    public String githubReleasesUrl;
    public String offsetsUrl;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElfModMetadata metadata = (ElfModMetadata) o;
        return modIsValid == metadata.modIsValid && name.equals(metadata.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modIsValid, name);
    }

    public String getGithubReleasesUrl() {
        return this.githubReleasesUrl;
    }

    public String getOffsetsUrl() {
        return this.offsetsUrl;
    }

    public boolean hasOffsetsUrl() {
        return offsetsUrl != null && !offsetsUrl.isEmpty();
    }

    public String getName() {
        return this.name;
    }

    public String getLibName() {
        return name;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int getPatchVersion() {
        return patchVersion;
    }
}
