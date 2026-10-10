package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WireCodecTest
{

    @TempDir
    Path tmp;

    /** An in-memory channel: what was written is what is read. */
    private static final class Loopback implements ByteChannel
    {

        private final ByteBuffer data;

        Loopback(byte[] aBytes)
        {
            data = ByteBuffer.wrap(aBytes);
        }


        @Override
        public int read(ByteBuffer aDst)
        {
            if (!data.hasRemaining())
            {
                return -1;
            }
            int n = Math.min(aDst.remaining(), data.remaining());
            for (int i = 0; i < n; i++)
            {
                aDst.put(data.get());
            }
            return n;
        }


        @Override
        public int write(ByteBuffer aSrc)
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public boolean isOpen()
        {
            return true;
        }


        @Override
        public void close()
        {
        }
    }


    /**
     * A channel that moves at most one byte per call and answers every other read with 0 bytes -
     * what a channel may do: a short read, and an empty one, are not the end of the stream.
     */
    private static final class Trickle implements ByteChannel
    {

        private final ByteBuffer data;

        private final ByteArrayOutputStream written = new ByteArrayOutputStream();

        private boolean idle;

        Trickle(byte[] aBytes)
        {
            data = ByteBuffer.wrap(aBytes);
        }


        @Override
        public int read(ByteBuffer aDst)
        {
            idle = !idle;
            if (idle)
            {
                return 0;
            }
            if (!data.hasRemaining())
            {
                return -1;
            }
            aDst.put(data.get());
            return 1;
        }


        @Override
        public int write(ByteBuffer aSrc)
        {
            int n = Math.min(aSrc.remaining(), 64 * 1024);
            for (int i = 0; i < n; i++)
            {
                written.write(aSrc.get());
            }
            return n;
        }


        byte[] written()
        {
            return written.toByteArray();
        }


        @Override
        public boolean isOpen()
        {
            return true;
        }


        @Override
        public void close()
        {
        }
    }

    @Test
    void framesRoundTripOverAUnixDomainSocket() throws Exception
    {
        Path sock = tmp.resolve("s-codec");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX))
        {
            server.bind(UnixDomainSocketAddress.of(sock));
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(sock));
                    SocketChannel accepted = server.accept())
            {
                WireCodec.write(client, WireCodec.hello("t0k"));
                WireCodec.write(client, WireCodec.open(List.of("/a/ä.sas7bdat", "/b c")));
                WireCodec.write(client, "");
                assertEquals(List.of("CUMBA-INSTANCE 1", "t0k"),
                        WireCodec.lines(WireCodec.read(accepted)));
                assertEquals(List.of("OPEN", "2", "/a/ä.sas7bdat", "/b c"),
                        WireCodec.lines(WireCodec.read(accepted)));
                assertEquals("", WireCodec.read(accepted), "an empty frame is legal");
                client.close();
                assertThrows(EOFException.class, () -> WireCodec.read(accepted));
            }
        }
    }


    @Test
    void linesStripACarriageReturnAndKeepEmptyOnes()
    {
        assertEquals(List.of("BUSY", "STARTING"), WireCodec.lines("BUSY\r\nSTARTING"));
        assertEquals(List.of("OPEN", "0"), WireCodec.lines("OPEN\n0"));
        assertEquals(List.of("ACCEPTED", ""), WireCodec.lines("ACCEPTED\n"));
        assertEquals(List.of(""), WireCodec.lines(""));
    }


    @Test
    void aLengthOverTheCapIsRefusedBeforeAnyAllocation()
    {
        // 2 GiB - 1: an allocation of that size would fail or take seconds; the cap check runs
        // first
        byte[] header = ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).array();
        IOException ex = assertThrows(IOException.class,
                () -> WireCodec.read(new Loopback(header)));
        assertTrue(ex.getMessage().contains("exceeds the cap"), ex.getMessage());
        byte[] justOver = ByteBuffer.allocate(4).putInt(WireCodec.MAX_FRAME_BYTES + 1).array();
        assertTrue(assertThrows(IOException.class, () -> WireCodec.read(new Loopback(justOver)))
                .getMessage().contains("exceeds the cap"));
        byte[] negative = ByteBuffer.allocate(4).putInt(-5).array();
        assertTrue(assertThrows(IOException.class, () -> WireCodec.read(new Loopback(negative)))
                .getMessage().contains("exceeds the cap"));
    }


    @Test
    void aFrameCutShortIsEof()
    {
        byte[] bytes = ByteBuffer.allocate(4 + 2).putInt(5)
                .put("ab".getBytes(StandardCharsets.UTF_8)).array();
        assertThrows(EOFException.class, () -> WireCodec.read(new Loopback(bytes)));
        assertThrows(EOFException.class, () -> WireCodec.read(new Loopback(new byte[2])),
                "a header cut short");
    }


    @Test
    void anEmptyReadIsNotTheEndOfTheStream() throws Exception
    {
        try (Trickle out = new Trickle(new byte[0]))
        {
            WireCodec.write(out, "BUSY\nSTARTING");
            assertEquals("BUSY\nSTARTING", WireCodec.read(new Trickle(out.written())));
        }
    }


    @Test
    void aFrameOfExactlyTheCapIsWrittenAndRead() throws Exception
    {
        // the cap is inclusive on both sides; the writer loops over partial writes
        String text = "x".repeat(WireCodec.MAX_FRAME_BYTES);
        byte[] frame;
        try (Trickle out = new Trickle(new byte[0]))
        {
            WireCodec.write(out, text);
            frame = out.written();
        }
        assertEquals(4 + WireCodec.MAX_FRAME_BYTES, frame.length);
        assertEquals(WireCodec.MAX_FRAME_BYTES, ByteBuffer.wrap(frame).getInt());
        assertEquals(text, WireCodec.read(new Loopback(frame)));
    }


    @Test
    void aTextOverTheCapIsNotWritten()
    {
        List<String> paths = new ArrayList<>();
        String big = "x".repeat(WireCodec.MAX_PATH_BYTES);
        for (int i = 0; i < WireCodec.MAX_PATHS + 20; i++)
        {
            paths.add(big);
        }
        String frame = WireCodec.open(paths);
        assertTrue(frame.getBytes(StandardCharsets.UTF_8).length > WireCodec.MAX_FRAME_BYTES);
        assertThrows(IOException.class, () -> WireCodec.write(new Loopback(new byte[0]), frame));
        // and the largest legal OPEN fits
        String legal = WireCodec.open(paths.subList(0, WireCodec.MAX_PATHS));
        assertTrue(legal.getBytes(StandardCharsets.UTF_8).length <= WireCodec.MAX_FRAME_BYTES);
    }


    @Test
    void theConstantsAreTheProtocol()
    {
        assertEquals("CUMBA-INSTANCE 1\nabc", WireCodec.hello("abc"));
        assertEquals("OPEN\n0", WireCodec.open(List.of()));
        assertEquals(256, WireCodec.MAX_PATHS);
        assertEquals(32 * 1024, WireCodec.MAX_PATH_BYTES);
        assertEquals(8 * 1024 * 1024 + 64 * 1024, WireCodec.MAX_FRAME_BYTES);
    }
}
