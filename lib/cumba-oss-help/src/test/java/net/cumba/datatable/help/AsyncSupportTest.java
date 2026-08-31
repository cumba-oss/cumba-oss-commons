package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class AsyncSupportTest
{

    @Test
    void joinOrThrowIO_alreadyCompleted_isNoOp()
    {
        CompletableFuture<Void> f = CompletableFuture.completedFuture(null);
        assertDoesNotThrow(() -> AsyncSupport.joinOrThrowIO(f));
    }


    @Test
    void joinOrThrowIO_ioExceptionCause_rethrowsAsIs()
    {
        IOException original = new IOException("disk fail");
        CompletableFuture<Void> f = CompletableFuture.failedFuture(original);
        IOException thrown = assertThrows(IOException.class, () -> AsyncSupport.joinOrThrowIO(f));
        assertSame(original, thrown);
    }


    @Test
    void joinOrThrowIO_runtimeExceptionCause_rethrowsAsIs()
    {
        IllegalStateException original = new IllegalStateException("state bad");
        CompletableFuture<Void> f = CompletableFuture.failedFuture(original);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> AsyncSupport.joinOrThrowIO(f));
        assertSame(original, thrown);
    }


    @Test
    void joinOrThrowIO_errorCause_rethrowsAsIs()
    {
        OutOfMemoryError original = new OutOfMemoryError("synthetic");
        CompletableFuture<Void> f = CompletableFuture.failedFuture(original);
        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class,
                () -> AsyncSupport.joinOrThrowIO(f));
        assertSame(original, thrown);
    }


    /**
     * Regression test for F-RS1: when a producer wraps an IOException in UncheckedIOException (e.g.
     * inside a parallel-stream worker that can't throw checked), the helper must surface the
     * original IOException so callers declared {@code throws IOException} see it — not the
     * UncheckedIOException wrapper.
     */
    @Test
    void joinOrThrowIO_uncheckedIOExceptionCause_unwrapsToIO()
    {
        IOException original = new IOException("nested via unchecked");
        UncheckedIOException uioe = new UncheckedIOException(original);
        CompletableFuture<Void> f = CompletableFuture.failedFuture(uioe);
        IOException thrown = assertThrows(IOException.class, () -> AsyncSupport.joinOrThrowIO(f));
        assertSame(original, thrown);
    }


    @Test
    void joinOrThrowIO_nestedCompletionExceptions_unwrapsToInnermost()
    {
        IOException original = new IOException("buried deep");
        // Simulate the shape CompletableFuture can produce when an exception bubbles through
        // multiple combinator layers (thenCompose / allOf): CE → CE → IOException.
        CompletionException inner = new CompletionException(original);
        CompletableFuture<Void> f = CompletableFuture.failedFuture(inner);
        IOException thrown = assertThrows(IOException.class, () -> AsyncSupport.joinOrThrowIO(f));
        assertSame(original, thrown);
    }


    @Test
    void fireAndForget_doesNotThrow()
    {
        CompletableFuture<Void> f = CompletableFuture.completedFuture(null);
        assertDoesNotThrow(() -> AsyncSupport.fireAndForget(f));
    }


    @Test
    void utilityClassCannotBeInstantiated() throws Exception
    {
        java.lang.reflect.Constructor<AsyncSupport> ctor = AsyncSupport.class
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        java.lang.reflect.InvocationTargetException ex = assertThrows(
                java.lang.reflect.InvocationTargetException.class, ctor::newInstance);
        assertEquals(UnsupportedOperationException.class, ex.getCause().getClass());
    }
}
