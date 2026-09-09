package net.cumba.web.api.cache;

import java.nio.charset.StandardCharsets;

/**
 * UTF-8 conversion helpers for the cache tests.
 *
 * <p>
 * The cache API deals in {@code byte[]} (see {@link CacheEntry}); the fixtures and assertions in
 * these tests are naturally written as text. These two methods are the only place that bridge is
 * made, and both name the charset explicitly — a bare {@code String.getBytes()} or
 * {@code new String(byte[])} would use the platform default and make the tests pass or fail
 * depending on the machine.
 * </p>
 */
final class CacheBytes
{

    private CacheBytes()
    {
        // utility
    }


    /**
     * Encodes text as UTF-8 bytes.
     *
     * @param aText
     *            the text.
     * @return its UTF-8 encoding.
     */
    static byte[] bytes(String aText)
    {
        return aText.getBytes(StandardCharsets.UTF_8);
    }


    /**
     * Decodes UTF-8 bytes as text.
     *
     * @param aBytes
     *            the bytes.
     * @return the decoded text.
     */
    static String text(byte[] aBytes)
    {
        return new String(aBytes, StandardCharsets.UTF_8);
    }
}
