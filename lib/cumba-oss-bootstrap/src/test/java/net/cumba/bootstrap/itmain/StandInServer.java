package net.cumba.bootstrap.itmain;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import net.cumba.bootstrap.instance.Endpoint;
import net.cumba.bootstrap.instance.WireCodec;

/**
 * A test-written v1 server: what the data browser's listener does, reduced to the protocol. Binds a
 * Unix domain socket in a directory, writes the endpoint file as the application does (atomic
 * replace, {@code socket= token= version=}), accepts on a daemon thread and answers each connection
 * by a scripted {@link Script}. The hand-off client is pinned against it from one JVM
 * ({@code HandOffClientTest}) and from five forked ones ({@code MultiProcessHandOffTest}).
 */
public final class StandInServer implements AutoCloseable
{

    /** What one connection is answered with. */
    public record Script(String helloReply, List<String> afterOpen, End end)
    {

        /** How the connection ends after the replies. */
        public enum End
        {
            /** Send {@code ACCEPTED}. */
            ACCEPTED,
            /** Send {@code CLOSING}. */
            CLOSING,
            /** Close without a word. */
            EOF,
            /** Never answer (the client's watchdog must act). */
            HANG,
            /** Do not even read the OPEN (a blocked write on the client). */
            NO_READ
        }

        /** The ordinary script: hello back, the replies, then {@code ACCEPTED}. */
        public static Script accepted(String... aReplies)
        {
            return new Script(null, List.of(aReplies), End.ACCEPTED);
        }


        /** Hello back, the replies, then {@code aEnd}. */
        public static Script ending(End aEnd, String... aReplies)
        {
            return new Script(null, List.of(aReplies), aEnd);
        }


        /** {@code DENIED <reason>} instead of the hello back. */
        public static Script denied(String aReason)
        {
            return new Script(WireCodec.DENIED + "\n" + aReason, List.of(), End.EOF);
        }


        /** A hello back of the given text. */
        public static Script hello(String aHelloReply)
        {
            return new Script(aHelloReply, List.of(), End.ACCEPTED);
        }
    }

    private final Path instanceDir;

    private final Path socketPath;

    private final String token;

    private final ServerSocketChannel server;

    private final Function<Integer, Script> scripts;

    private final List<List<String>> opens = new CopyOnWriteArrayList<>();

    private final List<String> hellos = new CopyOnWriteArrayList<>();

    private final CountDownLatch hanging = new CountDownLatch(1);

    private final Thread acceptor;

    private volatile boolean closed;

    private volatile IOException lastError;

    private int connections;

    /**
     * Binds and writes the endpoint.
     *
     * @param aInstanceDir
     *            the instance directory (the endpoint goes there)
     * @param aSocketDir
     *            where the socket is bound (the instance directory, or a fallback directory)
     * @param aScripts
     *            the script for the n-th connection (0-based)
     */
    public StandInServer(Path aInstanceDir, Path aSocketDir, Function<Integer, Script> aScripts)
        throws IOException
    {
        instanceDir = aInstanceDir;
        scripts = aScripts;
        Files.createDirectories(aSocketDir);
        socketPath = aSocketDir.resolve(Endpoint.SOCKET_PREFIX + hex(16));
        token = hex(64);
        server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socketPath));
        writeEndpoint(instanceDir, socketPath, token);
        acceptor = new Thread(this::acceptLoop, "stand-in-server");
        acceptor.setDaemon(true);
        acceptor.start();
    }


    /** Writes an endpoint file as the application does: {@code .tmp} + atomic move. */
    public static void writeEndpoint(Path aInstanceDir, Path aSocket, String aToken)
        throws IOException
    {
        Files.createDirectories(aInstanceDir);
        Path tmp = aInstanceDir.resolve(Endpoint.FILE_NAME + ".tmp");
        Files.writeString(tmp,
                Endpoint.KEY_SOCKET + "=" + aSocket + "\n" + Endpoint.KEY_TOKEN + "=" + aToken
                        + "\n" + Endpoint.KEY_VERSION + "=" + WireCodec.VERSION + "\n",
                StandardCharsets.UTF_8);
        Files.move(tmp, aInstanceDir.resolve(Endpoint.FILE_NAME), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }


    /** A random hex string. */
    public static String hex(int aChars)
    {
        byte[] bytes = new byte[aChars / 2];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
        {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    public Path socketPath()
    {
        return socketPath;
    }


    public String token()
    {
        return token;
    }


    /** The OPEN path lists received, in order. */
    public List<List<String>> opens()
    {
        return opens;
    }


    /** The tokens of the hellos received. */
    public List<String> hellos()
    {
        return hellos;
    }


    /** The last connection failure seen, for a diagnosis. */
    public IOException lastError()
    {
        return lastError;
    }


    /** Blocks until a connection reached a HANG / NO_READ state. */
    public void awaitHanging() throws InterruptedException
    {
        hanging.await();
    }


    // the accepted channel is closed by the connection thread (serve's try-with-resources)
    @SuppressWarnings("PMD.CloseResource")
    private void acceptLoop()
    {
        while (!closed)
        {
            SocketChannel channel;
            try
            {
                channel = server.accept();
            }
            catch (IOException e)
            {
                return;
            }
            int n;
            synchronized (this)
            {
                n = connections++;
            }
            Script script = scripts.apply(n);
            Thread t = new Thread(() -> serve(channel, script), "stand-in-connection-" + n);
            t.setDaemon(true);
            t.start();
        }
    }


    private void serve(SocketChannel aChannel, Script aScript)
    {
        try (aChannel)
        {
            List<String> hello = WireCodec.lines(WireCodec.read(aChannel));
            String got = hello.size() > 1 ? hello.get(1) : "";
            hellos.add(got);
            if (aScript.helloReply() != null)
            {
                WireCodec.write(aChannel, aScript.helloReply());
                if (aScript.helloReply().startsWith(WireCodec.DENIED))
                {
                    return;
                }
            }
            else if (!MessageDigest.isEqual(got.getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8)))
            {
                WireCodec.write(aChannel, WireCodec.DENIED + "\n" + WireCodec.DENIED_TOKEN);
                return;
            }
            else
            {
                WireCodec.write(aChannel, WireCodec.PROTOCOL + " " + WireCodec.VERSION + "\n"
                        + ProcessHandle.current().pid());
            }
            if (aScript.end() == Script.End.NO_READ)
            {
                hanging.countDown();
                Thread.sleep(Long.MAX_VALUE);
            }
            List<String> open = WireCodec.lines(WireCodec.read(aChannel));
            List<String> paths = new ArrayList<>(open.subList(2, open.size()));
            opens.add(paths);
            for (String reply : aScript.afterOpen())
            {
                WireCodec.write(aChannel, reply);
            }
            switch (aScript.end())
            {
            case ACCEPTED -> WireCodec.write(aChannel, WireCodec.ACCEPTED);
            case CLOSING -> WireCodec.write(aChannel, WireCodec.CLOSING);
            case HANG ->
            {
                hanging.countDown();
                Thread.sleep(Long.MAX_VALUE);
            }
            default ->
            {
                // EOF: just close
            }
            }
        }
        catch (IOException e)
        {
            // the client closed, or the watchdog did: fine for a stand-in, kept for a look
            lastError = e;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }


    @Override
    public void close() throws IOException
    {
        closed = true;
        server.close();
        acceptor.interrupt();
        Files.deleteIfExists(socketPath);
    }
}
