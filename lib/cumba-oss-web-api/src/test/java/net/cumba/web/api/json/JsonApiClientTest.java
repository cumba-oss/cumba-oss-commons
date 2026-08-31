package net.cumba.web.api.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.cumba.web.api.ApiException;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.cache.CacheValidator;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonApiClientTest
{

    // --- Builder tests ---

    @Nested
    class BuilderTests
    {

        @Test
        void buildRequiresTransport()
        {
            assertThrows(NullPointerException.class,
                    () -> JsonApiClient.builder().baseUrl("https://api.example.com").build());
        }


        @Test
        void buildRequiresBaseUrl()
        {
            assertThrows(NullPointerException.class,
                    () -> JsonApiClient.builder().transport(stubTransport(200, "{}")).build());
        }


        @Test
        void baseUrlStripsTrailingSlash()
        {
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://api.example.com/").build();
            assertEquals("https://api.example.com", client.baseUrl());
        }


        @Test
        void baseUrlPreservedWithoutTrailingSlash()
        {
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://api.example.com").build();
            assertEquals("https://api.example.com", client.baseUrl());
        }


        @Test
        void transportAccessor()
        {
            HttpTransport transport = stubTransport(200, "{}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").build();
            assertSame(transport, client.transport());
        }


        @Test
        void objectMapperDefaultsToNew()
        {
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://example.com").build();
            assertNotNull(client.objectMapper());
        }


        @Test
        void objectMapperCanBeCustomized()
        {
            ObjectMapper custom = new ObjectMapper();
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://example.com").objectMapper(custom).build();
            assertSame(custom, client.objectMapper());
        }


        @Test
        void cacheDefaultsToNoOp()
        {
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://example.com").build();
            assertInstanceOf(net.cumba.web.api.cache.NoOpApiCache.class, client.cache());
        }


        @Test
        void cacheDirCreatesFileCache(@TempDir Path tempDir)
        {
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://example.com").cacheDir(tempDir).build();
            assertInstanceOf(net.cumba.web.api.cache.FileApiCache.class, client.cache());
        }


        @Test
        void cacheValidatorIsPassedToFileCache(@TempDir Path tempDir)
        {
            CacheValidator validator = (_, _, _) -> true;
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://example.com").cacheDir(tempDir).cacheValidator(validator)
                    .build();
            assertInstanceOf(net.cumba.web.api.cache.FileApiCache.class, client.cache());
        }
    }

    // --- GET requests ---


    @Nested
    class GetRequests
    {

        @Test
        void getReturnsApiResource() throws IOException
        {
            JsonApiClient client = JsonApiClient.builder()
                    .transport(stubTransport(200, "{\"name\":\"test\"}"))
                    .baseUrl("https://api.example.com").build();

            ApiResource resource = client.get("/items/1");
            assertEquals(Optional.of("test"), resource.getString("name"));
        }


        @Test
        void getWithTypedResourceReturnsProxy() throws IOException
        {
            JsonApiClient client = JsonApiClient.builder()
                    .transport(stubTransport(200, "{\"name\":\"typed\"}"))
                    .baseUrl("https://api.example.com").build();

            ApiResource resource = client.get("/items/1", ApiResource.class);
            assertEquals(Optional.of("typed"), resource.getString("name"));
        }


        @Test
        void getRawJsonReturnsJsonNode() throws IOException
        {
            JsonApiClient client = JsonApiClient.builder()
                    .transport(stubTransport(200, "{\"key\":\"value\"}"))
                    .baseUrl("https://api.example.com").build();

            JsonNode node = client.getRawJson("/data");
            assertEquals("value", node.get("key").asText());
        }


        @Test
        void getThrowsApiExceptionForNon2xx()
        {
            JsonApiClient client = JsonApiClient.builder()
                    .transport(stubTransport(404, "Not Found")).baseUrl("https://api.example.com")
                    .build();

            ApiException ex = assertThrows(ApiException.class, () -> client.get("/missing"));
            assertEquals(404, ex.statusCode());
            assertEquals("Not Found", ex.responseBody());
        }


        @Test
        void getThrowsApiExceptionFor500()
        {
            JsonApiClient client = JsonApiClient.builder()
                    .transport(stubTransport(500, "Internal Error"))
                    .baseUrl("https://api.example.com").build();

            ApiException ex = assertThrows(ApiException.class, () -> client.get("/error"));
            assertEquals(500, ex.statusCode());
            assertTrue(ex.isServerError());
        }


        @Test
        void pathWithoutLeadingSlashGetsNormalized() throws IOException
        {
            CapturingTransport transport = new CapturingTransport(200, "{\"ok\":true}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://api.example.com").build();

            client.get("items/1");
            assertEquals(URI.create("https://api.example.com/items/1"),
                    transport.lastRequest.uri());
        }


        @Test
        void defaultHeadersAreApplied() throws IOException
        {
            CapturingTransport transport = new CapturingTransport(200, "{}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://api.example.com")
                    .defaultHeader("Authorization", "Bearer token123")
                    .defaultHeader("Accept", "application/json").build();

            client.get("/test");
            assertEquals("Bearer token123", transport.lastRequest.header("Authorization"));
            assertEquals("application/json", transport.lastRequest.header("Accept"));
        }
    }

    // --- Caching ---


    @Nested
    class Caching
    {

        @Test
        void responseIsCached(@TempDir Path tempDir) throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "{\"cached\":true}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://api.example.com").cacheDir(tempDir).build();

            // First call goes to network
            JsonNode first = client.getRawJson("/data/1");
            assertEquals(1, transport.callCount);
            assertTrue(first.get("cached").asBoolean());

            // Second call served from cache
            JsonNode second = client.getRawJson("/data/1");
            assertEquals(1, transport.callCount);
            assertTrue(second.get("cached").asBoolean());
        }


        @Test
        void invalidateCacheRemovesCachedFile(@TempDir Path tempDir) throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "{\"v\":1}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir).build();

            client.getRawJson("/item");
            assertEquals(1, transport.callCount);

            assertTrue(client.invalidateCache("/item"));

            client.getRawJson("/item");
            assertEquals(2, transport.callCount);
        }


        /**
         * The limitation {@code AbstractApiClient.invalidateCache} now documents: the argument is a
         * cache key, and since the query participates in the key, the bare path does not name the
         * entry a query-bearing request produced.
         */
        @Test
        void invalidateCacheByBarePathLeavesTheQueryBearingEntryInPlace(@TempDir Path tempDir)
            throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "{\"v\":1}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir).build();

            client.getRawJson("/item?expand=true");
            assertEquals(1, transport.callCount);

            // The endpoint path alone matches no stored key — nothing is removed …
            assertFalse(client.invalidateCache("/item"),
                    "the bare path is a different cache key from the query-bearing one");
            client.getRawJson("/item?expand=true");
            assertEquals(1, transport.callCount, "the entry must still be served from cache");

            // … and the full key, query included, is what reaches it.
            assertTrue(client.invalidateCache("/item?expand=true"));
            client.getRawJson("/item?expand=true");
            assertEquals(2, transport.callCount);
        }


        @Test
        void invalidateCacheReturnsFalseForMissingEntry(@TempDir Path tempDir) throws IOException
        {
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://example.com").cacheDir(tempDir).build();

            assertFalse(client.invalidateCache("/nonexistent"));
        }


        @Test
        void invalidateCacheReturnsFalseWithoutCacheDir() throws IOException
        {
            JsonApiClient client = JsonApiClient.builder().transport(stubTransport(200, "{}"))
                    .baseUrl("https://example.com").build();

            assertFalse(client.invalidateCache("/any"));
        }


        @Test
        void noCachingWithoutCacheDir() throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "{\"v\":1}");
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").build();

            client.getRawJson("/item");
            client.getRawJson("/item");
            assertEquals(2, transport.callCount);
        }


        @Test
        void cacheValidatorRejectsExpiredEntry(@TempDir Path tempDir) throws IOException
        {
            // Flippable validator stands in for TtlCacheValidator: tests the client contract
            // "rejected cache entry → second call hits the transport again" without depending
            // on wall-clock TTL expiry.
            CountingTransport transport = new CountingTransport(200, "{\"v\":1}");
            AtomicBoolean valid = new AtomicBoolean(true);
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir)
                    .cacheValidator((_, _, _) -> valid.get()).build();

            client.getRawJson("/item");
            assertEquals(1, transport.callCount);

            valid.set(false);

            // Expired — should fetch from network again
            client.getRawJson("/item");
            assertEquals(2, transport.callCount);
        }


        @Test
        void cacheValidatorCallbackRejectsEntry(@TempDir Path tempDir) throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "{\"v\":1}");
            CacheValidator rejectAll = (_, _, _) -> false;
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir).cacheValidator(rejectAll)
                    .build();

            client.getRawJson("/item");
            assertEquals(1, transport.callCount);

            // Callback rejects — should fetch again
            client.getRawJson("/item");
            assertEquals(2, transport.callCount);
        }


        @Test
        void cacheValidatorAcceptsEntry(@TempDir Path tempDir) throws IOException
        {
            CountingTransport transport = new CountingTransport(200, "{\"v\":1}");
            CacheValidator acceptAll = (_, _, _) -> true;
            JsonApiClient client = JsonApiClient.builder().transport(transport)
                    .baseUrl("https://example.com").cacheDir(tempDir).cacheValidator(acceptAll)
                    .build();

            client.getRawJson("/item");
            assertEquals(1, transport.callCount);

            // Callback accepts — served from cache
            client.getRawJson("/item");
            assertEquals(1, transport.callCount);
        }
    }

    // --- toCacheFileName ---


    @Nested
    class CacheFileName
    {

        @Test
        void stripsLeadingSlash()
        {
            String name = new net.cumba.web.api.cache.FileApiCache(Path.of("."), ".json")
                    .toCacheFileName("/api/data");
            assertFalse(name.startsWith("/"));
        }


        @Test
        void replacesSlashesWithUnderscores()
        {
            String name = new net.cumba.web.api.cache.FileApiCache(Path.of("."), ".json")
                    .toCacheFileName("/api/v1/data");
            assertTrue(name.startsWith("api_v1_data"));
        }


        @Test
        void appendsJsonExtension()
        {
            String name = new net.cumba.web.api.cache.FileApiCache(Path.of("."), ".json")
                    .toCacheFileName("/test");
            assertTrue(name.endsWith(".json"));
        }


        @Test
        void urlEncodesSpecialCharacters()
        {
            String name = new net.cumba.web.api.cache.FileApiCache(Path.of("."), ".json")
                    .toCacheFileName("/api/data?q=hello world");
            assertTrue(name.endsWith(".json"));
            // The '?' and space should be encoded
            assertFalse(name.contains("?"));
            assertFalse(name.contains(" "));
        }


        @Test
        void handlesPathWithoutLeadingSlash()
        {
            String name = new net.cumba.web.api.cache.FileApiCache(Path.of("."), ".json")
                    .toCacheFileName("data");
            assertTrue(name.startsWith("data"));
            assertTrue(name.endsWith(".json"));
        }
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

}
