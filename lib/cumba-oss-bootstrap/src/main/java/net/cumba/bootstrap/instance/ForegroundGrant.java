package net.cumba.bootstrap.instance;

/**
 * Lets the primary's window come to the front. On Windows the process the user just started may
 * pass its foreground right to another process ({@code AllowSetForegroundWindow}); without it the
 * primary's {@code toFront()} only flashes the taskbar button. Elsewhere a no-op.
 *
 * <p>
 * The Windows binding ({@link WindowsForegroundGrant}) is created lazily, on the first call: the
 * selection by platform runs on every build host, the native lookup only where it exists.
 */
@FunctionalInterface
public interface ForegroundGrant
{

    /**
     * What the native call answered.
     *
     * @param bool
     *            the call's {@code BOOL}: non-zero is success
     * @param lastError
     *            {@code GetLastError} right after the call
     */
    record Result(int bool, int lastError)
    {
    }

    /** No grant: every platform but Windows. */
    ForegroundGrant NONE = aPid -> new Result(1, 0);

    /**
     * Allows the process to take the foreground.
     *
     * @param aPid
     *            the process id
     * @return the result
     * @throws NativeCallException
     *             when the native call cannot be made or fails (no such library or symbol, a failed
     *             downcall)
     */
    Result allow(long aPid) throws NativeCallException;

    /** The native call could not be made or failed; the cause says why. */
    final class NativeCallException extends Exception
    {

        private static final long serialVersionUID = 1L;

        /**
         * @param aCause
         *            what the lookup or the downcall threw
         */
        public NativeCallException(Throwable aCause)
        {
            super(String.valueOf(aCause), aCause);
        }
    }

    /**
     * The grant for a platform.
     *
     * @param aPlatform
     *            the platform
     * @return the grant
     */
    static ForegroundGrant forPlatform(Platform aPlatform)
    {
        if (aPlatform == Platform.WINDOWS)
        {
            return aPid ->
            {
                WindowsForegroundGrant grant;
                try
                {
                    grant = new WindowsForegroundGrant();
                }
                catch (RuntimeException | Error e)
                {
                    // no such library or symbol, or a linkage error: the caller logs and goes on
                    throw new NativeCallException(e);
                }
                return grant.allow(aPid);
            };
        }
        return NONE;
    }
}
