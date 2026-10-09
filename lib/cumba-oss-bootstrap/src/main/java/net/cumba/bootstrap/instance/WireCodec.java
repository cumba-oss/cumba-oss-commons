package net.cumba.bootstrap.instance;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire protocol v1 of the single-instance hand-off, client half. Each frame is a 4-byte big-endian
 * length followed by UTF-8 text; lines are split on {@code \n}. The application's server half
 * spells the same constants (the halves share a protocol, not types) and the distribution's build
 * pins the two spellings equal.
 *
 * <table>
 * <caption>frames</caption>
 * <tr>
 * <th>from</th>
 * <th>frame</th>
 * </tr>
 * <tr>
 * <td>client</td>
 * <td>{@code CUMBA-INSTANCE 1\n<token>} - hello: version + token</td>
 * </tr>
 * <tr>
 * <td>server</td>
 * <td>{@code CUMBA-INSTANCE 1\n<pid>} - hello back, or {@code DENIED\n<reason>} then close
 * ({@code token}, {@code version}, {@code limit})</td>
 * </tr>
 * <tr>
 * <td>client</td>
 * <td>{@code OPEN\n<n>\n<path 1>\n...<path n>}</td>
 * </tr>
 * <tr>
 * <td>server</td>
 * <td>{@code BUSY\n<state>} (0..n times, {@code STARTING} or {@code QUEUED}) then {@code ACCEPTED}
 * or {@code CLOSING}</td>
 * </tr>
 * </table>
 *
 * <p>
 * The length prefix is checked against {@link #MAX_FRAME_BYTES} <em>before</em> a buffer is
 * allocated; a frame over the cap closes the connection.
 */
public final class WireCodec
{

    /** The hello word, followed by the protocol version. */
    public static final String PROTOCOL = "CUMBA-INSTANCE";

    /** The protocol version this half speaks. */
    public static final int VERSION = 1;

    /** Client → server: the paths to open. */
    public static final String OPEN = "OPEN";

    /** Server → client: the files reached the application. */
    public static final String ACCEPTED = "ACCEPTED";

    /** Server → client: the primary is exiting; wait for the lock. */
    public static final String CLOSING = "CLOSING";

    /** Server → client: alive, still working on it; second line = the state. */
    public static final String BUSY = "BUSY";

    /** Server → client: refused; second line = the reason. */
    public static final String DENIED = "DENIED";

    /** {@link #BUSY} state: the primary is still building its window. */
    public static final String STATE_STARTING = "STARTING";

    /** {@link #BUSY} state: queued behind a modal dialog. */
    public static final String STATE_QUEUED = "QUEUED";

    /** {@link #DENIED} reason: wrong token (a stale endpoint). */
    public static final String DENIED_TOKEN = "token";

    /** {@link #DENIED} reason: a protocol version the server does not implement. */
    public static final String DENIED_VERSION = "version";

    /** {@link #DENIED} reason: too many concurrent connections. */
    public static final String DENIED_LIMIT = "limit";

    /** Most paths in one {@link #OPEN}. */
    public static final int MAX_PATHS = 256;

    /** Most bytes (UTF-8) of one path. */
    public static final int MAX_PATH_BYTES = 32 * 1024;

    /** Longest frame: 256 paths of 32 KiB plus the header lines. */
    public static final int MAX_FRAME_BYTES = 8 * 1024 * 1024 + 64 * 1024;

    private WireCodec()
    {
    }


    /**
     * The client's hello.
     *
     * @param aToken
     *            the token from the endpoint file
     * @return the frame text
     */
    public static String hello(String aToken)
    {
        return PROTOCOL + " " + VERSION + "\n" + aToken;
    }


    /**
     * The client's {@link #OPEN} frame.
     *
     * @param aPaths
     *            the absolute paths, at most {@link #MAX_PATHS}
     * @return the frame text
     */
    public static String open(List<String> aPaths)
    {
        StringBuilder sb = new StringBuilder(OPEN).append('\n').append(aPaths.size());
        for (String path : aPaths)
        {
            sb.append('\n').append(path);
        }
        return sb.toString();
    }


    /**
     * A frame's lines.
     *
     * @param aFrame
     *            the frame text
     * @return the lines, split on {@code \n}; a trailing {@code \r} is stripped from each
     */
    public static List<String> lines(String aFrame)
    {
        List<String> lines = new ArrayList<>();
        for (String line : aFrame.split("\n", -1))
        {
            lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        return lines;
    }


    /**
     * Writes one frame.
     *
     * @param aChannel
     *            the connection
     * @param aText
     *            the frame text
     * @throws IOException
     *             on a write failure, or a text over {@link #MAX_FRAME_BYTES}
     */
    public static void write(ByteChannel aChannel, String aText) throws IOException
    {
        byte[] bytes = aText.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME_BYTES)
        {
            throw new IOException("frame of " + bytes.length + " bytes exceeds the cap");
        }
        ByteBuffer buffer = ByteBuffer.allocate(4 + bytes.length);
        buffer.putInt(bytes.length).put(bytes).flip();
        while (buffer.hasRemaining())
        {
            aChannel.write(buffer);
        }
    }


    /**
     * Reads one frame.
     *
     * @param aChannel
     *            the connection
     * @return the frame text
     * @throws EOFException
     *             when the peer closed before a whole frame arrived
     * @throws IOException
     *             on a read failure, or a length prefix over {@link #MAX_FRAME_BYTES} (checked
     *             before any buffer is allocated)
     */
    public static String read(ByteChannel aChannel) throws IOException
    {
        ByteBuffer header = ByteBuffer.allocate(4);
        readFully(aChannel, header);
        int length = header.flip().getInt();
        if (length < 0 || length > MAX_FRAME_BYTES)
        {
            throw new IOException("frame length " + length + " exceeds the cap");
        }
        ByteBuffer body = ByteBuffer.allocate(length);
        readFully(aChannel, body);
        return new String(body.array(), 0, length, StandardCharsets.UTF_8);
    }


    private static void readFully(ByteChannel aChannel, ByteBuffer aBuffer) throws IOException
    {
        while (aBuffer.hasRemaining())
        {
            if (aChannel.read(aBuffer) < 0)
            {
                throw new EOFException("connection closed");
            }
        }
    }
}
