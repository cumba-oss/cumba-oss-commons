package net.cumba.web.api.http;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class JdkHttpTransportTest
{

    @Test
    void defaultConstructorCreatesInstance()
    {
        JdkHttpTransport transport = new JdkHttpTransport();
        assertNotNull(transport);
    }


    @Test
    void customClientConstructor()
    {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkHttpTransport transport = new JdkHttpTransport(client);
        assertNotNull(transport);
    }


    @Test
    void implementsHttpTransport()
    {
        JdkHttpTransport transport = new JdkHttpTransport();
        assertInstanceOf(HttpTransport.class, transport);
    }


    @Test
    void defaultRequestTimeoutIsSixtySeconds()
    {
        assertEquals(Duration.ofSeconds(60), JdkHttpTransport.DEFAULT_REQUEST_TIMEOUT);
    }


    @Test
    void customRequestTimeoutAccepted()
    {
        HttpClient client = HttpClient.newHttpClient();
        // Should not throw; the timeout is honoured at send() time, not at construction.
        JdkHttpTransport transport = new JdkHttpTransport(client, Duration.ofSeconds(5));
        assertNotNull(transport);
    }


    @Test
    void nullRequestTimeoutDisablesTimeout()
    {
        HttpClient client = HttpClient.newHttpClient();
        // Passing null is the documented way to opt out of the timeout; the constructor must not
        // reject it.
        JdkHttpTransport transport = new JdkHttpTransport(client, null);
        assertNotNull(transport);
    }

    // --- gzip negotiation / decoding ---


    @Test
    void sendsAcceptEncodingGzipByDefault() throws IOException
    {
        AtomicReference<String> seen = new AtomicReference<>();
        try (TestServer server = new TestServer(exchange ->
        {
            seen.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            respondPlain(exchange, "ok");
        }); JdkHttpTransport transport = new JdkHttpTransport())
        {
            try (HttpResponse response = transport.send(HttpRequest.get(server.uri("/")).build()))
            {
                assertEquals("ok", bodyAsString(response));
            }
        }
        assertEquals("gzip", seen.get());
    }


    @Test
    void decodesGzipResponseTransparently() throws IOException
    {
        try (TestServer server = new TestServer(exchange ->
        {
            byte[] gzipped = gzip("compressed payload");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, gzipped.length);
            try (OutputStream os = exchange.getResponseBody())
            {
                os.write(gzipped);
            }
        }); JdkHttpTransport transport = new JdkHttpTransport())
        {
            try (HttpResponse response = transport.send(HttpRequest.get(server.uri("/")).build()))
            {
                assertEquals("compressed payload", bodyAsString(response));
            }
        }
    }


    @Test
    void stripsEncodingHeadersAfterDecoding() throws IOException
    {
        try (TestServer server = new TestServer(exchange ->
        {
            byte[] gzipped = gzip("payload");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, gzipped.length);
            try (OutputStream os = exchange.getResponseBody())
            {
                os.write(gzipped);
            }
        }); JdkHttpTransport transport = new JdkHttpTransport())
        {
            try (HttpResponse response = transport.send(HttpRequest.get(server.uri("/")).build()))
            {
                assertEquals("payload", bodyAsString(response));
                // Headers describing the encoded form must not survive onto the decoded response,
                // matched case-insensitively regardless of how the server cased them.
                assertTrue(headerMissingIgnoreCase(response, "Content-Encoding"),
                        "Content-Encoding should be stripped after decoding");
                assertTrue(headerMissingIgnoreCase(response, "Content-Length"),
                        "Content-Length should be stripped after decoding");
            }
        }
    }


    @Test
    void doesNotOverrideCallerAcceptEncoding() throws IOException
    {
        AtomicReference<List<String>> seen = new AtomicReference<>();
        try (TestServer server = new TestServer(exchange ->
        {
            seen.set(exchange.getRequestHeaders().get("Accept-Encoding"));
            respondPlain(exchange, "ok");
        }); JdkHttpTransport transport = new JdkHttpTransport())
        {
            // Lowercase header name: the transport must still recognise it and not add a duplicate.
            HttpRequest request = HttpRequest.get(server.uri("/"))
                    .header("accept-encoding", "identity").build();
            try (HttpResponse response = transport.send(request))
            {
                assertEquals("ok", bodyAsString(response));
            }
        }
        assertEquals(List.of("identity"), seen.get(),
                "caller-supplied Accept-Encoding must be preserved without a gzip duplicate");
    }


    @Test
    void plainResponsePassesThroughUnchanged() throws IOException
    {
        try (TestServer server = new TestServer(exchange -> respondPlain(exchange, "plain body"));
                JdkHttpTransport transport = new JdkHttpTransport())
        {
            try (HttpResponse response = transport.send(HttpRequest.get(server.uri("/")).build()))
            {
                assertEquals(200, response.statusCode());
                assertEquals("plain body", bodyAsString(response));
            }
        }
    }

    // --- helpers ---


    private static void respondPlain(HttpExchange exchange, String text) throws IOException
    {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(body);
        }
    }


    private static byte[] gzip(String text) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos))
        {
            gos.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }


    private static String bodyAsString(HttpResponse response) throws IOException
    {
        return new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
    }


    private static boolean headerMissingIgnoreCase(HttpResponse response, String name)
    {
        return response.headers().keySet().stream().noneMatch(key -> key.equalsIgnoreCase(name));
    }

    /** Minimal embedded HTTP server that delegates each exchange to a supplied handler. */
    private static final class TestServer implements AutoCloseable
    {

        private final HttpServer server;

        TestServer(HttpHandler handler) throws IOException
        {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", handler);
            server.start();
        }


        URI uri(String path)
        {
            return URI.create("http://localhost:" + server.getAddress().getPort() + path);
        }


        @Override
        public void close()
        {
            server.stop(0);
        }
    }
}
