package net.cumba.web.api.http;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HttpRequestTest
{

    private static final URI TEST_URI = URI.create("https://api.example.com/test");

    // --- Factory methods ---

    @Test
    void newBuilderSetsUri()
    {
        HttpRequest request = HttpRequest.newBuilder(TEST_URI).build();
        assertEquals(TEST_URI, request.uri());
    }


    @Test
    void newBuilderDefaultsToGet()
    {
        HttpRequest request = HttpRequest.newBuilder(TEST_URI).build();
        assertEquals(HttpMethod.GET, request.method());
    }


    @Test
    void getFactoryWithUri()
    {
        HttpRequest request = HttpRequest.get(TEST_URI).build();
        assertEquals(HttpMethod.GET, request.method());
        assertEquals(TEST_URI, request.uri());
    }


    @Test
    void getFactoryWithString()
    {
        HttpRequest request = HttpRequest.get("https://example.com").build();
        assertEquals(URI.create("https://example.com"), request.uri());
        assertEquals(HttpMethod.GET, request.method());
    }


    @Test
    void builderRejectsNullUri()
    {
        assertThrows(NullPointerException.class, () -> HttpRequest.newBuilder(null).build());
    }

    // --- Method ---


    @Test
    void setMethodToPost()
    {
        HttpRequest request = HttpRequest.newBuilder(TEST_URI).method(HttpMethod.POST).build();
        assertEquals(HttpMethod.POST, request.method());
    }


    @Test
    void setMethodToDelete()
    {
        HttpRequest request = HttpRequest.newBuilder(TEST_URI).method(HttpMethod.DELETE).build();
        assertEquals(HttpMethod.DELETE, request.method());
    }

    // --- Headers ---

    @Nested
    class Headers
    {

        @Test
        void headerAddsValue()
        {
            HttpRequest request = HttpRequest.get(TEST_URI).header("Accept", "application/json")
                    .build();
            assertEquals("application/json", request.header("Accept"));
        }


        @Test
        void headerAccumulatesMultipleValues()
        {
            HttpRequest request = HttpRequest.get(TEST_URI).header("X-Custom", "val1")
                    .header("X-Custom", "val2").build();
            List<String> values = request.headers().get("X-Custom");
            assertEquals(List.of("val1", "val2"), values);
        }


        @Test
        void headerReturnsFirstValueOnly()
        {
            HttpRequest request = HttpRequest.get(TEST_URI).header("X-Multi", "first")
                    .header("X-Multi", "second").build();
            assertEquals("first", request.header("X-Multi"));
        }


        @Test
        void headerReturnsNullForMissingHeader()
        {
            HttpRequest request = HttpRequest.get(TEST_URI).build();
            assertNull(request.header("X-Missing"));
        }


        @Test
        void setHeaderReplacesExistingValues()
        {
            HttpRequest request = HttpRequest.get(TEST_URI).header("X-Key", "old")
                    .setHeader("X-Key", "new").build();
            assertEquals(List.of("new"), request.headers().get("X-Key"));
        }


        @Test
        void headersAreUnmodifiable()
        {
            HttpRequest request = HttpRequest.get(TEST_URI).header("X-Key", "val").build();
            Map<String, List<String>> headers = request.headers();
            assertThrows(UnsupportedOperationException.class,
                    () -> headers.put("New", List.of("v")));
        }


        @Test
        void headerValuesAreUnmodifiable()
        {
            HttpRequest request = HttpRequest.get(TEST_URI).header("X-Key", "val").build();
            List<String> values = request.headers().get("X-Key");
            assertThrows(UnsupportedOperationException.class, () -> values.add("hack"));
        }


        @Test
        void emptyHeadersMap()
        {
            HttpRequest request = HttpRequest.get(TEST_URI).build();
            assertTrue(request.headers().isEmpty());
        }


        @Test
        void headersAreDeepCopied()
        {
            HttpRequest.Builder builder = HttpRequest.get(TEST_URI).header("X-Key", "original");
            HttpRequest request = builder.build();

            // Mutating the builder after build should not affect the request
            builder.header("X-Key", "added-after");
            assertEquals(List.of("original"), request.headers().get("X-Key"));
        }
    }

    // --- Body ---

    @Test
    void bodyDefaultsToNull()
    {
        HttpRequest request = HttpRequest.get(TEST_URI).build();
        assertNull(request.body());
    }


    @Test
    void bodyCanBeSet()
    {
        InputStream body = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(TEST_URI).method(HttpMethod.POST).body(body)
                .build();
        assertSame(body, request.body());
    }

    // --- toString ---


    @Test
    void toStringIncludesMethodAndUri()
    {
        HttpRequest request = HttpRequest.get(TEST_URI).build();
        assertEquals("GET https://api.example.com/test", request.toString());
    }


    @Test
    void toStringReflectsMethod()
    {
        HttpRequest request = HttpRequest.newBuilder(TEST_URI).method(HttpMethod.POST).build();
        assertTrue(request.toString().startsWith("POST "));
    }
}
