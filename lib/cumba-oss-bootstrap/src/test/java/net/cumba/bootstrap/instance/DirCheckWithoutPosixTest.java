package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real checks where the file system has no POSIX view - Windows, and a non-POSIX file system
 * elsewhere (here: a zip file system, which runs on every build host): no mode can be set or read,
 * so the directory is created plainly and the strict check falls back to existence, directory and
 * no-symlink.
 */
class DirCheckWithoutPosixTest
{

    @TempDir
    Path tmp;

    private FileSystem zip;

    @BeforeEach
    void openZip() throws Exception
    {
        URI uri = URI.create("jar:" + tmp.resolve("fs.zip").toUri());
        zip = FileSystems.newFileSystem(uri, Map.of("create", "true"));
        assertFalse(zip.supportedFileAttributeViews().contains("posix"),
                "the stand-in must have no POSIX view");
    }


    @AfterEach
    void closeZip() throws Exception
    {
        zip.close();
    }


    @Test
    void ensureCreatesTheDirectoryWithoutAMode()
    {
        Path dir = zip.getPath("/settings", "instance");
        assertNull(DirCheck.DEFAULT.ensureOwnerOnly(dir));
        assertTrue(Files.isDirectory(dir));
        assertNull(DirCheck.DEFAULT.ensureOwnerOnly(dir), "a second start finds it in order");
    }


    @Test
    void strictAcceptsAnExistingDirectoryWhenNoModeCanBeRead() throws Exception
    {
        Path run = Files.createDirectories(zip.getPath("/run"));
        assertNull(DirCheck.DEFAULT.verifyStrictOwnerOnly(run, run));
        String why = DirCheck.DEFAULT.verifyStrictOwnerOnly(zip.getPath("/missing"), run);
        assertTrue(why != null && why.contains("not a directory"), why);
    }
}
