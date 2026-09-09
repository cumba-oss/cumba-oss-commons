package net.cumba.web.api.cache;

import static net.cumba.web.api.cache.CacheBytes.bytes;
import static net.cumba.web.api.cache.CacheBytes.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.zip.GZIPInputStream;
import net.cumba.web.api.http.HttpRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compression roundtrip and naming tests for {@link GzipFileApiCache}.
 */
class GzipFileApiCacheTest
{

    private static HttpRequest requestFor(String path)
    {
        return HttpRequest.get(URI.create("http://example.com" + path)).build();
    }

    @Nested
    class Naming
    {

        @Test
        void extensionGetsGzSuffix(@TempDir Path tempDir)
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            assertTrue(cache.toCacheFileName("/data").endsWith(".json.gz"));
        }


        @Test
        void extensionWithValidatorGetsGzSuffix(@TempDir Path tempDir)
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".xml", null);
            assertTrue(cache.toCacheFileName("/data").endsWith(".xml.gz"));
        }
    }


    @Nested
    class CompressionRoundtrip
    {

        @Test
        void writtenFileIsGzipped(@TempDir Path tempDir) throws IOException
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            String body = "{\"hello\":\"world\"}";
            cache.write("/data", bytes(body));

            Path cacheFile = tempDir.resolve(cache.toCacheFileName("/data"));
            assertTrue(Files.exists(cacheFile));

            // First two bytes of any gzipped stream are the magic 0x1F 0x8B
            byte[] bytes = Files.readAllBytes(cacheFile);
            assertTrue(bytes.length >= 2);
            assertEquals((byte) 0x1F, bytes[0]);
            assertEquals((byte) 0x8B, bytes[1]);
        }


        @Test
        void roundtripSmallString(@TempDir Path tempDir) throws IOException
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.write("/data", bytes("hello"));

            assertEquals("hello", cache.read("/data").map(CacheBytes::text).orElse(null));
        }


        @Test
        void roundtripLargeString(@TempDir Path tempDir) throws IOException
        {
            String repeated = "X".repeat(100_000);
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.write("/big", bytes(repeated));

            Optional<byte[]> result = cache.read("/big");
            assertTrue(result.isPresent());
            assertEquals(repeated, text(result.get()));
        }


        @Test
        void roundtripPreservesUtf8(@TempDir Path tempDir) throws IOException
        {
            // ASCII-only multi-codepoint string to avoid encoding-source ambiguity
            String content = "Cafe Munchen Konig - 1234567890";
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.write("/utf8", bytes(content));

            assertEquals(content, cache.read("/utf8").map(CacheBytes::text).orElse(null));
        }


        @Test
        void roundtripEmptyString(@TempDir Path tempDir) throws IOException
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.write("/empty", bytes(""));

            assertEquals("", cache.read("/empty").map(CacheBytes::text).orElse(null));
        }


        @Test
        void compressedFileIsSmallerForRepeatedContent(@TempDir Path tempDir) throws IOException
        {
            String repeated = "abcdefghij".repeat(1000);
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.write("/repeat", bytes(repeated));

            Path cacheFile = tempDir.resolve(cache.toCacheFileName("/repeat"));
            long compressedSize = Files.size(cacheFile);
            assertTrue(compressedSize < repeated.length(), "Compressed size " + compressedSize
                    + " should be less than uncompressed " + repeated.length());
        }


        @Test
        void rawFileCanBeDecompressedExternally(@TempDir Path tempDir) throws IOException
        {
            String body = "raw decompression test";
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.write("/data", bytes(body));

            Path cacheFile = tempDir.resolve(cache.toCacheFileName("/data"));
            try (var fis = Files.newInputStream(cacheFile); var gis = new GZIPInputStream(fis))
            {
                String decoded = new String(gis.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(body, decoded);
            }
        }
    }


    @Nested
    class EntryRoundtrip
    {

        @Test
        void writeEntryRoundtripIncludingMeta(@TempDir Path tempDir) throws IOException
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            CacheEntry entry = new CacheEntry(201,
                    Map.of("Content-Type", List.of("application/json")), bytes("{\"a\":1}"));
            cache.writeEntry("/data", entry);

            Optional<CacheEntry> got = cache.readEntry("/data");
            assertTrue(got.isPresent());
            assertEquals(201, got.get().statusCode());
            assertEquals("{\"a\":1}", text(got.get().content()));
            assertEquals(List.of("application/json"), got.get().headers().get("Content-Type"));
        }


        @Test
        void putAndGetWithRequestKey(@TempDir Path tempDir) throws IOException
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            HttpRequest req = requestFor("/mdr/adam");
            cache.put(req, new CacheEntry(200, Map.of(), bytes("data")));

            Optional<CacheEntry> got = cache.get(req);
            assertTrue(got.isPresent());
            assertEquals("data", text(got.get().content()));
        }


        @Test
        void invalidateRemovesGzippedEntryAndMeta(@TempDir Path tempDir) throws IOException
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.writeEntry("/data",
                    new CacheEntry(200, Map.of("X", List.of("y")), bytes("body")));
            assertTrue(cache.invalidate("/data"));

            try (var stream = Files.list(tempDir))
            {
                assertEquals(0, stream.count());
            }
        }
    }


    @Nested
    class NullConstructorArgs
    {

        @Test
        void nullDirThrows()
        {
            assertThrows(NullPointerException.class, () -> new GzipFileApiCache(null, ".json"));
        }


        @Test
        void nullExtensionThrows(@TempDir Path tempDir)
        {
            assertThrows(NullPointerException.class, () -> new GzipFileApiCache(tempDir, null));
        }


        @Test
        void nullDirWithValidatorThrows()
        {
            assertThrows(NullPointerException.class,
                    () -> new GzipFileApiCache(null, ".json", null));
        }
    }


    @Nested
    class CompressionWithValidator
    {

        @Test
        void rejectingValidatorPreventsRead(@TempDir Path tempDir) throws IOException
        {
            CacheValidator reject = (_, _, _) -> false;
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json", reject);
            cache.writeEntry("/data", new CacheEntry(bytes("content")));

            assertFalse(cache.readEntry("/data").isPresent());
        }


        @Test
        void acceptingValidatorPreservesContent(@TempDir Path tempDir) throws IOException
        {
            CacheValidator accept = (_, _, _) -> true;
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json", accept);
            cache.writeEntry("/data",
                    new CacheEntry(200, Map.of("X", List.of("v")), bytes("content")));

            Optional<CacheEntry> got = cache.readEntry("/data");
            assertTrue(got.isPresent());
            assertEquals("content", text(got.get().content()));
            assertEquals(List.of("v"), got.get().headers().get("X"));
        }
    }
}
