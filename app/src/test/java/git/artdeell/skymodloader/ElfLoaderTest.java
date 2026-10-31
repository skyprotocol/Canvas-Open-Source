package git.artdeell.skymodloader;

import static org.junit.Assert.*;

import android.app.Application;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 34)
public class ElfLoaderTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    private static class RecordingLoader extends ElfLoader {
        final List<String> loaded = new ArrayList<>();
        List<String> needed = Collections.emptyList();

        RecordingLoader(String paths, Reader maps) throws IOException { super(paths, maps); }
        @Override protected List<String> readDependencies(File library) { return needed; }
        @Override public void loadNative(String path, String name) { loaded.add(path); }
    }

    private static class TrackedReader extends StringReader {
        boolean closed;
        TrackedReader() { super(""); }
        @Override public void close() { closed = true; super.close(); }
    }

    private String mapping(File file) {
        return "1000-2000 r-xp 0000 00:00 1 " + file.getAbsolutePath() + "\n";
    }

    @Test
    public void requiredDirectoryFailureClosesMapsAndThrows() throws Exception {
        TrackedReader reader = new TrackedReader();
        File notDirectory = files.newFile("not-a-directory");
        assertThrows(IOException.class, () -> new RecordingLoader(notDirectory.getPath(), reader));
        assertTrue(reader.closed);
    }

    @Test
    public void emptyDirectoryIsValidButMissingRequiredLibraryFails() throws Exception {
        TrackedReader reader = new TrackedReader();
        RecordingLoader loader = new RecordingLoader(files.getRoot().getPath(), reader);
        assertTrue(reader.closed);
        assertThrows(IOException.class, () -> loader.loadLib("libBootloader.so"));
        assertTrue(loader.loaded.isEmpty());
    }

    @Test
    public void mappedBasenameCollisionDoesNotSkipRequestedLibrary() throws Exception {
        File system = files.newFolder("system");
        File mods = files.newFolder("mods");
        File mapped = new File(system, "same.so");
        File requested = new File(mods, "same.so");
        assertTrue(mapped.createNewFile());
        assertTrue(requested.createNewFile());
        RecordingLoader loader = new RecordingLoader(mods.getPath(), new StringReader(mapping(mapped)));
        loader.loadLib("same.so");
        loader.loadLib(new File(mods, "." + File.separator + "same.so"));
        assertEquals(Collections.singletonList(requested.getCanonicalPath()), loader.loaded);
    }

    @Test
    public void missingPlatformDependencyIsLeftToTheLinker() throws Exception {
        File mod = files.newFile("mod.so");
        RecordingLoader loader = new RecordingLoader(files.getRoot().getPath(), new StringReader(""));
        loader.needed = Collections.singletonList("liblog.so");
        loader.loadLib(mod);
        assertEquals(Collections.singletonList(mod.getCanonicalPath()), loader.loaded);
    }

    @Test
    public void cachedLibraryRemovedBeforeLoadFails() throws Exception {
        File mod = files.newFile("mod.so");
        RecordingLoader loader = new RecordingLoader(files.getRoot().getPath(), new StringReader(""));
        assertTrue(mod.delete());
        assertThrows(IOException.class, () -> loader.loadLib("mod.so"));
        assertTrue(loader.loaded.isEmpty());
    }

    @Test
    public void nullModEnumerationIsAnError() {
        File unreadable = new File("fixture") {
            @Override public File[] listFiles(java.io.FileFilter filter) { return null; }
        };
        assertThrows(IOException.class,
                () -> ElfLoader.listRequiredFiles(unreadable, "MOD_ENUMERATION_FAILED", f -> true));
    }
}
