package net.cumba.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import net.cumba.web.api.json.JsonApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the blank-body cache guard now that {@code execute} works in {@code byte[]} rather than
 * {@code String}.
 *
 * <p>
 * The guard is the 2026-09-07 fix for F-web-api-02: a blank 2xx must not be persisted, because
 * {@code getRawJson} throws on one and a cached blank entry turns a single transient empty response
 * into a permanent failure across JVM restarts. It used to read {@code !body.isBlank()}; on bytes
 * it reads {@code !isBlank(body)}, and "blank" must still mean zero-length <b>or</b>
 * whitespace-only. {@code JsonApiClientEdgeCaseTest} covers the zero-length half; the
 * whitespace-only half is what a byte scan could quietly lose, so it is pinned here.
 * </p>
 */
class BlankBodyBytesTest
{

    /**
     * Every ASCII character {@link Character#isWhitespace} recognises, alone and in combination.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "", " ", "\t", "\n", "\r", "\f", "\013", "\034", "\035", "\036", "\037",
            "   \n\t  \r\n "
    })
    void asciiWhitespaceOnlyBodiesAreBlank(String aBody)
    {
        assertTrue(AbstractApiClient.isBlank(aBody.getBytes(StandardCharsets.UTF_8)),
                "body must count as blank: " + aBody.codePoints().boxed().toList());
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "{}", "x", " x ", "\n{\"a\":1}\n", "\u00E9", " \u00E9 ", "\u00A0", "\000"
    })
    void bodiesWithAnyNonWhitespaceByteAreNotBlank(String aBody)
    {
        assertFalse(AbstractApiClient.isBlank(aBody.getBytes(StandardCharsets.UTF_8)),
                "body must not count as blank: " + aBody.codePoints().boxed().toList());
    }


    /**
     * The one deliberate divergence from {@link String#isBlank()}, stated as a test so it cannot
     * drift into an accident: a body made up entirely of non-ASCII Unicode whitespace is blank to
     * {@code String} and non-blank here. Jackson rejects such a body with a parse error rather than
     * mistaking it for an empty document, so it is not the failure the guard exists to stop.
     */
    @Test
    void nonAsciiUnicodeWhitespaceDivergesFromStringIsBlankOnPurpose()
    {
        String lineSeparator = "\u2028";
        assertTrue(lineSeparator.isBlank(), "precondition: String.isBlank() calls this blank");
        assertFalse(AbstractApiClient.isBlank(lineSeparator.getBytes(StandardCharsets.UTF_8)),
                "the byte scan deliberately treats non-ASCII whitespace as content");
    }


    /**
     * A multi-byte UTF-8 character must never be mistaken for whitespace. Every byte of one is
     * {@code >= 0x80}, i.e. negative as a signed {@code byte} — the property the scan relies on.
     */
    @Test
    void multiByteCharactersAreNeverMistakenForWhitespace()
    {
        byte[] japanese = "\u65E5\u672C\u8A9E".getBytes(StandardCharsets.UTF_8);
        for (byte b : japanese)
        {
            assertTrue(b < 0, "expected only lead/continuation bytes, got " + b);
        }
        assertFalse(AbstractApiClient.isBlank(japanese));
    }


    /**
     * The behaviour that matters, end to end: a whitespace-only 2xx must leave no cache file, and
     * the next call must reach the transport rather than be served a poisoned entry.
     */
    @Test
    void whitespaceOnlySuccessBodyIsNotCached(@TempDir Path aTempDir) throws IOException
    {
        CountingTransport transport = new CountingTransport(200, "   \n\t  ");
        JsonApiClient client = JsonApiClient.builder().transport(transport)
                .baseUrl("https://example.com").cacheDir(aTempDir).build();

        assertThrows(ApiException.class, () -> client.getRawJson("/blank"));
        assertEquals(1, transport.calls);

        List<String> cacheFiles;
        try (Stream<Path> files = Files.list(aTempDir))
        {
            cacheFiles = files.map(f -> f.getFileName().toString()).sorted().toList();
        }
        assertEquals(List.of(), cacheFiles, "a whitespace-only 2xx must not leave a cache entry");

        assertThrows(ApiException.class, () -> client.getRawJson("/blank"));
        assertEquals(2, transport.calls, "the second call must reach the transport again");
    }


    /**
     * The negative control for the guard: a body that only <i>starts</i> with whitespace is real
     * content and must still be cached, or the guard would be throwing away good responses.
     */
    @Test
    void leadingWhitespaceDoesNotMakeABodyBlank(@TempDir Path aTempDir) throws IOException
    {
        CountingTransport transport = new CountingTransport(200, "  \n{\"key\":\"value\"}");
        JsonApiClient client = JsonApiClient.builder().transport(transport)
                .baseUrl("https://example.com").cacheDir(aTempDir).build();

        assertEquals("value", client.getRawJson("/data").get("key").asText());
        assertEquals("value", client.getRawJson("/data").get("key").asText());
        assertEquals(1, transport.calls, "the second call must be served from cache");
    }

    private static final class CountingTransport implements HttpTransport
    {

        private final int statusCode;

        private final String body;

        private int calls;

        CountingTransport(int aStatusCode, String aBody)
        {
            statusCode = aStatusCode;
            body = aBody;
        }


        @Override
        public HttpResponse send(HttpRequest aRequest)
        {
            calls++;
            return new HttpResponse(statusCode, null,
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
