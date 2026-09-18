package net.cumba.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.cumba.web.api.cache.ApiCache;
import net.cumba.web.api.cache.CacheEntry;
import net.cumba.web.api.dev.MapResource;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boundary and error-path coverage for {@link AbstractApiClient}, {@link ApiException} and
 * {@link Link} — the places where one status code or one path segment decides between a right and a
 * wrong answer.
 */
class ClientAndLinkBoundaryTest
{

    /** A concrete client, so the base class's own paths can be driven directly. */
    private static final class TestClient extends AbstractApiClient
    {

        private TestClient(Builder aBuilder)
        {
            super(aBuilder, ".json");
        }


        HttpResponse call(String aPath, boolean aCacheable) throws IOException
        {
            return execute(newGetRequest(aPath), aCacheable);
        }


        static String readBody(HttpResponse aResponse)
        {
            return readBodySafe(aResponse);
        }

        static final class Builder extends AbstractBuilder<Builder>
        {

            TestClient build()
            {
                return new TestClient(this);
            }
        }
    }


    /** Answers every request with one fixed status and body. */
    private record FixedTransport(int status, String body) implements HttpTransport
    {

        @Override
        public HttpResponse send(HttpRequest aRequest)
        {
            return new HttpResponse(status, Map.of("X", List.of("1")),
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static TestClient client(Path aCacheDir, int aStatus, String aBody)
    {
        return new TestClient.Builder().baseUrl("https://example.org/api/")
                .transport(new FixedTransport(aStatus, aBody)).cacheDir(aCacheDir).build();
    }

    @Nested
    class CacheableStatusRange
    {

        @Test
        void a200IsCached(@TempDir Path aDir) throws IOException
        {
            TestClient c = client(aDir, 200, "kept");
            c.call("/p", true).close();
            assertTrue(c.cache().read("/api/p").isPresent(), "200 is the bottom of the 2xx range");
        }


        @Test
        void a299IsCached(@TempDir Path aDir) throws IOException
        {
            TestClient c = client(aDir, 299, "kept");
            c.call("/p", true).close();
            assertTrue(c.cache().read("/api/p").isPresent());
        }


        @Test
        void a199IsNotCached(@TempDir Path aDir) throws IOException
        {
            TestClient c = client(aDir, 199, "dropped");
            c.call("/p", true).close();
            assertTrue(c.cache().read("/api/p").isEmpty(), "below 2xx is not a success");
        }


        @Test
        void a300IsNotCached(@TempDir Path aDir) throws IOException
        {
            TestClient c = client(aDir, 300, "dropped");
            c.call("/p", true).close();
            assertTrue(c.cache().read("/api/p").isEmpty(),
                    "300 is a redirect, not a body worth keeping");
        }


        @Test
        void anUncacheableCallNeverWritesEvenOnA200(@TempDir Path aDir) throws IOException
        {
            TestClient c = client(aDir, 200, "fresh");
            c.call("/p", false).close();
            assertTrue(c.cache().read("/api/p").isEmpty());
        }


        @Test
        void aCachedBodyIsServedWithoutTheTransport(@TempDir Path aDir) throws IOException
        {
            TestClient c = client(aDir, 200, "first");
            c.call("/p", true).close();
            c.cache().write("/api/p", "from-cache".getBytes(StandardCharsets.UTF_8));
            try (HttpResponse r = c.call("/p", true))
            {
                assertEquals("from-cache",
                        new String(r.body().readAllBytes(), StandardCharsets.UTF_8));
            }
        }


        @Test
        void invalidateCacheAddsTheLeadingSlashAndRemovesTheEntry(@TempDir Path aDir)
            throws IOException
        {
            TestClient c = client(aDir, 200, "kept");
            c.call("/p", true).close();
            assertTrue(c.invalidateCache("api/p"), "a missing leading slash is supplied");
            assertTrue(c.cache().read("/api/p").isEmpty());
            assertFalse(c.invalidateCache("/api/p"), "nothing left to remove");
        }
    }


    @Nested
    class ErrorBodyReading
    {

        @Test
        void anErrorBodyIsReadBackAsText()
        {
            HttpResponse r = new HttpResponse(500, Map.of(),
                    new ByteArrayInputStream("boom".getBytes(StandardCharsets.UTF_8)));
            assertEquals("boom", TestClient.readBody(r));
        }


        @Test
        void aResponseWithNoBodyReadsAsNullRatherThanAsEmptyText()
        {
            assertNull(TestClient.readBody(new HttpResponse(500, Map.of(), null)),
                    "'there was no body' and 'the body was empty' are different answers");
        }


        // [InputStreamSlowMultibyteRead] is suppressed deliberately. The check asks for an
        // int read(byte[], int, int) override on performance grounds; this stream is a test
        // double whose only job is to fail on the first byte, so there is no multi-byte read to
        // be slow. Overriding it would not be behaviour-neutral either: the inherited
        // implementation returns 0 for a zero-length request without calling read(), and an
        // override that threw unconditionally would change that arm of the contract under test.
        @SuppressWarnings("InputStreamSlowMultibyteRead")
        @Test
        void aBodyThatCannotBeReadSaysSoRatherThanLookingEmpty()
        {
            InputStream broken = new InputStream()
            {

                @Override
                public int read() throws IOException
                {
                    throw new IOException("disconnected");
                }
            };
            assertEquals("(unable to read response body)",
                    TestClient.readBody(new HttpResponse(500, Map.of(), broken)));
        }
    }


    @Nested
    class ExceptionMessage
    {

        @Test
        void aBodyOfExactlyTheLimitIsNotMarkedTruncated()
        {
            String body = "x".repeat(200);
            ApiException ex = new ApiException(404, body);
            assertEquals("HTTP 404: " + body, ex.getMessage());
            assertFalse(ex.getMessage().endsWith("..."), "200 characters still fit");
        }


        @Test
        void oneCharacterPastTheLimitIsTruncated()
        {
            ApiException ex = new ApiException(404, "x".repeat(201));
            assertEquals("HTTP 404: " + "x".repeat(200) + "...", ex.getMessage());
        }


        @Test
        void statusRangesAreInclusiveAtBothEnds()
        {
            assertTrue(new ApiException(400, null).isClientError());
            assertTrue(new ApiException(499, null).isClientError());
            assertFalse(new ApiException(399, null).isClientError());
            assertFalse(new ApiException(500, null).isClientError());
            assertTrue(new ApiException(500, null).isServerError());
            assertTrue(new ApiException(599, null).isServerError());
            assertFalse(new ApiException(600, null).isServerError());
        }
    }


    @Nested
    class LinkSegments
    {

        private Link link(String aHref)
        {
            return MapResource.of(Map.of("href", aHref), Link.class);
        }


        @Test
        void theLastSegmentOfARootLevelHrefIsTheId()
        {
            assertEquals("abc", link("/abc").id().orElseThrow(),
                    "the slash is at index 0 and the segment after it is still the id");
        }


        @Test
        void anHrefEndingInASlashHasNoId()
        {
            assertEquals(java.util.Optional.empty(), link("/a/b/").id());
            assertEquals(java.util.Optional.empty(), link("/").id());
        }


        @Test
        void anHrefWithNoSlashAtAllHasNoId()
        {
            assertEquals(java.util.Optional.empty(), link("abc").id());
        }


        @Test
        void anEmptyHrefHasNoIdAtAnyPosition()
        {
            assertEquals(java.util.Optional.empty(), link("").id());
            assertEquals(java.util.Optional.empty(), link("").id(0));
            assertEquals(java.util.Optional.empty(), link("").id(-1));
        }


        @Test
        void aNegativeIndexCountsBackFromTheEndAndReachesTheFirstSegment()
        {
            assertEquals("a", link("a/b").id(-1).orElseThrow(),
                    "index 0 of a relative href is a real segment, not the empty leading one");
            assertEquals("b", link("/a/b/c").id(-1).orElseThrow());
            assertEquals(java.util.Optional.empty(), link("a/b").id(-5), "past the front");
        }


        @Test
        void aPositiveIndexSelectsAnAbsolutePosition()
        {
            assertEquals("mdr", link("/mdr/adam/adam-2-1").id(1).orElseThrow());
            assertEquals("adam", link("/mdr/adam/adam-2-1").id(2).orElseThrow());
            assertEquals(java.util.Optional.empty(), link("/mdr/adam").id(9), "past the end");
            assertEquals(java.util.Optional.empty(), link("/a/b").id(3), "one past the last index");
            assertEquals("b", link("/a/b").id(0).orElseThrow(),
                    "index 0 means the last segment, not the leading empty one");
        }


        @Test
        void indexZeroIsTheSameAsTheId()
        {
            assertEquals(link("/mdr/adam/adam-2-1").id(), link("/mdr/adam/adam-2-1").id(0));
        }


        @Test
        void aLinkWithNoHrefHasNoId()
        {
            Link noHref = MapResource.of(Map.of("title", "t"), Link.class);
            assertEquals(java.util.Optional.empty(), noHref.id());
            assertEquals(java.util.Optional.empty(), noHref.id(1));
        }
    }


    @Nested
    class UrlNormalisation
    {

        @Test
        void aTrailingSlashOnTheBaseUrlIsNotDoubled(@TempDir Path aDir)
        {
            assertEquals("https://example.org/api", client(aDir, 200, "x").baseUrl());
        }


        @Test
        void aBaseUrlWithoutATrailingSlashIsLeftAlone()
        {
            TestClient c = new TestClient.Builder().baseUrl("https://example.org/api")
                    .transport(new FixedTransport(200, "x")).build();
            assertEquals("https://example.org/api", c.baseUrl());
        }


        @Test
        void withoutACacheDirNothingIsStored() throws IOException
        {
            TestClient c = new TestClient.Builder().baseUrl("https://example.org")
                    .transport(new FixedTransport(200, "x")).build();
            c.call("/p", true).close();
            assertTrue(c.cache().read("/p").isEmpty(), "the no-op cache keeps nothing");
            assertFalse(c.invalidateCache("/p"));
        }


        @Test
        void anExplicitCacheWinsOverACacheDirectory(@TempDir Path aDir)
        {
            ApiCache explicit = new ApiCache()
            {

                @Override
                public java.util.Optional<byte[]> read(String aPath)
                {
                    return java.util.Optional.empty();
                }


                @Override
                public void write(String aPath, byte[] aContent)
                {
                    // no-op
                }


                @Override
                public boolean invalidate(String aPath)
                {
                    return false;
                }
            };
            TestClient c = new TestClient.Builder().baseUrl("https://example.org")
                    .transport(new FixedTransport(200, "x")).cacheDir(aDir).cache(explicit).build();
            assertEquals(explicit, c.cache());
        }


        @Test
        void defaultHeadersReachEveryRequest()
        {
            java.util.concurrent.atomic.AtomicReference<HttpRequest> seen = new java.util.concurrent.atomic.AtomicReference<>();
            TestClient c = new TestClient.Builder().baseUrl("https://example.org")
                    .defaultHeader("Accept", "application/json")
                    .defaultHeader("X-Api-Key", "secret").transport(aRequest ->
                    {
                        seen.set(aRequest);
                        return new HttpResponse(200, Map.of(),
                                new ByteArrayInputStream(new byte[0]));
                    }).build();
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> c.call("/p", false).close());
            assertEquals("application/json", seen.get().header("Accept"));
            assertEquals("secret", seen.get().header("X-Api-Key"));
        }


        @Test
        void theTransportIsTheOneThatWasConfigured()
        {
            HttpTransport t = new FixedTransport(200, "x");
            TestClient c = new TestClient.Builder().baseUrl("https://example.org").transport(t)
                    .build();
            assertEquals(t, c.transport());
            assertNotEquals(null, c.cache());
        }
    }


    @Nested
    class BlankBodies
    {

        @Test
        void aWhitespaceOnly2xxIsNotCached(@TempDir Path aDir) throws IOException
        {
            TestClient c = client(aDir, 200, " \t\r\n ");
            c.call("/p", true).close();
            assertTrue(c.cache().read("/api/p").isEmpty(),
                    "a blank body cached once would be served for ever");
        }


        @Test
        void aSingleNonBlankCharacterIsEnoughToCache(@TempDir Path aDir) throws IOException
        {
            TestClient c = client(aDir, 200, " x ");
            c.call("/p", true).close();
            assertTrue(c.cache().read("/api/p").isPresent());
        }


        @Test
        void aCacheEntryRoundTripsThroughTheResponseForm()
        {
            CacheEntry entry = new CacheEntry(201, Map.of("A", List.of("1")),
                    "b".getBytes(StandardCharsets.UTF_8));
            HttpResponse r = entry.toHttpResponse();
            assertEquals(201, r.statusCode());
            assertEquals("1", r.header("a"), "header lookup is case-insensitive");
        }
    }
}
