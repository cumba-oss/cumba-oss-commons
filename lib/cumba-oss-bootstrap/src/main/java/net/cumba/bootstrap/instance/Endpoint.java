package net.cumba.bootstrap.instance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The endpoint file {@code <scope>/instance/endpoint}, written by the primary's application half
 * and read by every client: {@code socket=<path>}, {@code token=<64 hex>}, {@code version=1}; or
 * {@code listener=none} + {@code version=1} from a primary that cannot listen. Only a pointer: the
 * lock is the truth, and a stale file is answered by a refused socket or a {@code DENIED token}.
 *
 * @param socket
 *            the socket path; empty for {@code listener=none}
 * @param token
 *            the token; empty for {@code listener=none}
 * @param version
 *            the protocol version the primary speaks
 * @param noListener
 *            {@code listener=none}: the primary runs without a listener
 */
public record Endpoint(Optional<Path> socket, Optional<String> token, int version,
        boolean noListener)
{

    /** The file name, under the instance directory. */
    public static final String FILE_NAME = "endpoint";

    /** The lock file name, under the instance directory. */
    public static final String LOCK_FILE_NAME = "instance.lock";

    /** The instance directory, under the scope (the user settings directory). */
    public static final String DIR_NAME = "instance";

    /** The socket file name prefix; one fresh name {@code s-<16 hex>} per primary start. */
    public static final String SOCKET_PREFIX = "s-";

    /** Endpoint key: the socket path. */
    public static final String KEY_SOCKET = "socket";

    /** Endpoint key: the token. */
    public static final String KEY_TOKEN = "token";

    /** Endpoint key: the protocol version. */
    public static final String KEY_VERSION = "version";

    /** Endpoint key: {@code listener=none}. */
    public static final String KEY_LISTENER = "listener";

    /** The {@value #KEY_LISTENER} value of a primary without a listener. */
    public static final String LISTENER_NONE = "none";

    /**
     * Reads the file. Missing, unreadable or malformed all read as "no endpoint", which a client
     * treats as "the primary is starting" and retries inside its window.
     *
     * @param aFile
     *            the endpoint file
     * @return the endpoint, or empty
     */
    public static Optional<Endpoint> read(Path aFile)
    {
        List<String> lines;
        try
        {
            lines = Files.readAllLines(aFile, StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException e)
        {
            return Optional.empty();
        }
        return parse(lines);
    }


    /**
     * Parses the file's lines.
     *
     * @param aLines
     *            the lines
     * @return the endpoint, or empty when malformed
     */
    public static Optional<Endpoint> parse(List<String> aLines)
    {
        String socket = null;
        String token = null;
        String version = null;
        String listener = null;
        for (String raw : aLines)
        {
            String line = raw.strip();
            int eq = line.indexOf('=');
            if (line.isEmpty() || line.charAt(0) == '#' || eq < 0)
            {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            switch (key)
            {
            case KEY_SOCKET -> socket = value;
            case KEY_TOKEN -> token = value;
            case KEY_VERSION -> version = value;
            case KEY_LISTENER -> listener = value;
            default ->
            {
                // v1 ignores unknown keys (a later version may add some)
            }
            }
        }
        Integer v = parseVersion(version);
        if (v == null)
        {
            return Optional.empty();
        }
        if (LISTENER_NONE.equals(listener))
        {
            return Optional.of(new Endpoint(Optional.empty(), Optional.empty(), v, true));
        }
        if (socket == null || socket.isEmpty() || token == null || token.isEmpty())
        {
            return Optional.empty();
        }
        try
        {
            return Optional
                    .of(new Endpoint(Optional.of(Path.of(socket)), Optional.of(token), v, false));
        }
        catch (InvalidPathException e)
        {
            return Optional.empty();
        }
    }


    private static @Nullable Integer parseVersion(@Nullable String aVersion)
    {
        if (aVersion == null)
        {
            return null;
        }
        try
        {
            return Integer.valueOf(aVersion);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
