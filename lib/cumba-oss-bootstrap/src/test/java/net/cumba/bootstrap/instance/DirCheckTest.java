package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The real checks on a POSIX file system (the build hosts); a foreign owner is a fake's job. */
class DirCheckTest
{

    @TempDir
    Path tmp;

    @BeforeEach
    void posixOnly()
    {
        assumeTrue(tmp.getFileSystem().supportedFileAttributeViews().contains("posix"),
                "the mode checks need a POSIX file system");
    }


    @Test
    void ensureCreatesOwnerOnlyAndRepairsADriftedMode() throws Exception
    {
        Path dir = tmp.resolve("instance");
        assertNull(DirCheck.DEFAULT.ensureOwnerOnly(dir));
        assertEquals("rwx------", mode(dir));
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertNull(DirCheck.DEFAULT.ensureOwnerOnly(dir), "a second start");
        assertEquals("rwx------", mode(dir), "re-verified: the mode is applied again");
        // the parents are created too
        Path deep = tmp.resolve("a").resolve("b").resolve("instance");
        assertNull(DirCheck.DEFAULT.ensureOwnerOnly(deep));
        assertEquals("rwx------", mode(deep));
    }


    @Test
    void ensureRefusesAFileInPlaceOfTheDirectory() throws Exception
    {
        Path file = tmp.resolve("instance");
        Files.writeString(file, "x");
        String why = DirCheck.DEFAULT.ensureOwnerOnly(file);
        assertNotNull(why);
        assertTrue(why.contains("not a directory"), why);
        // and a parent that cannot be created
        String why2 = DirCheck.DEFAULT.ensureOwnerOnly(file.resolve("below"));
        assertNotNull(why2);
    }


    @Test
    void strictAcceptsAnOwnerOnlyDirectoryOfTheUser() throws Exception
    {
        Path dir = tmp.resolve("run");
        Files.createDirectory(dir,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        assertNull(DirCheck.DEFAULT.verifyStrictOwnerOnly(dir, tmp));
    }


    @Test
    void strictTakesTheOwnerFromTheFileSystemNeverFromUserName() throws Exception
    {
        // review LOW 6: user.name is a -D away from any value and a directory-service name need
        // not round-trip through the lookup service; the current user is the owner of a
        // directory this process made
        Path dir = tmp.resolve("run");
        Files.createDirectory(dir,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        String userName = System.getProperty("user.name");
        System.setProperty("user.name", "no-such-user-" + System.nanoTime());
        try
        {
            assertNull(DirCheck.DEFAULT.verifyStrictOwnerOnly(dir, tmp),
                    "our own directory, whatever user.name says");
        }
        finally
        {
            System.setProperty("user.name", userName);
        }
        // the reference must be readable: without it nothing is accepted
        String why = DirCheck.DEFAULT.verifyStrictOwnerOnly(dir, tmp.resolve("no-such-dir"));
        assertNotNull(why);
        assertTrue(why.contains("NoSuchFileException"), why);
    }


    @Test
    void strictRefusesADirectoryOfAnotherOwnerThanTheReference() throws Exception
    {
        // the refusal a build host cannot plant (it would need another user's directory), seen
        // from the other side: our directory against a reference owned by root
        Path root = Path.of("/");
        assumeTrue(!Files.getOwner(root).equals(Files.getOwner(tmp)),
                "the build runs as root: no foreign owner to compare with");
        Path dir = tmp.resolve("run");
        Files.createDirectory(dir,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        String why = DirCheck.DEFAULT.verifyStrictOwnerOnly(dir, root);
        assertNotNull(why);
        assertTrue(why.contains("is owned by " + Files.getOwner(dir).getName() + ", not by "
                + Files.getOwner(root).getName()), why);
    }


    @Test
    void strictRefusesTheShapesAnotherUserCanPlant() throws Exception
    {
        Path open = tmp.resolve("open");
        Files.createDirectory(open,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
        String why = DirCheck.DEFAULT.verifyStrictOwnerOnly(open, tmp);
        assertNotNull(why);
        assertTrue(why.contains("not owner-only"), why);

        Path target = tmp.resolve("target");
        Files.createDirectory(target,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path link = tmp.resolve("link");
        Files.createSymbolicLink(link, target);
        String whyLink = DirCheck.DEFAULT.verifyStrictOwnerOnly(link, tmp);
        assertNotNull(whyLink);
        assertTrue(whyLink.contains("symbolic link"), whyLink);

        assertTrue(DirCheck.DEFAULT.verifyStrictOwnerOnly(tmp.resolve("missing"), tmp)
                .contains("not a directory"));
        Path file = tmp.resolve("file");
        Files.writeString(file, "x");
        assertTrue(DirCheck.DEFAULT.verifyStrictOwnerOnly(file, tmp).contains("not a directory"));
    }


    private static String mode(Path aDir) throws Exception
    {
        return PosixFilePermissions
                .toString(Files.getPosixFilePermissions(aDir, LinkOption.NOFOLLOW_LINKS));
    }
}
