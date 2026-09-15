package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A gzip cache file that is not gzip must fail <b>loudly</b>.
 *
 * <p>
 * This is the shape that matters here: the alternative — catching the error and answering an empty
 * or absent entry — would send the caller to the network silently on every call, or worse, hand
 * back an empty body that reads as a valid empty response. It also drives the only path on which
 * {@code openCacheFileStream} has a file handle open when the GZIP header check throws.
 * </p>
 */
class GzipCacheCorruptionTest
{

    /** Exposes the protected path helper so the test can plant a corrupt file. */
    private static final class Probe extends GzipFileApiCache
    {

        Probe(Path aDir)
        {
            super(aDir, ".json", null);
        }


        Path bodyOf(String aPath)
        {
            return cacheDir().resolve(toCacheFileName(aPath));
        }


        InputStream openRaw(Path aFile) throws IOException
        {
            return openCacheFileStream(aFile);
        }
    }

    @Test
    void aCacheFileThatIsNotGzipFailsRatherThanReadingAsEmpty(@TempDir Path aDir) throws IOException
    {
        Probe cache = new Probe(aDir);
        Path body = cache.bodyOf("/p");
        Files.createDirectories(body.getParent());
        Files.write(body, "this is not gzip".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> cache.openRaw(body),
                "the GZIP header check must surface, not degrade to an empty body");
        assertThrows(IOException.class, () -> cache.read("/p"));
    }


    @Test
    void theStreamingReadOfACorruptEntryAlsoFails(@TempDir Path aDir) throws IOException
    {
        // A validator that does not need the entry content is what selects the streaming path.
        CacheValidator streaming = new CacheValidator()
        {

            @Override
            public boolean isValid(HttpRequest aRequest, CacheEntry aEntry, long aTimestampMs)
            {
                return true;
            }


            @Override
            public boolean needsContent()
            {
                return false;
            }
        };
        GzipFileApiCache cache = new GzipFileApiCache(aDir, ".json", streaming);
        Path body = aDir.resolve(cache.toCacheFileName("/p"));
        Files.createDirectories(body.getParent());
        Files.write(body, "still not gzip".getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.get("https://example.org/p").build();
        assertThrows(IOException.class, () -> cache.openStream(request));
    }


    @Test
    void aWellFormedEntryStillRoundTripsThroughGzip(@TempDir Path aDir) throws IOException
    {
        GzipFileApiCache cache = new GzipFileApiCache(aDir, ".json", null);
        cache.writeEntry("/p",
                new CacheEntry(200, Map.of(), "payload".getBytes(StandardCharsets.UTF_8)));

        Optional<byte[]> read = cache.read("/p");
        assertTrue(read.isPresent());
        assertEquals("payload", new String(read.orElseThrow(), StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.get("https://example.org/p").build();
        try (HttpResponse response = cache.openStream(request).orElseThrow())
        {
            assertEquals("payload",
                    new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
