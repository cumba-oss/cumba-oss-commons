package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import net.cumba.bootstrap.itmain.LockHolderMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real lock: contention is measured against a <b>forked</b> holder process, never against a
 * second channel in this JVM (which the JDK reports as an
 * {@link java.nio.channels.OverlappingFileLockException}, a different case, tested separately).
 */
class InstanceLockTest
{

    @TempDir
    Path tmp;

    @Test
    void aLockHeldByAnotherProcessIsHeldAndFreeAfterItExits() throws Exception
    {
        Path lockFile = tmp.resolve("scope").resolve("instance").resolve("instance.lock");
        Process holder = fork(lockFile);
        try (java.io.OutputStream stdin = holder.getOutputStream();
                BufferedReader out = new BufferedReader(
                        new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8)))
        {
            assertEquals("LOCK ACQUIRED", out.readLine(), "the holder took the lock");
            long fdsBefore = openFileDescriptors();
            assertEquals(LockProbe.Status.HELD, new InstanceLock().tryLock(lockFile));
            assertEquals(LockProbe.Status.HELD, new InstanceLock().tryLock(lockFile),
                    "still held: a probe does not disturb the holder");
            if (fdsBefore >= 0)
            {
                // the lock wait probes every 100 ms for 30 s: a probe that kept its channel
                // would leak 300 open files per wait
                assertEquals(fdsBefore, openFileDescriptors(), "a held probe leaves no file open");
            }
            stdin.close();
            assertTrue(holder.waitFor(30, TimeUnit.SECONDS), "the holder exits on EOF");
        }
        assertEquals(LockProbe.Status.ACQUIRED, new InstanceLock().tryLock(lockFile),
                "released by the OS when the holder ended");
        List<FileLock> held = InstanceLock.held();
        assertTrue(held.stream().anyMatch(FileLock::isValid), "held for the JVM's life");
    }


    @Test
    void aLockThisJvmHoldsThroughAnotherChannelCountsAsHeld() throws Exception
    {
        Path lockFile = tmp.resolve("own.lock");
        try (FileChannel other = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
                FileLock lock = other.lock(0, 1, false))
        {
            assertEquals(LockProbe.Status.HELD, new InstanceLock().tryLock(lockFile),
                    "OverlappingFileLockException is 'held', not an error");
            assertTrue(lock.isValid(), "the probe's channel stays open: the other lock survives");
        }
    }


    @Test
    void aFileSystemThatCannotLockIsUnsupported() throws Exception
    {
        Path file = Files.writeString(tmp.resolve("file"), "x");
        // a lock file whose parent is a regular file cannot be created
        try (LogCapture log = LogCapture.on(InstanceLock.class))
        {
            assertEquals(LockProbe.Status.UNSUPPORTED,
                    new InstanceLock().tryLock(file.resolve("instance.lock")));
            log.assertLogged(Level.INFO, "cannot lock " + file.resolve("instance.lock") + " (");
            log.assertLogged(Level.INFO, "); running as a separate instance");
        }
    }


    @Test
    void theMissingDirectoriesOfTheLockFileAreCreated()
    {
        Path lockFile = tmp.resolve("new").resolve("deeper").resolve("instance.lock");
        assertEquals(LockProbe.Status.ACQUIRED, new InstanceLock().tryLock(lockFile));
        assertTrue(Files.isRegularFile(lockFile), "created with its parents");
    }


    /** This process's open file descriptors where {@code /proc} shows them (Linux), else -1. */
    private static long openFileDescriptors() throws IOException
    {
        Path fds = Path.of("/proc/self/fd");
        if (!Files.isDirectory(fds))
        {
            return -1;
        }
        try (Stream<Path> s = Files.list(fds))
        {
            return s.count();
        }
    }


    private static Process fork(Path aLockFile) throws Exception
    {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        // a child inherits no surefire system property: the redirected home is passed on
        ProcessBuilder pb = new ProcessBuilder(java,
                "-Duser.home=" + System.getProperty("user.home"), "-cp", classpath,
                LockHolderMain.class.getName(), aLockFile.toString());
        pb.redirectErrorStream(false);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        return pb.start();
    }
}
