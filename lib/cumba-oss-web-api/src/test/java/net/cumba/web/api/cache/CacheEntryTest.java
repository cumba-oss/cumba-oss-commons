package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.cumba.web.api.http.HttpResponse;
import org.junit.jupiter.api.Test;

class CacheEntryTest
{

    @Test
    void compactConstructorRejectsNullContent()
    {
        assertThrows(NullPointerException.class, () -> new CacheEntry(200, Map.of(), null));
    }


    @Test
    void convenienceConstructorRejectsNullContent()
    {
        assertThrows(NullPointerException.class, () -> new CacheEntry(null));
    }


    @Test
    void emptyContentIsAccepted()
    {
        CacheEntry entry = new CacheEntry("");
        assertEquals("", entry.content());
        assertEquals(200, entry.statusCode());
    }


    @Test
    void toHttpResponseAlwaysProvidesABodyStream() throws IOException
    {
        CacheEntry entry = new CacheEntry(200, Map.of("X", List.of("y")), "hello");
        try (HttpResponse response = entry.toHttpResponse())
        {
            assertNotNull(response.body());
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("hello", body);
            assertEquals(200, response.statusCode());
        }
    }


    @Test
    void emptyContentRoundTripsToAnEmptyBodyStream() throws IOException
    {
        CacheEntry entry = new CacheEntry("");
        try (HttpResponse response = entry.toHttpResponse())
        {
            assertNotNull(response.body());
            assertTrue(
                    new String(response.body().readAllBytes(), StandardCharsets.UTF_8).isEmpty());
        }
    }
}
