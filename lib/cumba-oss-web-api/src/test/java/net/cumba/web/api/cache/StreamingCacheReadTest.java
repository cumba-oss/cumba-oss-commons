package net.cumba.web.api.cache;

import static net.cumba.web.api.cache.CacheBytes.bytes;
import static net.cumba.web.api.cache.CacheBytes.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import net.cumba.web.api.json.JsonApiClient;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the low-memory cache-read path (F-web-api-01).
 *
 * <p>
 * These tests are deliberately about <b>which path runs</b>, not only about the answer being right.
 * A cache hit served through {@link CacheEntry} costs a full-size UTF-16 {@code String} plus a
 * full-size re-encoded {@code byte[]} for bytes that are already on disk in the form the parser
 * wants; the streaming path costs neither. Asserting only that the JSON still parses would leave
 * the change free to regress silently back to the buffered route, which is the whole point of it.
 * {@link CountingGzipCache#readCacheFile(Path)} is the single funnel every buffered read passes
 * through, so a count of zero is proof the entry was never materialised.
 * </p>
 */
class StreamingCacheReadTest
{

    private static final String BODY = "{\"name\":\"alpha\",\"n\":7}";

    // --- CacheValidator.needsContent() ---

    @Test
    void ttlValidatorDeclaresThatItDoesNotNeedTheContent()
    {
        assertFalse(new TtlCacheValidator(60_000L).needsContent(),
                "TtlCacheValidator reads only the timestamp, so it must not force the body to be "
                        + "materialised");
    }


    @Test
    void anUnspecifiedValidatorIsAssumedToNeedTheContent()
    {
        CacheValidator lambda = (_, _, _) -> true;
        assertTrue(lambda.needsContent(),
                "the default must be conservative: a validator that does inspect the body and "
                        + "said otherwise would silently decide on an empty string");
    }

    // --- The streaming read itself ---


    @Test
    void cacheHitWithoutValidatorNeverMaterialisesTheEntry(@TempDir Path aDir) throws IOException
    {
        CountingGzipCache cache = new CountingGzipCache(aDir, null);
        CountingTransport transport = new CountingTransport();
        JsonApiClient client = clientWith(cache, transport);

        client.getRawJson("/item");
        assertEquals(1, transport.calls, "first call must reach the network");
        cache.bufferedReads = 0;

        JsonNode node = client.getRawJson("/item");

        assertEquals(1, transport.calls, "second call must be served from cache");
        assertEquals("alpha", node.get("name").asText());
        assertEquals(0, cache.bufferedReads,
                "the cache hit must be streamed, not read into a CacheEntry");
    }


    @Test
    void cacheHitWithTtlValidatorNeverMaterialisesTheEntry(@TempDir Path aDir) throws IOException
    {
        CountingGzipCache cache = new CountingGzipCache(aDir, new TtlCacheValidator(600_000L));
        CountingTransport transport = new CountingTransport();
        JsonApiClient client = clientWith(cache, transport);

        client.getRawJson("/item");
        cache.bufferedReads = 0;

        JsonNode node = client.getRawJson("/item");

        assertEquals(1, transport.calls, "second call must be served from cache");
        assertEquals(7, node.get("n").asInt());
        assertEquals(0, cache.bufferedReads,
                "a validator that needs no content must not cost a full-size materialisation");
    }


    @Test
    void cacheHitWithContentInspectingValidatorStillUsesTheBufferedPath(@TempDir Path aDir)
        throws IOException
    {
        CacheValidator contentBased = (_, aEntry, _) -> !text(aEntry.content()).contains("expired");
        CountingGzipCache cache = new CountingGzipCache(aDir, contentBased);
        CountingTransport transport = new CountingTransport();
        JsonApiClient client = clientWith(cache, transport);

        client.getRawJson("/item");
        cache.bufferedReads = 0;

        JsonNode node = client.getRawJson("/item");

        assertEquals(1, transport.calls, "second call must be served from cache");
        assertEquals("alpha", node.get("name").asText());
        assertEquals(1, cache.bufferedReads,
                "a content-inspecting validator must still get the whole body - streaming it "
                        + "would hand it an empty array and silently change its answer");
    }


    @Test
    void streamingHitStillHonoursAnExpiredTtl(@TempDir Path aDir) throws IOException
    {
        CountingGzipCache cache = new CountingGzipCache(aDir, new TtlCacheValidator(1_000L));
        CountingTransport transport = new CountingTransport();
        JsonApiClient client = clientWith(cache, transport);

        client.getRawJson("/item");
        Path cacheFile = aDir.resolve(cache.toCacheFileName("/item"));
        assertTrue(Files.exists(cacheFile));
        Files.setLastModifiedTime(cacheFile,
                FileTime.fromMillis(System.currentTimeMillis() - 60_000L));

        client.getRawJson("/item");

        assertEquals(2, transport.calls,
                "the streaming path must consult the validator, or entries would outlive their TTL");
    }


    @Test
    void streamingHitFallsBackToTheLegacyPathOnlyKey(@TempDir Path aDir) throws IOException
    {
        CountingGzipCache cache = new CountingGzipCache(aDir, null);
        CountingTransport transport = new CountingTransport();
        JsonApiClient client = clientWith(cache, transport);

        cache.writeEntry("/item", new CacheEntry(bytes(BODY)));
        cache.bufferedReads = 0;

        JsonNode node = client.getRawJson("/item?expand=true");

        assertEquals(0, transport.calls, "the legacy path-only entry must still be found");
        assertEquals("alpha", node.get("name").asText());
        assertEquals(0, cache.bufferedReads, "the legacy hit must stream too");
    }


    private static JsonApiClient clientWith(ApiCache aCache, HttpTransport aTransport)
    {
        return JsonApiClient.builder().transport(aTransport).baseUrl("https://example.com")
                .cache(aCache).build();
    }

    /**
     * A {@link GzipFileApiCache} that counts every buffered (whole-body-materialising) read.
     */
    private static final class CountingGzipCache extends GzipFileApiCache
    {

        private int bufferedReads;

        CountingGzipCache(Path aDir, @Nullable CacheValidator aValidator)
        {
            super(aDir, ".json", aValidator);
        }


        @Override
        protected Optional<byte[]> readCacheFile(Path aCacheFile) throws IOException
        {
            bufferedReads++;
            return super.readCacheFile(aCacheFile);
        }
    }


    private static final class CountingTransport implements HttpTransport
    {

        private int calls;

        @Override
        public HttpResponse send(HttpRequest aRequest)
        {
            calls++;
            return new HttpResponse(200, null,
                    new ByteArrayInputStream(BODY.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
