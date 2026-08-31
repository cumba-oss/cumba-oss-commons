package net.cumba.bootstrap.itmain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Test-only target program loaded by the launcher under test. It deliberately does <em>not</em>
 * call {@link System#exit}, so invoking it cannot tear down the test JVM. It records what it
 * observed (args, a system property the launcher applied, the seeded {@code bootstrap.dir}, and the
 * context classloader type) to the file named by the {@code launcher.test.out} system property, so
 * the test can assert on it after the launcher loaded this class in a separate classloader.
 */
public final class TestMain
{

    private TestMain()
    {
    }


    /**
     * Records observations, or throws on demand to exercise exception propagation.
     *
     * @param args
     *            when the first element is {@code "throw"}, throws instead of recording
     */
    public static void main(String[] args)
    {
        if (args.length > 0 && "throw".equals(args[0]))
        {
            throw new IllegalStateException("boom from target main");
        }
        if (args.length > 0 && "error".equals(args[0]))
        {
            throw new AssertionError("error from target main");
        }
        Path out = Path.of(System.getProperty("launcher.test.out"));
        List<String> lines = List.of("args=" + String.join("|", args),
                "foo=" + System.getProperty("foo", ""),
                "bootstrapDir=" + System.getProperty("bootstrap.dir", ""), "tccl=" + Thread
                        .currentThread().getContextClassLoader().getClass().getSimpleName());
        try
        {
            Files.write(out, lines);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
