package net.cumba.datatable.help;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Helpers for working with {@link CompletableFuture} chains whose final stage is deliberately not
 * consumed by the caller.
 */
public class AsyncSupport
{

    private AsyncSupport()
    {
        throw new UnsupportedOperationException("utility class");
    }


    /**
     * Discard a {@link CompletableFuture} that has already had its error handling attached (via
     * {@code exceptionally} / {@code handle} / {@code whenComplete}), or that is a deliberate
     * fire-and-forget background task.
     *
     * <p>
     * Wrapping the chain in this call makes the discard explicit and isolates the
     * {@code @SuppressWarnings("FutureReturnValueIgnored")} to a single, documented location
     * instead of scattering it across every call site.
     * </p>
     *
     * @param aFuture
     *            the future whose return value is intentionally ignored.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public static void fireAndForget(CompletableFuture<?> aFuture)
    {
        // intentionally empty — the caller has decided this future's
        // result and any exceptions are handled within the chain itself
    }


    /**
     * Join the given future and unwrap any {@link CompletionException} so the caller sees the
     * original failure. {@link IOException} causes are rethrown as-is; {@link RuntimeException}
     * causes are rethrown as-is; anything else (including JVM {@link Error}) is wrapped in
     * {@code IOException}.
     *
     * <p>
     * Without unwrapping, {@code CompletableFuture.join()} masks the original cause inside a
     * {@link CompletionException} whose message reads like a generic concurrency error — and
     * callers declared {@code throws IOException} see the wrapper escape as a
     * {@code RuntimeException}, violating the throws contract.
     * </p>
     *
     * @param aFuture
     *            the future to join.
     * @throws IOException
     *             if the future completed with an {@link IOException} or any other non-runtime
     *             cause.
     */
    public static void joinOrThrowIO(CompletableFuture<?> aFuture) throws IOException
    {
        try
        {
            aFuture.join();
        }
        catch (CompletionException ce)
        {
            // Unwrap repeatedly: producers sometimes wrap an IOException in
            // UncheckedIOException to cross a no-throws boundary (e.g. inside a parallel-stream
            // worker), and CompletableFuture itself can nest CompletionException when an
            // exception bubbles through `thenCompose` / `allOf` layers. Walk the cause chain
            // until we find a meaningful endpoint or run out.
            Throwable cause = ce;
            // `ne.getCause() != ne` is a deliberate IDENTITY check, not a value comparison: it is
            // the self-referential-cause guard that stops this loop spinning forever when an
            // exception is its own cause. Objects.equals() here would express the wrong intent
            // (and would start consulting a subclass's equals()).
            while (cause instanceof CompletionException ne && ne.getCause() != null
                    && ne.getCause() != ne)
            {
                cause = ne.getCause();
            }
            if (cause instanceof java.io.UncheckedIOException uioe && uioe.getCause() != null)
            {
                throw uioe.getCause();
            }
            if (cause instanceof IOException ioe)
            {
                throw ioe;
            }
            if (cause instanceof RuntimeException re)
            {
                throw re;
            }
            if (cause instanceof Error err)
            {
                throw err;
            }
            throw new IOException(cause);
        }
    }
}
