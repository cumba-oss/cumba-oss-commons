package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import net.cumba.web.api.http.HttpRequest;
import org.junit.jupiter.api.Test;

class CacheValidatorTest
{

    @Test
    void lambdaCanBeUsedAsCacheValidator()
    {
        CacheValidator validator = (request, _, _) -> request.uri().getPath().startsWith("/stable");

        CacheEntry entry = new CacheEntry("content");

        assertTrue(validator.isValid(
                HttpRequest.get(URI.create("https://example.com/stable/data")).build(), entry,
                System.currentTimeMillis()));
        assertFalse(validator.isValid(
                HttpRequest.get(URI.create("https://example.com/volatile/data")).build(), entry,
                System.currentTimeMillis()));
    }


    @Test
    void methodReferenceCanBeUsedAsCacheValidator()
    {
        CacheValidator validator = CacheValidatorTest::alwaysValid;
        assertTrue(validator.isValid(HttpRequest.get(URI.create("https://example.com/any")).build(),
                new CacheEntry("content"), 0L));
    }


    @Test
    void callbackReceivesRequestEntryAndTimestamp()
    {
        long[] capturedTimestamp = new long[1];
        HttpRequest[] capturedRequest = new HttpRequest[1];
        CacheEntry[] capturedEntry = new CacheEntry[1];

        CacheValidator validator = (request, entry, timestamp) ->
        {
            capturedRequest[0] = request;
            capturedEntry[0] = entry;
            capturedTimestamp[0] = timestamp;
            return true;
        };

        HttpRequest request = HttpRequest.get(URI.create("https://example.com/test/path")).build();
        CacheEntry entry = new CacheEntry(200, java.util.Map.of(), "body");

        assertTrue(validator.isValid(request, entry, 42L));
        assertSame(request, capturedRequest[0]);
        assertSame(entry, capturedEntry[0]);
        assertEquals(42L, capturedTimestamp[0]);
    }


    private static boolean alwaysValid(HttpRequest aRequest, CacheEntry aEntry, long aTimestamp)
    {
        return true;
    }
}
