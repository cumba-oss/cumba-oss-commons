package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.cumba.web.api.ApiException;
import net.cumba.web.api.http.JdkHttpTransport;
import org.junit.jupiter.api.Test;

/**
 * Tests for the mutation-hardening fixes in {@link XmlApiClient}.
 *
 * <ul>
 * <li>F-webapi-05 — a 2xx response with a blank body must throw {@link ApiException} (as
 * {@code JsonApiClient.getRawJson} does), not a generic {@code IOException} out of the XML
 * parser.</li>
 * <li>F-webapi-06 — the XXE guard: a document with a DOCTYPE must be rejected, and a JAXP factory
 * that does not support a hardening feature must be logged at WARNING instead of being silently
 * accepted.</li>
 * </ul>
 */
class XmlApiClientHardeningTest
{

    // ---- F-webapi-05: blank body ----

    @Test
    void blankBodyThrowsApiExceptionNotIoException() throws Exception
    {
        try (TestServer server = new TestServer("   \n", 200, "application/xml");
                JdkHttpTransport transport = new JdkHttpTransport())
        {
            XmlApiClient client = XmlApiClient.builder().transport(transport)
                    .baseUrl(server.baseUrl()).build();

            ApiException ex = assertThrows(ApiException.class, () -> client.getRawXml("/x"));
            assertEquals(200, ex.statusCode());
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("blank"),
                    "message should name the blank body, was: " + ex.getMessage());
        }
    }


    @Test
    void nonBlankGarbageStillFailsAsParseError() throws Exception
    {
        try (TestServer server = new TestServer("this is not xml", 200, "application/xml");
                JdkHttpTransport transport = new JdkHttpTransport())
        {
            XmlApiClient client = XmlApiClient.builder().transport(transport)
                    .baseUrl(server.baseUrl()).build();

            IOException ex = assertThrows(IOException.class, () -> client.getRawXml("/x"));
            assertTrue(ex.getMessage().contains("Failed to parse XML response"));
        }
    }

    // ---- F-webapi-06: XXE guard ----


    @Test
    void aDocumentWithADoctypeIsRejected() throws Exception
    {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Root [<!ENTITY xxe "boom">]>
                <Root>&xxe;</Root>
                """;
        try (TestServer server = new TestServer(xml, 200, "application/xml");
                JdkHttpTransport transport = new JdkHttpTransport())
        {
            XmlApiClient client = XmlApiClient.builder().transport(transport)
                    .baseUrl(server.baseUrl()).build();

            // DOCTYPE processing is disabled, so this must fail rather than expand the
            // entity. (Pin for the setFeature call — the mutant removing it survives
            // without this test.)
            assertThrows(IOException.class, () -> client.getRawXml("/x"));
        }
    }


    @Test
    void aFactoryRejectingAHardeningFeatureIsLoggedAtWarning() throws Exception
    {
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler capture = new Handler()
        {

            @Override
            public void publish(LogRecord aRecord)
            {
                records.add(aRecord);
            }


            @Override
            public void flush()
            {
                // nothing buffered
            }


            @Override
            public void close()
            {
                // nothing held
            }
        };
        java.util.logging.Logger julLogger = java.util.logging.Logger
                .getLogger(XmlApiClient.class.getName());
        julLogger.addHandler(capture);
        String prop = "javax.xml.parsers.DocumentBuilderFactory";
        String saved = System.getProperty(prop);
        System.setProperty(prop, FeatureRejectingFactory.class.getName());
        try (JdkHttpTransport transport = new JdkHttpTransport())
        {
            XmlApiClient.builder().transport(transport).baseUrl("http://localhost").build();

            boolean warned = records.stream()
                    .anyMatch(r -> r.getLevel().intValue() >= Level.WARNING.intValue()
                            && String.valueOf(r.getMessage()).contains("disallow-doctype-decl"));
            assertTrue(warned, "losing the doctype guard must be logged at WARNING, records: "
                    + records.stream().map(LogRecord::getMessage).toList());
        }
        finally
        {
            julLogger.removeHandler(capture);
            if (saved != null)
            {
                System.setProperty(prop, saved);
            }
            else
            {
                System.clearProperty(prop);
            }
        }
    }

    /**
     * A JAXP factory that behaves like the platform default but claims not to support the
     * doctype-disallowing feature — the third-party-parser scenario from F-webapi-06.
     */
    public static class FeatureRejectingFactory extends DocumentBuilderFactory
    {

        private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newDefaultInstance();

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException
        {
            return delegate.newDocumentBuilder();
        }


        @Override
        public void setAttribute(String aName, Object aValue)
        {
            delegate.setAttribute(aName, aValue);
        }


        @Override
        public Object getAttribute(String aName)
        {
            return delegate.getAttribute(aName);
        }


        @Override
        public void setFeature(String aName, boolean aValue) throws ParserConfigurationException
        {
            if ("http://apache.org/xml/features/disallow-doctype-decl".equals(aName))
            {
                throw new ParserConfigurationException("Feature not supported: " + aName);
            }
            delegate.setFeature(aName, aValue);
        }


        @Override
        public boolean getFeature(String aName) throws ParserConfigurationException
        {
            return delegate.getFeature(aName);
        }
    }

    // ---- Test HTTP server (mirrors XmlApiClientTest.TestServer) ----


    private static class TestServer implements AutoCloseable
    {

        private final HttpServer server;

        TestServer(String aResponseBody, int aStatusCode, String aContentType) throws IOException
        {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            byte[] body = aResponseBody.getBytes(StandardCharsets.UTF_8);
            server.createContext("/", exchange ->
            {
                exchange.getResponseHeaders().set("Content-Type", aContentType);
                exchange.sendResponseHeaders(aStatusCode, body.length);
                try (OutputStream os = exchange.getResponseBody())
                {
                    os.write(body);
                }
            });
            server.start();
        }


        String baseUrl()
        {
            return "http://localhost:" + server.getAddress().getPort();
        }


        @Override
        public void close()
        {
            server.stop(0);
        }
    }
}
