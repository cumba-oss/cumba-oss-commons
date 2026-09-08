package net.cumba.web.api.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.cumba.web.api.ApiException;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Additional edge-case tests for {@link JsonApiClient} covering issues found during code review.
 */
// Anonymous InputStream test fixture; multibyte read performance is irrelevant.
@SuppressWarnings("InputStreamSlowMultibyteRead")
class JsonApiClientEdgeCaseTest
{

    // --- readBodyAsString now throws on success path ---

    @Test
    void getThrowsIOExceptionWhenBodyReadFails()
    {
        HttpTransport failingBodyTransport = _ -> new HttpResponse(200, null,
                new FailingInputStream());

        JsonApiClient client = JsonApiClient.builder().transport(failingBodyTransport)
                .baseUrl("https://api.example.com").build();

        assertThrows(IOException.class, () -> client.get("/test"));
    }

    // --- Caching edge cases ---

    @Nested
    class CachingEdgeCases
    {

        @Test
        void differentPathsGetDifferentCacheEntries(@TempDir Path tempDir) throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "{\"v\":1}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir).build();

            client.getRawJson("/path1");
            client.getRawJson("/path2");
            assertEquals(2, transport.callCount);

            // Both paths should be cached now
            client.getRawJson("/path1");
            client.getRawJson("/path2");
            assertEquals(2, transport.callCount);
        }


        @Test
        void pathNormalizationConsistentForCaching(@TempDir Path tempDir) throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "{\"v\":1}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir).build();

            // Both paths normalize to "/item"
            client.getRawJson("/item");
            client.getRawJson("item");
            // Only one network call because both normalize to "/item"
            assertEquals(1, transport.callCount);
        }


        @Test
        void cacheFileContainsValidJson(@TempDir Path tempDir) throws IOException
        {
            JsonApiClient client = JsonApiClient.builder()
                    .transport(stubTransport(200, "{\"key\":\"value\"}"))
                    .baseUrl("https://example.com").cacheDir(tempDir).build();

            JsonNode first = client.getRawJson("/data");
            assertEquals("value", first.get("key").asText());

            // Read again from cache
            JsonNode second = client.getRawJson("/data");
            assertEquals("value", second.get("key").asText());
        }


        /**
         * A blank 2xx must not reach the cache. getRawJson throws on one, so a cached blank entry
         * would make a single transient empty response a permanent failure - in later JVM runs too,
         * since the cache is on disk and nothing evicts on content.
         */
        @Test
        void blankSuccessBodyIsNotCached(@TempDir Path tempDir) throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir).build();

            assertThrows(ApiException.class, () -> client.getRawJson("/blank"));
            assertEquals(1, transport.callCount);

            List<String> cacheFiles;
            try (Stream<Path> files = Files.list(tempDir))
            {
                cacheFiles = files.map(f -> f.getFileName().toString()).sorted().toList();
            }
            assertEquals(List.of(), cacheFiles, "a blank 2xx must not leave a cache entry");

            // Second call must reach the transport again rather than throw from cache.
            assertThrows(ApiException.class, () -> client.getRawJson("/blank"));
            assertEquals(2, transport.callCount);
        }


        /**
         * The consequence that matters: once the server answers properly again, the client must see
         * it. A cached blank entry would keep throwing instead.
         */
        @Test
        void serverRecoveryAfterBlankResponseIsNotMaskedByCache(@TempDir Path tempDir)
            throws IOException
        {
            BlankThenJsonTransport transport = new BlankThenJsonTransport();
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir).build();

            assertThrows(ApiException.class, () -> client.getRawJson("/flaky"));

            JsonNode recovered = client.getRawJson("/flaky");
            assertEquals(1, recovered.get("v").asInt());
            assertEquals(2, transport.callCount);
        }
    }

    // --- Builder edge cases ---


    @Nested
    class BuilderEdgeCases
    {

        @Test
        void multipleDefaultHeadersWithSameName() throws IOException
        {
            CapturingTransport transport = new CapturingTransport(200, "{}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").defaultHeader("X-Custom", "val1")
                    .defaultHeader("X-Custom", "val2").build();

            client.get("/test");
            // Both values should be present as accumulated headers
            assertNotNull(transport.lastRequest.headers().get("X-Custom"));
            assertEquals(2, transport.lastRequest.headers().get("X-Custom").size());
        }


        @Test
        void baseUrlWithMultipleTrailingSlashes()
        {
            // Only one trailing slash is stripped
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://example.com//").build();
            assertEquals("https://example.com/", client.baseUrl());
        }
    }

    // --- toCacheFileName edge cases ---


    @Nested
    class CacheFileNameEdgeCases
    {

        @Test
        void emptyPathProducesValidFileName()
        {
            String name = new net.cumba.web.api.cache.FileApiCache(java.nio.file.Path.of("."),
                    ".json").toCacheFileName("");
            assertTrue(name.endsWith(".json"));
        }


        @Test
        void pathWithOnlySlashProducesValidFileName()
        {
            String name = new net.cumba.web.api.cache.FileApiCache(java.nio.file.Path.of("."),
                    ".json").toCacheFileName("/");
            assertTrue(name.endsWith(".json"));
        }


        @Test
        void pathWithSpecialCharsIsEncoded()
        {
            String name = new net.cumba.web.api.cache.FileApiCache(java.nio.file.Path.of("."),
                    ".json").toCacheFileName("/api/v1/search?q=hello&page=1");
            assertTrue(name.endsWith(".json"));
            // No raw special chars in the filename
            String withoutExtension = name.substring(0, name.length() - 5);
            assertNotNull(withoutExtension);
        }
    }

    // --- Typed get with ApiResource ---

    @Test
    void getWithApiResourceTypeWorks() throws IOException
    {
        JsonApiClient client = JsonApiClient.builder()
                .transport(stubTransport(200, "{\"name\":\"Study-1\"}"))
                .baseUrl("https://api.example.com").build();

        ApiResource resource = client.get("/study/1", ApiResource.class);
        assertEquals(Optional.of("Study-1"), resource.getString("name"));
    }

    // --- Error handling edge cases ---


    @Test
    void apiExceptionContainsResponseBodyOnError()
    {
        String errorBody = "{\"error\":\"not found\",\"detail\":\"resource does not exist\"}";
        JsonApiClient client = JsonApiClient.builder().transport(stubTransport(404, errorBody))
                .baseUrl("https://api.example.com").build();

        ApiException ex = assertThrows(ApiException.class, () -> client.get("/missing"));
        assertEquals(404, ex.statusCode());
        assertEquals(errorBody, ex.responseBody());
        assertTrue(ex.isClientError());
    }

    // --- F-cdisc-library-04: a 2xx with no JSON content must fail, not yield an
    // empty resource ---


    @Test
    void getRawJsonThrowsOnZeroLengthBody()
    {
        JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, ""))
                .baseUrl("https://api.example.com").build();

        ApiException ex = assertThrows(ApiException.class, () -> client.getRawJson("/empty"));
        assertEquals(200, ex.statusCode());
        assertEquals("Server returned a blank response body", ex.responseBody());
        assertTrue(ex.getMessage().contains("blank response body"), ex.getMessage());
    }


    @Test
    void getPlainThrowsOnZeroLengthBodyInsteadOfEmptyResource()
    {
        JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, ""))
                .baseUrl("https://api.example.com").build();

        ApiException ex = assertThrows(ApiException.class, () -> client.get("/empty"));
        assertEquals("Server returned a blank response body", ex.responseBody());
    }


    @Test
    void getTypedThrowsOnZeroLengthBodyInsteadOfEmptyResource()
    {
        JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, ""))
                .baseUrl("https://api.example.com").build();

        ApiException ex = assertThrows(ApiException.class,
                () -> client.get("/empty", ApiResource.class));
        assertEquals("Server returned a blank response body", ex.responseBody());
    }


    @Test
    void getRawJsonThrowsOnWhitespaceOnlyBody()
    {
        JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, " \n\t "))
                .baseUrl("https://api.example.com").build();

        ApiException ex = assertThrows(ApiException.class, () -> client.getRawJson("/blank"));
        assertEquals("Server returned a blank response body", ex.responseBody());
    }


    /**
     * The null-body and blank-body failures must stay diagnosable apart: same exception type and
     * status, different message.
     */
    @Test
    void nullBodyAndBlankBodyReportDistinctMessages()
    {
        JsonApiClient nullBodyClient = JsonApiClient.builder()
                .transport(_ -> new HttpResponse(200, null, null))
                .baseUrl("https://api.example.com").build();
        JsonApiClient blankBodyClient = JsonApiClient.builder().transport(stubTransport(200, ""))
                .baseUrl("https://api.example.com").build();

        ApiException nullEx = assertThrows(ApiException.class,
                () -> nullBodyClient.getRawJson("/null"));
        ApiException blankEx = assertThrows(ApiException.class,
                () -> blankBodyClient.getRawJson("/blank"));

        assertEquals("Server returned empty response body", nullEx.responseBody());
        assertEquals("Server returned a blank response body", blankEx.responseBody());
        assertTrue(!nullEx.getMessage().equals(blankEx.getMessage()), "messages must differ");
    }


    @Test
    void nonBlankBodyStillParses() throws IOException
    {
        JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                .baseUrl("https://api.example.com").build();

        JsonNode node = client.getRawJson("/ok");
        assertNotNull(node);
        assertTrue(node.isObject());
    }

    // --- Helpers ---


    private static HttpTransport stubTransport(int statusCode, String body)
    {
        return _ -> new HttpResponse(statusCode, null,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static class CapturingTransport implements HttpTransport
    {

        private final int statusCode;

        private final String body;

        HttpRequest lastRequest;

        CapturingTransport(int statusCode, String body)
        {
            this.statusCode = statusCode;
            this.body = body;
        }


        @Override
        public HttpResponse send(HttpRequest request)
        {
            this.lastRequest = request;
            return new HttpResponse(statusCode, null,
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
    }


    private static class CountingTransport implements HttpTransport
    {

        private final int statusCode;

        private final String body;

        int callCount = 0;

        CountingTransport(int statusCode, String body)
        {
            this.statusCode = statusCode;
            this.body = body;
        }


        @Override
        public HttpResponse send(HttpRequest request)
        {
            callCount++;
            return new HttpResponse(statusCode, null,
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
    }


    /**
     * Answers the first call with a blank 2xx body and every later call with real JSON - the
     * transient-blank-response scenario the cache must not make permanent.
     */
    private static class BlankThenJsonTransport implements HttpTransport
    {

        int callCount = 0;

        @Override
        public HttpResponse send(HttpRequest request)
        {
            callCount++;
            String body = callCount == 1 ? "" : "{\"v\":1}";
            return new HttpResponse(200, null,
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
    }


    private static class FailingInputStream extends InputStream
    {

        @Override
        public int read() throws IOException
        {
            throw new IOException("simulated read failure");
        }


        @Override
        public byte[] readAllBytes() throws IOException
        {
            throw new IOException("simulated read failure");
        }
    }
}
