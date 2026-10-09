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
        assertNull(DirCheck.DEFAULT.verifyStrictOwnerOnly(dir));
    }


    @Test
    void strictRefusesTheShapesAnotherUserCanPlant() throws Exception
    {
        Path open = tmp.resolve("open");
        Files.createDirectory(open,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
        String why = DirCheck.DEFAULT.verifyStrictOwnerOnly(open);
        assertNotNull(why);
        assertTrue(why.contains("not owner-only"), why);

        Path target = tmp.resolve("target");
        Files.createDirectory(target,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path link = tmp.resolve("link");
        Files.createSymbolicLink(link, target);
        String whyLink = DirCheck.DEFAULT.verifyStrictOwnerOnly(link);
        assertNotNull(whyLink);
        assertTrue(whyLink.contains("symbolic link"), whyLink);

        assertTrue(DirCheck.DEFAULT.verifyStrictOwnerOnly(tmp.resolve("missing"))
                .contains("not a directory"));
        Path file = tmp.resolve("file");
        Files.writeString(file, "x");
        assertTrue(DirCheck.DEFAULT.verifyStrictOwnerOnly(file).contains("not a directory"));
    }


    private static String mode(Path aDir) throws Exception
    {
        return PosixFilePermissions
                .toString(Files.getPosixFilePermissions(aDir, LinkOption.NOFOLLOW_LINKS));
    }
}
