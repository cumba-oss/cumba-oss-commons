package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFilesTest
{

    @TempDir
    Path tmp;

    @Test
    void readsUtf8Lines() throws IOException
    {
        Path file = Files.writeString(tmp.resolve("a.conf"), "[properties]\nk = ä\n");
        assertEquals(List.of("[properties]", "k = ä"), ConfigFiles.read(file, "user config", "h"));
    }


    @Test
    void aMissingFileNamesWhatItIsAndTheHint()
    {
        Path file = tmp.resolve("missing.conf");
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ConfigFiles.read(file, "user config", "set by -Dx"));
        assertEquals("user config not found: " + file + " (set by -Dx)", ex.getMessage());
    }


    @Test
    void aNonUtf8FileIsReportedAsSuch() throws IOException
    {
        Path file = Files.write(tmp.resolve("ansi.conf"), new byte[]
        {
                'k', '=', (byte) 0xE4
        });
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ConfigFiles.read(file, "user config", "h"));
        assertEquals("user config " + file + " is not UTF-8 text (save it as UTF-8; a byte-order"
                + " mark is fine)", ex.getMessage());
    }


    @Test
    void anUnreadableFileNamesItself() throws IOException
    {
        Path dir = Files.createDirectories(tmp.resolve("a-directory.conf"));
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ConfigFiles.read(dir, "user config", "h"));
        assertTrue(ex.getMessage().startsWith("cannot read user config " + dir + ": "),
                ex::getMessage);
    }
}
