package net.cumba.datatable.help;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Creates temp files / directories restricted to the current user on POSIX filesystems
 * (owner-only). On non-POSIX systems the platform default applies.
 */
public final class SecureTempFiles
{

    private static final FileAttribute<?>[] FILE_ATTRS;

    private static final FileAttribute<?>[] DIR_ATTRS;

    static
    {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        {
            FILE_ATTRS = new FileAttribute<?>[]
            {
                    PosixFilePermissions
                            .asFileAttribute(PosixFilePermissions.fromString("rw-------"))
            };
            DIR_ATTRS = new FileAttribute<?>[]
            {
                    PosixFilePermissions
                            .asFileAttribute(PosixFilePermissions.fromString("rwx------"))
            };
        }
        else
        {
            FILE_ATTRS = new FileAttribute<?>[0];
            DIR_ATTRS = new FileAttribute<?>[0];
        }
    }

    private SecureTempFiles()
    {
    }


    public static Path createTempFile(String prefix, String suffix) throws IOException
    {
        return Files.createTempFile(prefix, suffix, FILE_ATTRS);
    }


    public static Path createTempDirectory(String prefix) throws IOException
    {
        return Files.createTempDirectory(prefix, DIR_ATTRS);
    }
}
