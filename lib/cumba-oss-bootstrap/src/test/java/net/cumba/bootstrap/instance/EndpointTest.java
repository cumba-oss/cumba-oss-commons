package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EndpointTest
{

    @TempDir
    Path tmp;

    @Test
    void parsesSocketTokenAndVersion()
    {
        Endpoint e = Endpoint.parse(List.of("# written by the app", "socket=/run/x/s-ab",
                "token = abc123 ", "version=1", "future-key=ignored")).orElseThrow();
        assertEquals(Path.of("/run/x/s-ab"), e.socket().orElseThrow());
        assertEquals("abc123", e.token().orElseThrow());
        assertEquals(1, e.version());
        assertFalse(e.noListener());
    }


    @Test
    void listenerNoneNeedsNoSocketOrToken()
    {
        Endpoint e = Endpoint.parse(List.of("listener=none", "version=1")).orElseThrow();
        assertTrue(e.noListener());
        assertEquals(Optional.empty(), e.socket());
        assertEquals(Optional.empty(), e.token());
        assertEquals(1, e.version());
    }


    @Test
    void malformedReadsAsNoEndpoint()
    {
        assertEquals(Optional.empty(), Endpoint.parse(List.of()), "empty file");
        assertEquals(Optional.empty(), Endpoint.parse(List.of("socket=/s", "version=1")),
                "no token");
        assertEquals(Optional.empty(), Endpoint.parse(List.of("token=t", "version=1")),
                "no socket");
        assertEquals(Optional.empty(), Endpoint.parse(List.of("socket=/s", "token=t")),
                "no version");
        assertEquals(Optional.empty(),
                Endpoint.parse(List.of("socket=/s", "token=t", "version=one")), "bad version");
        assertEquals(Optional.empty(), Endpoint.parse(List.of("socket=", "token=t", "version=1")),
                "blank socket");
        assertEquals(Optional.empty(), Endpoint.parse(List.of("socket=/s", "token=", "version=1")),
                "blank token");
        assertEquals(Optional.empty(),
                Endpoint.parse(List.of("socket=/s\0bad", "token=t", "version=1")),
                "a path the file system rejects");
        assertEquals(Optional.empty(), Endpoint.parse(List.of("no equals sign", "version=1")),
                "a line without '=' is skipped, the rest is still incomplete");
    }


    @Test
    void readsTheFileAndTreatsAMissingOneAsNone() throws Exception
    {
        Path file = tmp.resolve(Endpoint.FILE_NAME);
        assertEquals(Optional.empty(), Endpoint.read(file));
        Files.writeString(file, "socket=" + tmp.resolve("s-1") + "\ntoken=t\nversion=2\n",
                StandardCharsets.UTF_8);
        Endpoint e = Endpoint.read(file).orElseThrow();
        assertEquals(tmp.resolve("s-1"), e.socket().orElseThrow());
        assertEquals(2, e.version());
        // a directory in its place is unreadable: none
        Files.delete(file);
        Files.createDirectory(file);
        assertEquals(Optional.empty(), Endpoint.read(file));
    }
}
