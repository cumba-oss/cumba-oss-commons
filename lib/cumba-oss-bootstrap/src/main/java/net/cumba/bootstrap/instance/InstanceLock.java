package net.cumba.bootstrap.instance;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The real per-user instance lock: {@link FileChannel#tryLock(long, long, boolean)} on byte 0 of
 * {@code instance.lock}, the channel and the lock held in a static field for the JVM's life. The
 * operating system releases it on any exit, a crash included, so the lock is the truth and the
 * endpoint file beside it only a pointer.
 *
 * <p>
 * An {@link OverlappingFileLockException} (the lock is already held <em>in this JVM</em>, through
 * another channel - a test, say) counts as held, never as an error. That probe's channel is
 * deliberately kept open: on some systems closing <em>any</em> channel on the file releases every
 * lock the JVM holds on it ({@link FileLock}), which would silently drop the lock the other channel
 * holds.
 */
public final class InstanceLock implements LockProbe
{

    private static final System.Logger LOGGER = System.getLogger(InstanceLock.class.getName());

    /** The locks this JVM holds, with their channels: kept for the JVM's life. */
    private static final List<FileLock> HELD = new ArrayList<>();

    /** Channels of overlapping probes, kept open on purpose (see the class comment). */
    private static final List<FileChannel> OVERLAPPING = new ArrayList<>();

    // The channel and the lock are held for the JVM's life by design (the class comment).
    @SuppressWarnings("PMD.CloseResource")
    @Override
    public synchronized Status tryLock(Path aLockFile)
    {
        FileChannel channel = null;
        try
        {
            Path parent = aLockFile.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            channel = FileChannel.open(aLockFile, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
            FileLock lock;
            try
            {
                lock = channel.tryLock(0, 1, false);
            }
            catch (OverlappingFileLockException e)
            {
                OVERLAPPING.add(channel);
                return Status.HELD;
            }
            if (lock == null)
            {
                channel.close();
                return Status.HELD;
            }
            HELD.add(lock);
            return Status.ACQUIRED;
        }
        catch (IOException | RuntimeException e)
        {
            LOGGER.log(Level.INFO, "Single instance: cannot lock " + aLockFile + " (" + e
                    + "); running as a separate instance");
            closeQuietly(channel);
            return Status.UNSUPPORTED;
        }
    }


    /**
     * The locks this JVM holds through this class, for a test that must prove a lock is really
     * there (and still valid after an overlapping probe).
     */
    static synchronized List<FileLock> held()
    {
        return List.copyOf(HELD);
    }


    private static void closeQuietly(@Nullable FileChannel aChannel)
    {
        if (aChannel == null)
        {
            return;
        }
        try
        {
            aChannel.close();
        }
        catch (IOException e)
        {
            LOGGER.log(Level.DEBUG, "Single instance: closing a failed lock channel: " + e);
        }
    }
}
