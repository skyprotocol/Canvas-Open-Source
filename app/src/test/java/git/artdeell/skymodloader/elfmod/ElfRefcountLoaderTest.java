package git.artdeell.skymodloader.elfmod;

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
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 34)
public class ElfRefcountLoaderTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    private static class RecordingLoader extends ElfRefcountLoader {
        final List<String> loaded = new ArrayList<>();
        RecordingLoader(File game, File mods, String maps) throws IOException {
            super(preparePaths(game.getPath(), mods), mods, new StringReader(maps));
        }
        @Override public void loadNative(String path, String name) { loaded.add(path); }
    }

    // A section-only ELF fixture with real mod metadata, parsed by the production jelf code.
    private File mod(File directory, String name) throws IOException {
        byte[] names = "\0.shstrtab\0.config\0".getBytes(StandardCharsets.UTF_8);
        byte[] metadata = ("{\"name\":\"" + name + "\",\"description\":\"fixture\","
                + "\"majorVersion\":1,\"minorVersion\":0,\"patchVersion\":0,\"dependencies\":[]}")
                .getBytes(StandardCharsets.UTF_8);
        ByteBuffer elf = ByteBuffer.allocate(256 + names.length + metadata.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        elf.put(new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1});
        elf.position(16);
        elf.putShort((short)3).putShort((short)183).putInt(1);
        elf.putLong(0).putLong(0).putLong(64).putInt(0);
        elf.putShort((short)64).putShort((short)0).putShort((short)0);
        elf.putShort((short)64).putShort((short)3).putShort((short)1);
        elf.position(128);
        section(elf, 1, 3, 256, names.length);
        section(elf, 11, 1, 256 + names.length, metadata.length);
        elf.put(names).put(metadata);
        File result = new File(directory, name);
        Files.write(result.toPath(), elf.array());
        return result;
    }

    private void section(ByteBuffer elf, int name, int type, long offset, long size) {
        elf.putInt(name).putInt(type).putLong(0).putLong(0).putLong(offset).putLong(size);
        elf.putInt(0).putInt(0).putLong(1).putLong(0);
    }

    @Test
    public void missingModsDirectoryIsCreatedBeforeCachingAndNewModLoads() throws Exception {
        File game = files.newFolder("game");
        File mods = new File(files.getRoot(), "mods");
        RecordingLoader loader = new RecordingLoader(game, mods, "");
        assertTrue(mods.isDirectory());
        File requested = mod(mods, "new.so");
        loader.load();
        assertEquals(Collections.singletonList(requested.getCanonicalPath()), loader.loaded);
    }

    @Test
    public void explicitModWinsGameNameCollisionAndRegistersEvenWhenMapped() throws Exception {
        File game = files.newFolder("game");
        File mods = files.newFolder("mods");
        mod(game, "same.so");
        File requested = mod(mods, "same.so");
        String maps = "1000-2000 r-xp 0 00:00 1 " + requested.getAbsolutePath() + "\n";
        RecordingLoader loader = new RecordingLoader(game, mods, maps);
        loader.load();
        assertEquals(Collections.singletonList(requested.getCanonicalPath()), loader.loaded);
    }

    @Test
    public void disabledMarkerRemainsAnIntentionalSkip() throws Exception {
        File game = files.newFolder("game");
        File mods = files.newFolder("mods");
        File requested = mod(mods, "disabled.so");
        File marker = new File(requested.getPath() + "_invalid.txt");
        assertTrue(marker.createNewFile());
        RecordingLoader loader = new RecordingLoader(game, mods, "");
        loader.load();
        assertTrue(loader.loaded.isEmpty());
        assertTrue(marker.exists());
    }

    @Test
    public void directoryBecomingUnlistableAfterConstructionFailsLoad() throws Exception {
        File game = files.newFolder("game");
        File mods = files.newFolder("mods");
        RecordingLoader loader = new RecordingLoader(game, mods, "");
        assertTrue(mods.delete());
        assertTrue(mods.createNewFile());
        assertThrows(IOException.class, loader::load);
        assertTrue(loader.loaded.isEmpty());
    }
}
