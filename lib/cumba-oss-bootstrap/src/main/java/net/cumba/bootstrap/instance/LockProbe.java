package net.cumba.bootstrap.instance;

import java.nio.file.Path;

/**
 * The one-method seam over the per-user instance lock: the hand-off logic is tested in one JVM
 * against a fake, the real lock ({@link InstanceLock}) against a lock held by a separate process.
 */
@FunctionalInterface
public interface LockProbe
{

    /** What a probe found. */
    enum Status
    {

        /** The lock is now held by this JVM, for its life. */
        ACQUIRED,

        /** Another process (or this JVM, through another channel) holds it. */
        HELD,

        /** The file system cannot lock (no lock support, not writable, ...). */
        UNSUPPORTED
    }

    /**
     * Tries the lock once, without waiting.
     *
     * @param aLockFile
     *            the lock file
     * @return the result
     */
    Status tryLock(Path aLockFile);
}
