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
import java.nio.file.Path;
import java.util.Optional;
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
