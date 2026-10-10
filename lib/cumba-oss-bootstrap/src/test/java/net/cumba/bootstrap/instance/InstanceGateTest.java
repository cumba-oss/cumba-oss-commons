package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import net.cumba.bootstrap.instance.HandOffClientTest.FakeDirCheck;
import net.cumba.bootstrap.instance.HandOffClientTest.ScriptedLock;
import net.cumba.bootstrap.instance.InstanceGate.Deps;
import net.cumba.bootstrap.instance.InstanceGate.Kind;
import net.cumba.bootstrap.instance.InstanceGate.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceGateTest
{

    @TempDir
    Path tmp;

    @AfterEach
    void clearPublished()
    {
        System.clearProperty(InstanceGate.DIR_PROPERTY);
        System.clearProperty(InstanceGate.LOCKED_PROPERTY);
        System.clearProperty(InstanceGate.OUTCOME_PROPERTY);
    }


    private Deps deps(LockProbe aLock, DirCheck aDirCheck)
    {
        return new Deps(new FakeClock(), aLock, aDirCheck, Platform.LINUX, ForegroundGrant.NONE,
                new CloseWatchdog(new ManualScheduler()), () ->
                {
                });
    }


    @Test
    void aFreeLockIsPrimaryAndDeletesTheStaleEndpoint() throws Exception
    {
        Path scope = tmp.resolve("scope");
        Path instanceDir = scope.resolve("instance");
        Files.createDirectories(instanceDir);
        Path endpoint = instanceDir.resolve("endpoint");
        Files.writeString(endpoint, "socket=/old\ntoken=old\nversion=1\n");
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.ACQUIRED);
        FakeDirCheck dirCheck = new FakeDirCheck();
        Outcome o = InstanceGate.enter(scope, new String[0], tmp, deps(lock, dirCheck));
        assertEquals(Kind.PRIMARY, o.kind());
        assertEquals("PRIMARY", o.token());
        assertEquals(instanceDir, o.instanceDir());
        assertEquals(List.of(instanceDir.resolve("instance.lock")), lock.files);
        assertEquals(List.of(instanceDir), dirCheck.ensured);
        assertFalse(Files.exists(endpoint), "H1: the previous run's endpoint is gone");

        o.publish();
        assertEquals(instanceDir.toString(), System.getProperty(InstanceGate.DIR_PROPERTY));
        assertEquals("true", System.getProperty(InstanceGate.LOCKED_PROPERTY));
        assertEquals("PRIMARY", System.getProperty(InstanceGate.OUTCOME_PROPERTY));
    }


    @Test
    void aRefusedInstanceDirectoryIsExtraBeforeAnyLock()
    {
        FakeDirCheck dirCheck = new FakeDirCheck();
        dirCheck.ensureReason = "nope";
        ScriptedLock lock = new ScriptedLock(LockProbe.Status.ACQUIRED);
        Outcome o;
        try (LogCapture log = LogCapture.on(InstanceGate.class))
        {
            o = InstanceGate.enter(tmp, new String[0], tmp, deps(lock, dirCheck));
            log.assertLogged(Level.INFO,
                    "instance directory refused (nope); starting as a separate instance");
        }
        assertEquals(Kind.EXTRA, o.kind());
        assertEquals("dir-check", o.reason());
        assertEquals(List.of(), lock.files, "no lock probe");
        o.publish();
        assertEquals("EXTRA:dir-check", System.getProperty(InstanceGate.OUTCOME_PROPERTY));
        assertNull(System.getProperty(InstanceGate.DIR_PROPERTY),
                "EXTRA publishes the outcome only");
        assertNull(System.getProperty(InstanceGate.LOCKED_PROPERTY));
    }


    @Test
    void noLockSupportIsExtra()
    {
        Outcome o = InstanceGate.enter(tmp, new String[0], tmp,
                deps(new ScriptedLock(LockProbe.Status.UNSUPPORTED), new FakeDirCheck()));
        assertEquals(Kind.EXTRA, o.kind());
        assertEquals("no-lock-support", o.reason());
    }


    @Test
    void offAndHandedOffPublishAsSpecified()
    {
        InstanceGate.publishOff();
        assertEquals("OFF", System.getProperty(InstanceGate.OUTCOME_PROPERTY));
        assertNull(System.getProperty(InstanceGate.DIR_PROPERTY));
        clearPublished();
        new Outcome(Kind.HANDED_OFF, "", tmp).publish();
        assertNull(System.getProperty(InstanceGate.OUTCOME_PROPERTY), "a hand-off exits");
        assertEquals("HANDED_OFF", new Outcome(Kind.HANDED_OFF, "", tmp).token());
        assertEquals("OFF", new Outcome(Kind.OFF, "", tmp).token());
        new Outcome(Kind.OFF, "", tmp).publish();
        assertEquals("OFF", System.getProperty(InstanceGate.OUTCOME_PROPERTY));
    }


    @Test
    void pathsAreTheExistingFilesAndDirectoriesResolvedAgainstTheWorkingDirectory() throws Exception
    {
        Path f = Files.writeString(tmp.resolve("x.csv"), "");
        Path d = Files.createDirectories(tmp.resolve("sub").resolve("dir"));
        List<String> paths = InstanceGate.pathsOf(new String[]
        {
                "x.csv", "--new-instance", "sub/dir", "sub/../x.csv", "nope.csv", "-v", f.toString()
        }, tmp);
        assertEquals(List.of(f.toString(), d.toString(), f.toString(), f.toString()), paths);
        assertEquals(List.of(), InstanceGate.pathsOf(new String[0], tmp));
    }


    @Test
    void tooManyOrTooLongPathsAreRefused() throws Exception
    {
        String[] many = new String[WireCodec.MAX_PATHS + 1];
        java.util.Arrays.fill(many, "f");
        assertEquals("too-many-paths", assertThrows(IllegalArgumentException.class,
                () -> InstanceGate.pathsOf(many, tmp, p -> true)).getMessage());
        String[] justEnough = new String[WireCodec.MAX_PATHS];
        java.util.Arrays.fill(justEnough, "f");
        assertEquals(WireCodec.MAX_PATHS, InstanceGate.pathsOf(justEnough, tmp, p -> true).size());
        String[] tooLong =
        {
                "x".repeat(WireCodec.MAX_PATH_BYTES + 1)
        };
        assertEquals("path-too-long", assertThrows(IllegalArgumentException.class,
                () -> InstanceGate.pathsOf(tooLong, tmp, p -> true)).getMessage());
        // the cap is on the absolute path's UTF-8 bytes, inclusive
        String prefix = tmp.toAbsolutePath().normalize() + tmp.getFileSystem().getSeparator();
        int room = WireCodec.MAX_PATH_BYTES - prefix.getBytes(StandardCharsets.UTF_8).length;
        String[] atTheCap =
        {
                prefix + "x".repeat(room)
        };
        assertEquals(List.of(atTheCap[0]), InstanceGate.pathsOf(atTheCap, tmp, p -> true));
        String[] overTheCap =
        {
                prefix + "x".repeat(room + 1)
        };
        assertEquals("path-too-long", assertThrows(IllegalArgumentException.class,
                () -> InstanceGate.pathsOf(overTheCap, tmp, p -> true)).getMessage());
    }


    @Test
    void theLimitsApplyToAHandOffOnlyNeverToAPrimary() throws Exception
    {
        // review LOW 3: the limits are the protocol's. With the lock free this start is the
        // primary and opens its 257 files itself; checked before the lock, the limit made it an
        // EXTRA without a lock, so the next start became a second primary.
        String[] files = new String[WireCodec.MAX_PATHS + 1];
        for (int i = 0; i < files.length; i++)
        {
            files[i] = Files.writeString(tmp.resolve("f" + i), "").toString();
        }
        ScriptedLock free = new ScriptedLock(LockProbe.Status.ACQUIRED);
        Outcome primary = InstanceGate.enter(tmp, files, tmp, deps(free, new FakeDirCheck()));
        assertEquals(Kind.PRIMARY, primary.kind(), primary.toString());
        assertEquals(1, free.files.size());

        // held: the hand-off cannot carry them - EXTRA, after the lock probe, before any connect
        ScriptedLock held = new ScriptedLock(LockProbe.Status.HELD);
        Outcome extra;
        try (LogCapture log = LogCapture.on(InstanceGate.class))
        {
            extra = InstanceGate.enter(tmp, files, tmp, deps(held, new FakeDirCheck()));
            log.assertLogged(Level.INFO,
                    "Single instance: too-many-paths; starting as a separate instance");
        }
        assertEquals(Kind.EXTRA, extra.kind());
        assertEquals("too-many-paths", extra.reason());
        assertEquals(1, held.files.size(), "the lock was probed first");
    }


    @Test
    void aPathTheFileSystemRejectsIsSkipped()
    {
        assertEquals(List.of(), InstanceGate.pathsOf(new String[]
        {
                "bad\0name"
        }, tmp));
    }


    @Test
    void aFailingEndpointDeleteIsLoggedAndIgnored() throws Exception
    {
        // a non-empty directory in the endpoint's place cannot be deleted by deleteIfExists
        Path endpoint = tmp.resolve("endpoint");
        Files.createDirectories(endpoint.resolve("child"));
        try (LogCapture log = LogCapture.on(InstanceGate.class))
        {
            InstanceGate.deleteStaleEndpoint(endpoint);
            log.assertLogged(Level.INFO, "cannot delete the previous endpoint " + endpoint
                    + " (java.nio.file.DirectoryNotEmptyException");
        }
        assertTrue(Files.isDirectory(endpoint), "still there, the gate went on");
        InstanceGate.deleteStaleEndpoint(tmp.resolve("absent"));
    }


    @Test
    void theSplashCloserNeverTouchesAwtWhenHeadless()
    {
        // the contract: headless, it returns without touching AWT (and a test JVM shows no
        // splash screen in any case, so the other branch finds none)
        System.setProperty("java.awt.headless", "true");
        assertDoesNotThrow(InstanceGate::closeSplashScreen);
        assertTrue(java.awt.GraphicsEnvironment.isHeadless());
    }


    @Test
    void anUnexpectedGateFailureIsLoggedWithItsCauseAndPublishedAsExtra()
    {
        IllegalStateException failure = new IllegalStateException("no unix sockets here");
        try (LogCapture log = LogCapture.on(InstanceGate.class))
        {
            InstanceGate.publishError(failure);
            log.assertLogged(Level.WARNING, "the gate failed; starting as a separate instance");
            log.assertLoggedWithThrowable(Level.WARNING, IllegalStateException.class);
        }
        assertEquals("EXTRA:error", System.getProperty(InstanceGate.OUTCOME_PROPERTY));
        assertNull(System.getProperty(InstanceGate.DIR_PROPERTY));
    }


    @Test
    void theRealSeamsMakeAFreshScopePrimary()
    {
        // the production entry: the real directory check, the real lock, nothing else running
        Path scope = tmp.resolve("real-scope");
        Outcome o = InstanceGate.enter(scope, new String[0], tmp);
        assertEquals(Kind.PRIMARY, o.kind(), o.toString());
        assertEquals(scope.resolve(Endpoint.DIR_NAME), o.instanceDir());
        assertTrue(Files.isRegularFile(o.instanceDir().resolve(Endpoint.LOCK_FILE_NAME)));
        assertEquals(LockProbe.Status.HELD,
                new InstanceLock().tryLock(o.instanceDir().resolve(Endpoint.LOCK_FILE_NAME)),
                "this JVM holds the lock now");
    }


    @Test
    void defaultsAreTheRealSeams()
    {
        Deps d = Deps.defaults();
        assertEquals(Clock.SYSTEM, d.clock());
        assertTrue(d.lockProbe() instanceof InstanceLock);
        assertEquals(DirCheck.DEFAULT, d.dirCheck());
        assertEquals(Platform.current(), d.platform());
    }
}
