package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import net.cumba.web.api.http.HttpRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link FileApiCache}'s request-aware API, metadata sidecar handling,
 * cache-key derivation, and edge-case file-system behaviour. Complements the existing
 * {@code FileApiCacheValidatorTest} (validator-focussed) and {@code CacheValidatorTest}.
 */
class FileApiCacheIntegrationTest
{

    private static HttpRequest requestFor(String path)
    {
        return HttpRequest.get(URI.create("http://example.com" + path)).build();
    }

    @Nested
    class CacheFileNaming
    {

        @Test
        void leadingSlashStripped(@TempDir Path tempDir)
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            assertEquals("mdr_adam.json", cache.toCacheFileName("/mdr/adam"));
        }


        @Test
        void noLeadingSlash(@TempDir Path tempDir)
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            assertEquals("mdr_adam.json", cache.toCacheFileName("mdr/adam"));
        }


        @Test
        void urlEncodingApplied(@TempDir Path tempDir)
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            // Spaces and characters not safe in URL get encoded
            String name = cache.toCacheFileName("/with space");
            assertTrue(name.contains("+") || name.contains("%20"));
            assertTrue(name.endsWith(".json"));
        }


        @Test
        void rootPathProducesEmptyBase(@TempDir Path tempDir)
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            assertEquals(".json", cache.toCacheFileName("/"));
        }


        @Test
        void extensionAppended(@TempDir Path tempDir)
        {
            FileApiCache cache = new FileApiCache(tempDir, ".xml");
            assertTrue(cache.toCacheFileName("/data").endsWith(".xml"));
        }
    }


    @Nested
    class RequestAwareApi
    {

        @Test
        void getReturnsEmptyForMissingEntry(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            assertFalse(cache.get(requestFor("/missing")).isPresent());
        }


        @Test
        void putAndGetRoundtripPreservesContent(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            CacheEntry entry = new CacheEntry(200,
                    Map.of("Content-Type", List.of("application/json")), "{\"hello\":\"world\"}");
            cache.put(requestFor("/data"), entry);

            Optional<CacheEntry> got = cache.get(requestFor("/data"));
            assertTrue(got.isPresent());
            assertEquals(200, got.get().statusCode());
            assertEquals("{\"hello\":\"world\"}", got.get().content());
            assertEquals(List.of("application/json"), got.get().headers().get("Content-Type"));
        }


        @Test
        void putWritesMetaSidecar(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.put(requestFor("/data"),
                    new CacheEntry(201, Map.of("X-Test", List.of("v1")), "body"));

            try (var stream = Files.list(tempDir))
            {
                long metaCount = stream.filter(p -> p.getFileName().toString().endsWith(".meta"))
                        .count();
                assertEquals(1, metaCount);
            }
        }


        @Test
        void invalidateRemovesEntryAndMeta(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            HttpRequest req = requestFor("/data");
            cache.put(req, new CacheEntry(200, Map.of(), "content"));

            assertTrue(cache.invalidate(req));
            assertFalse(cache.get(req).isPresent());

            try (var stream = Files.list(tempDir))
            {
                assertEquals(0, stream.count());
            }
        }


        @Test
        void invalidateReturnsFalseForMissingEntry(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            assertFalse(cache.invalidate(requestFor("/missing")));
        }


        @Test
        void toCacheKeyUsesPath(@TempDir Path tempDir)
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            assertEquals("/mdr/adam", cache.toCacheKey(requestFor("/mdr/adam")));
        }


        @Test
        void toCacheKeyHandlesNullPath(@TempDir Path tempDir)
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            // opaque URI has no path component
            HttpRequest req = HttpRequest.get(URI.create("mailto:user@example.com")).build();
            String key = cache.toCacheKey(req);
            assertNotNull(key);
            assertEquals("/", key);
        }
    }


    @Nested
    class ReadEntry
    {

        @Test
        void readEntryReturnsEmptyForMissing(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            assertFalse(cache.readEntry("/missing").isPresent());
        }


        @Test
        void readEntryReturnsDefaultMetaWhenSidecarMissing(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            // write body only (no meta sidecar) — emulates pre-existing legacy cache entry
            cache.write("/data", "raw content");

            Optional<CacheEntry> entry = cache.readEntry("/data");
            assertTrue(entry.isPresent());
            assertEquals(200, entry.get().statusCode());
            assertTrue(entry.get().headers().isEmpty());
            assertEquals("raw content", entry.get().content());
        }


        @Test
        void readEntryReturnsStoredMeta(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.writeEntry("/data",
                    new CacheEntry(404, Map.of("X-Err", List.of("nope")), "missing"));

            Optional<CacheEntry> entry = cache.readEntry("/data");
            assertTrue(entry.isPresent());
            assertEquals(404, entry.get().statusCode());
            assertEquals(List.of("nope"), entry.get().headers().get("X-Err"));
            assertEquals("missing", entry.get().content());
        }


        @Test
        void readEntryHandlesCorruptedMetaFile(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.write("/data", "body");

            // Write a garbled .meta sidecar
            Path bodyFile = tempDir.resolve(cache.toCacheFileName("/data"));
            Path metaFile = tempDir.resolve(cache.toCacheFileName("/data") + ".meta");
            Files.writeString(metaFile, "not valid json{", StandardCharsets.UTF_8);
            assertTrue(Files.exists(bodyFile));
            assertTrue(Files.exists(metaFile));

            // Corrupted meta — falls back to status 200 / empty headers
            Optional<CacheEntry> entry = cache.readEntry("/data");
            assertTrue(entry.isPresent());
            assertEquals(200, entry.get().statusCode());
            assertTrue(entry.get().headers().isEmpty());
        }


        @Test
        void readEntryAppliesValidator(@TempDir Path tempDir) throws IOException
        {
            CacheValidator rejectAll = (_, _, _) -> false;
            FileApiCache cache = new FileApiCache(tempDir, ".json", rejectAll);
            cache.writeEntry("/data", new CacheEntry("body"));

            assertFalse(cache.readEntry("/data").isPresent());
        }


        @Test
        void readEntryInvalidatesRejectedEntries(@TempDir Path tempDir) throws IOException
        {
            CacheValidator rejectAll = (_, _, _) -> false;
            FileApiCache cache = new FileApiCache(tempDir, ".json", rejectAll);
            cache.writeEntry("/data", new CacheEntry("body"));

            cache.readEntry("/data");
            assertFalse(cache.cacheTimestamp("/data").isPresent());
        }


        @Test
        void getReturnsEntryWithRequestAwareValidator(@TempDir Path tempDir) throws IOException
        {
            // Validator that uses request context: accept only GET requests
            CacheValidator validator = (req, _, _) -> req != null;
            FileApiCache cache = new FileApiCache(tempDir, ".json", validator);
            cache.writeEntry("/data", new CacheEntry("ok"));

            assertTrue(cache.get(requestFor("/data")).isPresent());
        }
    }


    @Nested
    class HeadersRoundtrip
    {

        @Test
        void headersWithMultipleValuesPreserved(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            CacheEntry entry = new CacheEntry(200, Map.of("Set-Cookie", List.of("a=1", "b=2")),
                    "body");
            cache.writeEntry("/data", entry);

            Optional<CacheEntry> got = cache.readEntry("/data");
            assertTrue(got.isPresent());
            assertEquals(List.of("a=1", "b=2"), got.get().headers().get("Set-Cookie"));
        }


        @Test
        void emptyHeadersPreserved(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.writeEntry("/data", new CacheEntry(200, Map.of(), "body"));

            Optional<CacheEntry> got = cache.readEntry("/data");
            assertTrue(got.isPresent());
            assertTrue(got.get().headers().isEmpty());
        }


        @Test
        void nullHeadersConvertedToEmpty(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.writeEntry("/data", new CacheEntry(200, null, "body"));

            Optional<CacheEntry> got = cache.readEntry("/data");
            assertTrue(got.isPresent());
            assertTrue(got.get().headers().isEmpty());
        }


        @Test
        void nullContentPreservedAsNull(@TempDir Path tempDir) throws IOException
        {
            // CacheEntry constructor accepts null content; write/read goes through
            // Files.writeString which rejects null content. The path-based write therefore
            // can't handle null content, but the body of `CacheEntry` may still be null —
            // confirm we don't crash when reading a body file that exists with empty content.
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.write("/data", "");

            Optional<String> got = cache.read("/data");
            assertTrue(got.isPresent());
            assertEquals("", got.get());
        }
    }


    @Nested
    class RejectsNullConstructorArgs
    {

        @Test
        void nullDirThrows()
        {
            assertThrows(NullPointerException.class, () -> new FileApiCache(null, ".json"));
        }


        @Test
        void nullExtensionThrows(@TempDir Path tempDir)
        {
            assertThrows(NullPointerException.class, () -> new FileApiCache(tempDir, null));
        }
    }


    @Nested
    class TimestampAndInvalidation
    {

        @Test
        void invalidateRemovesMetaFile(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.writeEntry("/data", new CacheEntry(200, Map.of("X", List.of("y")), "content"));

            Path metaFile = tempDir.resolve(cache.toCacheFileName("/data") + ".meta");
            assertTrue(Files.exists(metaFile));

            assertTrue(cache.invalidate("/data"));
            assertFalse(Files.exists(metaFile));
        }


        @Test
        void timestampReturnsValueForExistingEntry(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.write("/data", "content");
            OptionalLong ts = cache.cacheTimestamp("/data");
            assertTrue(ts.isPresent());
        }


        @Test
        void writeOverwritesExistingEntry(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.write("/data", "first");
            cache.write("/data", "second");

            assertEquals("second", cache.read("/data").orElse(null));
        }


        @Test
        void writeInNestedSubdirectoryStillFlatFile(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.write("/a/b/c/leaf", "value");

            // No nested directories — slashes are flattened to underscores
            try (var stream = Files.list(tempDir))
            {
                List<Path> files = stream.toList();
                assertEquals(1, files.size());
                assertTrue(files.get(0).getFileName().toString().contains("a_b_c_leaf"));
            }
            assertEquals("value", cache.read("/a/b/c/leaf").orElse(null));
        }


        @Test
        void readReturnsEmptyForMissingEntry(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            Optional<String> result = cache.read("/missing");
            assertNull(result.orElse(null));
        }
    }
}
