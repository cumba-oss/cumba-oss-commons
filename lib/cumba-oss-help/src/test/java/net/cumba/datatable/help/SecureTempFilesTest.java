package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link SecureTempFiles} exists for exactly one reason — the temp file or directory it creates
 * must be readable by its owner only — and until now nothing asserted that. A regression that
 * dropped the {@code FileAttribute} arrays would have created world-readable temp files holding
 * subject data with the whole suite still green.
 */
class SecureTempFilesTest
{

    private static boolean posix()
    {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }


    @Test
    void createTempFileCreatesAnEmptyRegularFile() throws IOException
    {
        Path file = SecureTempFiles.createTempFile("cumba-help-test-", ".tmp");
        try
        {
            assertNotNull(file);
            assertTrue(Files.isRegularFile(file), "must be a regular file: " + file);
            assertEquals(0L, Files.size(file), "a fresh temp file starts empty");
            assertTrue(file.getFileName().toString().startsWith("cumba-help-test-"));
            assertTrue(file.getFileName().toString().endsWith(".tmp"));
        }
        finally
        {
            Files.deleteIfExists(file);
        }
    }


    @Test
    void createTempDirectoryCreatesADirectory() throws IOException
    {
        Path dir = SecureTempFiles.createTempDirectory("cumba-help-test-dir-");
        try
        {
            assertNotNull(dir);
            assertTrue(Files.isDirectory(dir), "must be a directory: " + dir);
            assertTrue(dir.getFileName().toString().startsWith("cumba-help-test-dir-"));
        }
        finally
        {
            Files.deleteIfExists(dir);
        }
    }


    @Test
    void createTempFileIsOwnerReadWriteOnly() throws IOException
    {
        assumeTrue(posix(), "POSIX permissions are only asserted on a POSIX filesystem");
        Path file = SecureTempFiles.createTempFile("cumba-help-perm-", ".tmp");
        try
        {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            assertEquals(PosixFilePermissions.fromString("rw-------"), perms,
                    "temp files must not be readable by group or other; got "
                            + PosixFilePermissions.toString(perms));
        }
        finally
        {
            Files.deleteIfExists(file);
        }
    }


    @Test
    void createTempDirectoryIsOwnerAccessibleOnly() throws IOException
    {
        assumeTrue(posix(), "POSIX permissions are only asserted on a POSIX filesystem");
        Path dir = SecureTempFiles.createTempDirectory("cumba-help-perm-dir-");
        try
        {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(dir);
            assertEquals(PosixFilePermissions.fromString("rwx------"), perms,
                    "temp directories must not be traversable by group or other; got "
                            + PosixFilePermissions.toString(perms));
        }
        finally
        {
            Files.deleteIfExists(dir);
        }
    }


    @Test
    void repeatedCallsYieldDistinctPaths() throws IOException
    {
        Path a = SecureTempFiles.createTempFile("cumba-help-uniq-", ".tmp");
        Path b = SecureTempFiles.createTempFile("cumba-help-uniq-", ".tmp");
        try
        {
            assertTrue(!a.equals(b), "each call must create its own file");
        }
        finally
        {
            Files.deleteIfExists(a);
            Files.deleteIfExists(b);
        }
    }
}
