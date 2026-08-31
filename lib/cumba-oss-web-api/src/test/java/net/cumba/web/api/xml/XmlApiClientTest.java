package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import net.cumba.web.api.ApiException;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.cache.CacheValidator;
import net.cumba.web.api.http.JdkHttpTransport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlApiClientTest
{

    @Nested
    class GetRequests
    {

        interface StudyDef extends ApiResource
        {

            default Optional<String> oid()
            {
                return getString("OID");
            }


            default Optional<String> studyName()
            {
                return getString("StudyName");
            }
        }

        @Test
        void getParsesXmlResponse() throws Exception
        {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Study OID="S.001">
                        <StudyName>My Clinical Trial</StudyName>
                    </Study>
                    """;

            try (TestServer server = new TestServer(xml, 200, "application/xml");
                    JdkHttpTransport transport = new JdkHttpTransport())
            {
                XmlApiClient client = XmlApiClient.builder().transport(transport)
                        .baseUrl(server.baseUrl()).defaultHeader("Accept", "application/xml")
                        .build();

                StudyDef study = client.get("/study/S.001", StudyDef.class);

                assertNotNull(study);
                assertEquals("S.001", study.oid().orElse(null));
                assertEquals("My Clinical Trial", study.studyName().orElse(null));
            }
        }


        @Test
        void getPlainReturnsApiResource() throws Exception
        {
            String xml = "<Root><Name>Test</Name></Root>";

            try (TestServer server = new TestServer(xml, 200, "application/xml");
                    JdkHttpTransport transport = new JdkHttpTransport())
            {
                XmlApiClient client = XmlApiClient.builder().transport(transport)
                        .baseUrl(server.baseUrl()).build();

                ApiResource res = client.get("/test");
                assertEquals("Test", res.getString("Name").orElse(null));
            }
        }


        @Test
        void httpErrorThrowsApiException() throws Exception
        {
            try (TestServer server = new TestServer("<Error>Not Found</Error>", 404,
                    "application/xml"); JdkHttpTransport transport = new JdkHttpTransport())
            {
                XmlApiClient client = XmlApiClient.builder().transport(transport)
                        .baseUrl(server.baseUrl()).build();

                ApiException ex = assertThrows(ApiException.class, () -> client.get("/missing"));
                assertEquals(404, ex.statusCode());
            }
        }
    }


    @Nested
    class CacheFileName
    {

        @Test
        void convertsPathToXmlExtension()
        {
            assertEquals("study_S.001.xml",
                    new net.cumba.web.api.cache.FileApiCache(java.nio.file.Path.of("."), ".xml")
                            .toCacheFileName("/study/S.001"));
        }


        @Test
        void stripsLeadingSlash()
        {
            assertEquals("root.xml",
                    new net.cumba.web.api.cache.FileApiCache(java.nio.file.Path.of("."), ".xml")
                            .toCacheFileName("/root"));
        }
    }


    @Nested
    class BuilderTests
    {

        @Test
        void builderRequiresTransportAndBaseUrl()
        {
            assertThrows(NullPointerException.class,
                    () -> XmlApiClient.builder().baseUrl("http://example.com").build());

            assertThrows(NullPointerException.class,
                    () -> XmlApiClient.builder().transport(new JdkHttpTransport()).build());
        }


        @Test
        void builderStripsTrailingSlash()
        {
            try (JdkHttpTransport transport = new JdkHttpTransport())
            {
                XmlApiClient client = XmlApiClient.builder().transport(transport)
                        .baseUrl("http://example.com/").build();

                assertEquals("http://example.com", client.baseUrl());
            }
        }


        @Test
        void cacheValidatorIsPassedToFileCache(@TempDir Path tempDir)
        {
            CacheValidator validator = (_, _, _) -> true;
            try (JdkHttpTransport transport = new JdkHttpTransport())
            {
                XmlApiClient client = XmlApiClient.builder().transport(transport)
                        .baseUrl("http://example.com").cacheDir(tempDir).cacheValidator(validator)
                        .build();
                assertInstanceOf(net.cumba.web.api.cache.FileApiCache.class, client.cache());
            }
        }
    }


    @Nested
    class CacheValidation
    {

        @Test
        void cacheValidatorRejectsEntry(@TempDir Path tempDir) throws Exception
        {
            String xml = "<Root><Name>Test</Name></Root>";
            CacheValidator rejectAll = (_, _, _) -> false;

            try (TestServer server = new TestServer(xml, 200, "application/xml");
                    JdkHttpTransport transport = new JdkHttpTransport())
            {
                XmlApiClient client = XmlApiClient.builder().transport(transport)
                        .baseUrl(server.baseUrl()).cacheDir(tempDir).cacheValidator(rejectAll)
                        .build();

                // First call fetches from network and caches
                ApiResource first = client.get("/test");
                assertEquals("Test", first.getString("Name").orElse(null));

                // Second call: validator rejects cache, fetches from network again
                ApiResource second = client.get("/test");
                assertEquals("Test", second.getString("Name").orElse(null));
            }
        }
    }

    // --- Embedded HTTP server helper ---


    private static class TestServer implements AutoCloseable
    {

        private final HttpServer server;

        TestServer(String responseBody, int statusCode, String contentType) throws IOException
        {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            server.createContext("/", exchange ->
            {
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(statusCode, body.length);
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
