package net.cumba.web.api.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
class HttpResponseTest
{

    // --- Status code ---

    @Test
    void statusCodeIsStored()
    {
        HttpResponse response = new HttpResponse(200, null, null);
        assertEquals(200, response.statusCode());
    }

    // --- isSuccess ---

    @Nested
    class IsSuccess
    {

        @Test
        void trueFor200()
        {
            assertTrue(new HttpResponse(200, null, null).isSuccess());
        }


        @Test
        void trueFor201()
        {
            assertTrue(new HttpResponse(201, null, null).isSuccess());
        }


        @Test
        void trueFor299()
        {
            assertTrue(new HttpResponse(299, null, null).isSuccess());
        }


        @Test
        void falseFor199()
        {
            assertFalse(new HttpResponse(199, null, null).isSuccess());
        }


        @Test
        void falseFor300()
        {
            assertFalse(new HttpResponse(300, null, null).isSuccess());
        }


        @Test
        void falseFor404()
        {
            assertFalse(new HttpResponse(404, null, null).isSuccess());
        }


        @Test
        void falseFor500()
        {
            assertFalse(new HttpResponse(500, null, null).isSuccess());
        }
    }

    // --- Headers ---

    @Test
    void headersAreStored()
    {
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        HttpResponse response = new HttpResponse(200, headers, null);
        assertEquals(List.of("application/json"), response.headers().get("Content-Type"));
    }


    @Test
    void headersAreUnmodifiable()
    {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Key", List.of("val"));
        HttpResponse response = new HttpResponse(200, headers, null);
        assertThrows(UnsupportedOperationException.class,
                () -> response.headers().put("New", List.of("v")));
    }


    @Test
    void nullHeadersBecomesEmptyMap()
    {
        HttpResponse response = new HttpResponse(200, null, null);
        assertNotNull(response.headers());
        assertTrue(response.headers().isEmpty());
    }


    @Test
    void headerReturnsFirstValue()
    {
        Map<String, List<String>> headers = Map.of("X-Multi", List.of("first", "second"));
        HttpResponse response = new HttpResponse(200, headers, null);
        assertEquals("first", response.header("X-Multi"));
    }


    @Test
    void headerReturnsNullForMissingHeader()
    {
        HttpResponse response = new HttpResponse(200, null, null);
        assertNull(response.header("X-Missing"));
    }


    @Test
    void headerReturnsNullForEmptyValueList()
    {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Empty", List.of());
        HttpResponse response = new HttpResponse(200, headers, null);
        assertNull(response.header("X-Empty"));
    }


    @Test
    void headerLookupIsCaseInsensitive()
    {
        Map<String, List<String>> headers = Map.of("Content-Encoding", List.of("gzip"));
        HttpResponse response = new HttpResponse(200, headers, null);
        assertEquals("gzip", response.header("content-encoding"));
        assertEquals("gzip", response.header("CONTENT-ENCODING"));
        assertEquals("gzip", response.header("Content-Encoding"));
    }


    @Test
    void headersMapGetIsCaseInsensitive()
    {
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        HttpResponse response = new HttpResponse(200, headers, null);
        assertEquals(List.of("application/json"), response.headers().get("content-type"));
    }

    // --- Body ---


    @Test
    void bodyIsStored()
    {
        InputStream body = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
        HttpResponse response = new HttpResponse(200, null, body);
        assertSame(body, response.body());
    }


    @Test
    void bodyCanBeNull()
    {
        HttpResponse response = new HttpResponse(204, null, null);
        assertNull(response.body());
    }

    // --- AutoCloseable ---


    @Test
    void closeClosesBody() throws IOException
    {
        CloseTrackingStream body = new CloseTrackingStream();
        HttpResponse response = new HttpResponse(200, null, body);
        assertFalse(body.closed);
        response.close();
        assertTrue(body.closed);
    }


    @Test
    void closeWithNullBodyDoesNotThrow()
    {
        HttpResponse response = new HttpResponse(200, null, null);
        assertDoesNotThrow(response::close);
    }


    @Test
    void tryWithResourcesClosesBody() throws IOException
    {
        CloseTrackingStream body = new CloseTrackingStream();
        try (var _ = new HttpResponse(200, null, body))
        {
            assertFalse(body.closed);
        }
        assertTrue(body.closed);
    }

    // --- toString ---


    @Test
    void toStringIncludesStatusCode()
    {
        HttpResponse response = new HttpResponse(404, null, null);
        assertEquals("HttpResponse[status=404]", response.toString());
    }

    // --- Helper ---

    private static class CloseTrackingStream extends InputStream
    {

        boolean closed = false;

        @Override
        public int read()
        {
            return -1;
        }


        @Override
        public void close() throws IOException
        {
            closed = true;
            super.close();
        }
    }
}
