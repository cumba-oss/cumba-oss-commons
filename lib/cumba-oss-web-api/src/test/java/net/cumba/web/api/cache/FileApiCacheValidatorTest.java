package net.cumba.web.api.cache;

import static net.cumba.web.api.cache.CacheBytes.bytes;
import static net.cumba.web.api.cache.CacheBytes.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileApiCacheValidatorTest
{

    @Nested
    class WithoutValidator
    {

        @Test
        void readReturnsCachedContent(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            cache.write("/data", bytes("content"));

            Optional<byte[]> result = cache.read("/data");
            assertTrue(result.isPresent());
            assertEquals("content", text(result.get()));
        }


        @Test
        void nullValidatorAllowsAllEntries(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json", null);
            cache.write("/data", bytes("content"));

            assertTrue(cache.read("/data").isPresent());
        }
    }


    @Nested
    class WithTtlValidator
    {

        @Test
        void recentEntryIsServed(@TempDir Path tempDir) throws IOException
        {
            // 1 hour TTL — freshly written entry should be valid
            FileApiCache cache = new FileApiCache(tempDir, ".json",
                    new TtlCacheValidator(3_600_000L));
            cache.write("/data", bytes("fresh"));

            Optional<byte[]> result = cache.read("/data");
            assertTrue(result.isPresent());
            assertEquals("fresh", text(result.get()));
        }


        @Test
        void expiredEntryIsInvalidated(@TempDir Path tempDir) throws IOException
        {
            // Flippable validator stands in for TtlCacheValidator. We exercise the cache's
            // contract — "when the validator rejects an entry, read() returns empty" — without
            // depending on wall-clock TTL expiry.
            AtomicBoolean valid = new AtomicBoolean(true);
            FileApiCache cache = new FileApiCache(tempDir, ".json", (_, _, _) -> valid.get());
            cache.write("/data", bytes("old"));
            valid.set(false);

            Optional<byte[]> result = cache.read("/data");
            assertFalse(result.isPresent());
        }


        @Test
        void expiredEntryIsDeletedFromDisk(@TempDir Path tempDir) throws IOException
        {
            AtomicBoolean valid = new AtomicBoolean(true);
            FileApiCache cache = new FileApiCache(tempDir, ".json", (_, _, _) -> valid.get());
            cache.write("/data", bytes("old"));
            valid.set(false);

            // read() invalidates the expired entry
            cache.read("/data");

            // cacheTimestamp should now return empty — file was deleted
            OptionalLong timestamp = cache.cacheTimestamp("/data");
            assertFalse(timestamp.isPresent());
        }
    }


    @Nested
    class WithCallbackValidator
    {

        @Test
        void callbackRejectingEntryReturnsEmpty(@TempDir Path tempDir) throws IOException
        {
            CacheValidator rejectAll = (_, _, _) -> false;
            FileApiCache cache = new FileApiCache(tempDir, ".json", rejectAll);
            cache.write("/data", bytes("content"));

            assertFalse(cache.read("/data").isPresent());
        }


        @Test
        void callbackAcceptingEntryReturnsContent(@TempDir Path tempDir) throws IOException
        {
            CacheValidator acceptAll = (_, _, _) -> true;
            FileApiCache cache = new FileApiCache(tempDir, ".json", acceptAll);
            cache.write("/data", bytes("content"));

            Optional<byte[]> result = cache.read("/data");
            assertTrue(result.isPresent());
            assertEquals("content", text(result.get()));
        }


        @Test
        void callbackReceivesEntryContent(@TempDir Path tempDir) throws IOException
        {
            CacheEntry[] capturedEntry = new CacheEntry[1];
            CacheValidator capturing = (_, entry, _) ->
            {
                capturedEntry[0] = entry;
                return true;
            };

            FileApiCache cache = new FileApiCache(tempDir, ".json", capturing);
            cache.write("/mdr/adam/adam-2-1", bytes("data"));
            cache.read("/mdr/adam/adam-2-1");

            assertEquals("data", text(capturedEntry[0].content()));
        }


        @Test
        void callbackReceivesTimestamp(@TempDir Path tempDir) throws IOException
        {
            long[] capturedTimestamp = new long[1];
            CacheValidator capturing = (_, _, timestamp) ->
            {
                capturedTimestamp[0] = timestamp;
                return true;
            };

            FileApiCache cache = new FileApiCache(tempDir, ".json", capturing);
            long beforeWrite = System.currentTimeMillis();
            cache.write("/data", bytes("content"));
            long afterWrite = System.currentTimeMillis();

            cache.read("/data");

            // filesystem granularity tolerance (e.g., FAT32 has 2s resolution)
            assertTrue(capturedTimestamp[0] >= beforeWrite - 2000);
            assertTrue(capturedTimestamp[0] <= afterWrite + 2000);
        }


        @Test
        void entryContentBasedValidation(@TempDir Path tempDir) throws IOException
        {
            CacheValidator contentBased = (_, entry,
                    _) -> !text(entry.content()).contains("expired");

            FileApiCache cache = new FileApiCache(tempDir, ".json", contentBased);
            cache.write("/stable", bytes("valid data"));
            cache.write("/volatile", bytes("expired data"));

            assertTrue(cache.read("/stable").isPresent());
            assertFalse(cache.read("/volatile").isPresent());
        }
    }


    @Nested
    class CacheTimestamp
    {

        @Test
        void returnsTimestampForExistingEntry(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            long before = System.currentTimeMillis();
            cache.write("/data", bytes("content"));
            long after = System.currentTimeMillis();

            OptionalLong timestamp = cache.cacheTimestamp("/data");
            assertTrue(timestamp.isPresent());
            // filesystem granularity tolerance (e.g., FAT32 has 2s resolution)
            assertTrue(timestamp.getAsLong() >= before - 2000);
            assertTrue(timestamp.getAsLong() <= after + 2000);
        }


        @Test
        void returnsEmptyForMissingEntry(@TempDir Path tempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(tempDir, ".json");
            OptionalLong timestamp = cache.cacheTimestamp("/nonexistent");
            assertFalse(timestamp.isPresent());
        }
    }


    @Nested
    class GzipWithValidator
    {

        @Test
        void gzipCacheRespectsValidator(@TempDir Path tempDir) throws IOException
        {
            CacheValidator rejectAll = (_, _, _) -> false;
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json", rejectAll);
            cache.write("/data", bytes("compressed content"));

            assertFalse(cache.read("/data").isPresent());
        }


        @Test
        void gzipCacheServesValidEntry(@TempDir Path tempDir) throws IOException
        {
            CacheValidator acceptAll = (_, _, _) -> true;
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json", acceptAll);
            cache.write("/data", bytes("compressed content"));

            Optional<byte[]> result = cache.read("/data");
            assertTrue(result.isPresent());
            assertEquals("compressed content", text(result.get()));
        }


        @Test
        void gzipCacheWithoutValidatorServesAll(@TempDir Path tempDir) throws IOException
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.write("/data", bytes("content"));

            assertTrue(cache.read("/data").isPresent());
        }


        @Test
        void gzipCacheTimestampWorks(@TempDir Path tempDir) throws IOException
        {
            GzipFileApiCache cache = new GzipFileApiCache(tempDir, ".json");
            cache.write("/data", bytes("content"));

            OptionalLong timestamp = cache.cacheTimestamp("/data");
            assertTrue(timestamp.isPresent());
        }
    }
}
