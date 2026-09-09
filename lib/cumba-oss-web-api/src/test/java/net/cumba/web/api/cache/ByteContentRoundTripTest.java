package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins what the {@code byte[]} cache API buys over the {@code String} one: content goes in and
 * comes out byte for byte, whatever it is.
 *
 * <p>
 * The previous implementation read cache files with {@code Files.readString(..., UTF_8)} and wrote
 * them with {@code String.getBytes(UTF_8)}. That is lossless only for input that happens to be
 * well-formed UTF-8 — anything else is silently replaced by U+FFFD on the way through, and the
 * bytes that come back are not the bytes that went in. Storage has no business decoding a body it
 * was never told the charset of, so these tests assert the identity, not the decoding.
 * </p>
 */
class ByteContentRoundTripTest
{

    /**
     * Not valid UTF-8: a lone 0xFF, a truncated two-byte sequence, and an unpaired continuation.
     */
    private static final byte[] NOT_UTF8 =
    {
            (byte) 0xFF, (byte) 0xFE, (byte) 0xC3, 0x28, (byte) 0x80, 0x00, 0x41
    };

    private static final String UNICODE_BODY = "{\"n\":\"\u00C4\u00D6\u00DC \u65E5\u672C\u8A9E \u2713 \uD83D\uDE00\"}";

    @Test
    void fileCacheReturnsExactlyTheBytesWritten(@TempDir Path aDir) throws IOException
    {
        FileApiCache cache = new FileApiCache(aDir, ".bin");

        cache.write("/raw", NOT_UTF8);

        assertArrayEquals(NOT_UTF8, cache.read("/raw").orElseThrow(),
                "storage must not decode; a body that is not valid UTF-8 must survive unchanged");
    }


    @Test
    void gzipCacheReturnsExactlyTheBytesWritten(@TempDir Path aDir) throws IOException
    {
        GzipFileApiCache cache = new GzipFileApiCache(aDir, ".bin");

        cache.write("/raw", NOT_UTF8);

        assertArrayEquals(NOT_UTF8, cache.read("/raw").orElseThrow(),
                "compression must not decode either");
    }


    @Test
    void entryRoundTripKeepsMultiByteCharactersIntact(@TempDir Path aDir) throws IOException
    {
        GzipFileApiCache cache = new GzipFileApiCache(aDir, ".json");
        byte[] body = UNICODE_BODY.getBytes(StandardCharsets.UTF_8);

        cache.writeEntry("/u", new CacheEntry(200, Map.of("X", List.of("y")), body));
        CacheEntry got = cache.readEntry("/u").orElseThrow();

        assertArrayEquals(body, got.content());
        assertEquals(UNICODE_BODY, new String(got.content(), StandardCharsets.UTF_8),
                "and it still decodes to the original text when a caller asks for characters");
    }


    /**
     * {@link CacheEntry} is compared by value, body included — the record's generated
     * {@code equals} would compare the array by identity and quietly report two equal entries as
     * different.
     */
    @Test
    void entriesWithEqualBodiesAreEqual()
    {
        byte[] one = "body".getBytes(StandardCharsets.UTF_8);
        byte[] two = "body".getBytes(StandardCharsets.UTF_8);

        assertEquals(new CacheEntry(200, Map.of(), one), new CacheEntry(200, Map.of(), two));
        assertEquals(new CacheEntry(200, Map.of(), one).hashCode(),
                new CacheEntry(200, Map.of(), two).hashCode());
    }


    /**
     * {@code toString} must report the body's size rather than its content: the generated record
     * version prints an array identity hash, and printing the body itself would put a
     * multi-megabyte CT package into a log line.
     */
    @Test
    void toStringReportsTheBodySizeNotTheBody()
    {
        CacheEntry entry = new CacheEntry(200, Map.of(), "abcde".getBytes(StandardCharsets.UTF_8));

        String rendered = entry.toString();

        assertTrue(rendered.contains("5 bytes"), rendered);
        assertFalse(rendered.contains("abcde"), rendered);
    }


    /**
     * The entry shares its array rather than copying it, in both directions — that is the whole
     * point of the type and is stated in its javadoc, so it is pinned rather than left to be
     * "fixed" by a well-meaning defensive copy that would double the peak footprint of every cached
     * CT package.
     */
    @Test
    void theBodyArrayIsSharedNotCopied()
    {
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);

        CacheEntry entry = new CacheEntry(200, Map.of(), body);

        assertSame(body, entry.content(), "content() must hand back the very array passed in");
    }
}
