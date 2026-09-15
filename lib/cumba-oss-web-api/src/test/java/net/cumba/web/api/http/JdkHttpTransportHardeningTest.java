package net.cumba.web.api.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@link JdkHttpTransport} paths that carry a wrong answer rather than an error: the
 * content-negotiation predicates, the gzip-decode branch, interrupt handling, and {@code close}.
 */
class JdkHttpTransportHardeningTest
{

    @Test
    void gzipIsStillNegotiatedWhenTheCallerSetsAnUnrelatedHeader() throws IOException
    {
        AtomicReference<List<String>> seen = new AtomicReference<>();
        try (TestServer server = new TestServer(exchange ->
        {
            seen.set(exchange.getRequestHeaders().get("Accept-Encoding"));
            respondPlain(exchange, "ok");
        }); JdkHttpTransport transport = new JdkHttpTransport())
        {
            HttpRequest request = HttpRequest.get(server.uri("/x")).header("X-Trace", "1").build();
            try (HttpResponse response = transport.send(request))
            {
                assertEquals("ok", bodyAsString(response));
            }
        }
        assertEquals(List.of("gzip"), seen.get(),
                "an unrelated caller header must not be mistaken for Accept-Encoding");
    }


    @Test
    void aNonGzipContentEncodingIsNotDecoded() throws IOException
    {
        try (TestServer server = new TestServer(exchange ->
        {
            exchange.getResponseHeaders().set("Content-Encoding", "deflate");
            respondPlain(exchange, "plain-not-deflated");
        }); JdkHttpTransport transport = new JdkHttpTransport())
        {
            try (HttpResponse response = transport.send(HttpRequest.get(server.uri("/x")).build()))
            {
                assertEquals("plain-not-deflated", bodyAsString(response),
                        "only 'gzip' may be fed to the gzip decoder");
                assertEquals("deflate", headerIgnoreCase(response, "Content-Encoding"),
                        "an encoding we did not decode must stay described on the response");
            }
        }
    }


    @Test
    void decodingGzipRemovesOnlyTheEncodingHeaders() throws IOException
    {
        byte[] payload = gzip("decoded");
        try (TestServer server = new TestServer(exchange ->
        {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.getResponseHeaders().set("X-Keep", "yes");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody())
            {
                os.write(payload);
            }
        }); JdkHttpTransport transport = new JdkHttpTransport())
        {
            try (HttpResponse response = transport.send(HttpRequest.get(server.uri("/x")).build()))
            {
                assertEquals("decoded", bodyAsString(response));
                assertEquals("yes", headerIgnoreCase(response, "X-Keep"),
                        "stripping the encoding headers must not strip every other header");
                assertNullHeader(response, "Content-Encoding");
                assertNullHeader(response, "Content-Length");
            }
        }
    }


    @Test
    void aNullRequestTimeoutStillSends() throws IOException
    {
        HttpClient client = HttpClient.newHttpClient();
        try (TestServer server = new TestServer(exchange -> respondPlain(exchange, "ok"));
                JdkHttpTransport transport = new JdkHttpTransport(client, null))
        {
            try (HttpResponse response = transport.send(HttpRequest.get(server.uri("/x")).build()))
            {
                assertEquals(200, response.statusCode());
                assertEquals("ok", bodyAsString(response),
                        "a null timeout means 'do not set one', never 'set a null one'");
            }
        }
    }


    @Test
    void anInterruptedSendRaisesIoExceptionAndReArmsTheInterruptFlag() throws Exception
    {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any())).thenThrow(new InterruptedException("stopped"));

        JdkHttpTransport transport = new JdkHttpTransport(client);
        HttpRequest request = HttpRequest.get("http://localhost:1/x").build();

        IOException failure = assertThrows(IOException.class, () -> transport.send(request));

        // Read-and-clear, so the flag cannot leak into another test in this JVM.
        assertTrue(Thread.interrupted(),
                "swallowing the interrupt would leave the caller unable to stop");
        assertInstanceOf(InterruptedException.class, failure.getCause());
        assertTrue(failure.getMessage().contains("HTTP request interrupted"), failure.getMessage());
    }


    @Test
    void closeTerminatesTheUnderlyingClient()
    {
        HttpClient client = HttpClient.newHttpClient();
        assertFalse(client.isTerminated(), "precondition: the client is live");
        try (JdkHttpTransport transport = new JdkHttpTransport(client))
        {
            assertInstanceOf(HttpTransport.class, transport);
        }
        assertTrue(client.isTerminated(),
                "close() must release the client's threads and connections");
    }

    // --- helpers ---


    private static void respondPlain(HttpExchange aExchange, String aText) throws IOException
    {
        byte[] body = aText.getBytes(StandardCharsets.UTF_8);
        aExchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = aExchange.getResponseBody())
        {
            os.write(body);
        }
    }


    private static byte[] gzip(String aText) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos))
        {
            gos.write(aText.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }


    private static String bodyAsString(HttpResponse aResponse) throws IOException
    {
        return new String(aResponse.body().readAllBytes(), StandardCharsets.UTF_8);
    }


    private static String headerIgnoreCase(HttpResponse aResponse, String aName)
    {
        return aResponse.headers().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(aName)).map(e -> e.getValue().get(0))
                .findFirst().orElse(null);
    }


    private static void assertNullHeader(HttpResponse aResponse, String aName)
    {
        assertEquals(null, headerIgnoreCase(aResponse, aName), aName + " must be gone");
    }

    /** Minimal embedded HTTP server that delegates each exchange to a supplied handler. */
    private static final class TestServer implements AutoCloseable
    {

        private final HttpServer server;

        TestServer(HttpHandler aHandler) throws IOException
        {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", aHandler);
            server.start();
        }


        URI uri(String aPath)
        {
            return URI.create("http://localhost:" + server.getAddress().getPort() + aPath);
        }


        @Override
        public void close()
        {
            server.stop(0);
        }
    }
}
