package net.cumba.bootstrap.instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The owner-only directory checks of the hand-off, behind a seam so the refusals (a foreign owner
 * above all, which no test can create) run on a Linux build host.
 *
 * <p>
 * Two strengths. {@link #ensureOwnerOnly} is for {@code instance/} inside the user's own settings
 * directory: created with {@code rwx------} at creation, and on every later start the mode is
 * re-applied (it is our directory in our directory; a drifted mode is repaired, not refused).
 * {@link #verifyStrictOwnerOnly} is for a socket directory under a shared location
 * ({@code $XDG_RUNTIME_DIR}, {@code java.io.tmpdir}): it must exist, be a directory and not a
 * symbolic link (read with {@link LinkOption#NOFOLLOW_LINKS}), be owned by the current user and
 * carry exactly {@code rwx------}, else it is refused - a shared {@code /tmp} is where another user
 * can pre-create or swap the directory. The application half creates that directory with the mode
 * set at creation (no window for another user); this half only verifies it before connecting.
 *
 * <p>
 * Where the file system has no POSIX view (Windows, and a non-POSIX file system elsewhere) the mode
 * cannot be read or set: the settings directory then inherits the profile's ACL, which keeps other
 * users out, and the strict checks fall back to existence, directory and no-symlink.
 */
public interface DirCheck
{

    /** The real checks. */
    DirCheck DEFAULT = new Default();

    /** The one mode the directories may have. */
    Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    /**
     * Creates {@code aDir} owner-only, or re-applies the mode to an existing one.
     *
     * @param aDir
     *            the directory
     * @return {@code null} when it is in order, else the reason it is not
     */
    @Nullable
    String ensureOwnerOnly(Path aDir);


    /**
     * Verifies an existing {@code aDir} strictly (see the class comment); creates nothing.
     *
     * @param aDir
     *            the directory
     * @return {@code null} when it is in order, else the reason it is not
     */
    @Nullable
    String verifyStrictOwnerOnly(Path aDir);

    /** The real implementation over NIO. */
    final class Default implements DirCheck
    {

        private Default()
        {
        }


        @Override
        public @Nullable String ensureOwnerOnly(Path aDir)
        {
            try
            {
                if (!Files.isDirectory(aDir, LinkOption.NOFOLLOW_LINKS))
                {
                    if (Files.exists(aDir, LinkOption.NOFOLLOW_LINKS))
                    {
                        return aDir + " exists and is not a directory";
                    }
                    Files.createDirectories(aDir, attributesFor(aDir));
                }
                PosixFileAttributeView view = Files.getFileAttributeView(aDir,
                        PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (view != null && !OWNER_ONLY.equals(view.readAttributes().permissions()))
                {
                    view.setPermissions(OWNER_ONLY);
                }
                return null;
            }
            catch (IOException | UnsupportedOperationException | SecurityException e)
            {
                return aDir + ": " + e;
            }
        }


        @Override
        public @Nullable String verifyStrictOwnerOnly(Path aDir)
        {
            try
            {
                if (Files.isSymbolicLink(aDir))
                {
                    return aDir + " is a symbolic link";
                }
                if (!Files.isDirectory(aDir, LinkOption.NOFOLLOW_LINKS))
                {
                    return aDir + " is not a directory";
                }
                PosixFileAttributeView view = Files.getFileAttributeView(aDir,
                        PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (view == null)
                {
                    return null;
                }
                PosixFileAttributes attributes = view.readAttributes();
                if (!OWNER_ONLY.equals(attributes.permissions()))
                {
                    return aDir + " is not owner-only ("
                            + PosixFilePermissions.toString(attributes.permissions()) + ")";
                }
                UserPrincipal me = aDir.getFileSystem().getUserPrincipalLookupService()
                        .lookupPrincipalByName(System.getProperty("user.name", ""));
                if (!me.equals(attributes.owner()))
                {
                    return aDir + " is owned by " + attributes.owner().getName();
                }
                return null;
            }
            catch (IOException | UnsupportedOperationException | SecurityException e)
            {
                return aDir + ": " + e;
            }
        }


        /** {@code rwx------} where the file system has a POSIX view, nothing elsewhere. */
        private static FileAttribute<?>[] attributesFor(Path aDir)
        {
            if (aDir.getFileSystem().supportedFileAttributeViews().contains("posix"))
            {
                return new FileAttribute<?>[]
                {
                        PosixFilePermissions.asFileAttribute(OWNER_ONLY)
                };
            }
            return new FileAttribute<?>[0];
        }
    }
}
