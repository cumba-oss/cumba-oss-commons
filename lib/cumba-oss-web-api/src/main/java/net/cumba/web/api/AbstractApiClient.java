package net.cumba.web.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.cumba.web.api.cache.ApiCache;
import net.cumba.web.api.cache.CacheEntry;
import net.cumba.web.api.cache.CacheValidator;
import net.cumba.web.api.cache.FileApiCache;
import net.cumba.web.api.cache.NoOpApiCache;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import org.jspecify.annotations.Nullable;

/**
 * Abstract base class for HTTP API clients that provides transport-format-independent
 * functionality: request execution, response caching, base URL management, and default headers.
 *
 * <p>
 * The core method {@link #execute(HttpRequest, boolean)} sends an HTTP request through the
 * configured {@link HttpTransport} and optionally caches the response via the {@link ApiCache}.
 * When caching is enabled, the cache owns all caching decisions: key derivation
 * ({@link ApiCache#toCacheKey(HttpRequest)}), validation ({@link CacheValidator}), and storage.
 * </p>
 *
 * <p>
 * Subclasses provide format-specific parsing (JSON, XML, etc.) on top of the {@link HttpResponse}
 * returned by {@code execute()}.
 * </p>
 *
 * @see net.cumba.web.api.json.JsonApiClient
 * @see net.cumba.web.api.xml.XmlApiClient
 */
public abstract class AbstractApiClient
{

    private final HttpTransport transport;

    private final String baseUrl;

    private final List<HeaderEntry> defaultHeaders;

    private final ApiCache cache;

    /**
     * Initializes common client state from the builder.
     *
     * @param aBuilder
     *            the builder providing configuration.
     * @param aCacheExtension
     *            the file extension for the default file-based cache (e.g., ".json", ".xml"). Used
     *            only when {@code cacheDir} is set and no custom cache is provided.
     */
    protected AbstractApiClient(AbstractBuilder<?> aBuilder, String aCacheExtension)
    {
        this.transport = Objects.requireNonNull(aBuilder.transport, "transport must not be null");
        this.baseUrl = stripTrailingSlash(
                Objects.requireNonNull(aBuilder.baseUrl, "baseUrl must not be null"));
        this.defaultHeaders = Collections
                .unmodifiableList(new ArrayList<>(aBuilder.defaultHeaders));

        if (aBuilder.cache != null)
        {
            this.cache = aBuilder.cache;
        }
        else if (aBuilder.cacheDir != null)
        {
            this.cache = new FileApiCache(aBuilder.cacheDir, aCacheExtension,
                    aBuilder.cacheValidator);
        }
        else
        {
            this.cache = NoOpApiCache.INSTANCE;
        }
    }

    // --- Core execute ---


    /**
     * Executes an HTTP request, optionally using the cache. This is the central method through
     * which all requests flow. The cache decision is controlled by the caller via the
     * {@code aCacheable} parameter.
     *
     * <p>
     * When {@code aCacheable} is {@code true}:
     * <ul>
     * <li>The cache is consulted first via {@link ApiCache#get(HttpRequest)}. If a valid entry
     * exists, it is returned as an {@link HttpResponse} without making a network call.</li>
     * <li>After a successful network response (2xx), the response is stored via
     * {@link ApiCache#put(HttpRequest, CacheEntry)}.</li>
     * </ul>
     *
     * <p>
     * When {@code aCacheable} is {@code false}, the request always goes to the network and the
     * response is never cached. Use this for mutating operations (PUT, POST, DELETE) or for
     * endpoints that should always be fresh.
     * </p>
     *
     * <p>
     * <b>Closing the returned response is mandatory.</b> On the network path, and on a cache hit
     * served as a {@link CacheEntry}, the body is fully buffered (a {@link ByteArrayInputStream})
     * and the original network stream is already closed — closing costs nothing there. But a cache
     * hit may instead be served by {@link ApiCache#openStream(HttpRequest)}, and that body is a
     * <b>live stream over the cache's storage</b>, holding a file handle until it is closed.
     * Callers must therefore use try-with-resources.
     * </p>
     *
     * <p>
     * A consequence worth stating plainly: the body is <b>not</b> guaranteed to be re-readable.
     * Read it once. That was always the documented shape of {@link HttpResponse}, but the previous
     * wording here promised buffering unconditionally, and code written against that promise —
     * reading the body twice, or reading it after the response is closed — breaks on the streaming
     * path. The streaming path is used only when the configured {@link CacheValidator} does not
     * {@linkplain CacheValidator#needsContent() need the entry content}; otherwise the buffered
     * {@code CacheEntry} path is taken exactly as before.
     * </p>
     *
     * @param aRequest
     *            the HTTP request to execute.
     * @param aCacheable
     *            {@code true} to allow caching, {@code false} to bypass the cache entirely.
     * @return the HTTP response (from cache or network). Must be closed by the caller.
     * @throws IOException
     *             if a network or I/O error occurs.
     */
    protected HttpResponse execute(HttpRequest aRequest, boolean aCacheable) throws IOException
    {
        // Try cache first. Prefer the streaming read: the bytes are already on disk in exactly
        // the form the parser wants, and the CacheEntry route would spend a full-size String and a
        // full-size byte[] re-encoding them for nothing. openStream() declines (empty) on a miss,
        // and whenever the cache's validator needs the entry content - so the buffered path below
        // stays the behaviour for every cache and every validator that has not opted in.
        if (aCacheable)
        {
            Optional<HttpResponse> streamed = cache.openStream(aRequest);
            if (streamed.isPresent())
            {
                return streamed.get();
            }
            Optional<CacheEntry> cached = cache.get(aRequest);
            if (cached.isPresent())
            {
                return cached.get().toHttpResponse();
            }
        }

        // Network call — read body fully and close the transport response
        int statusCode;
        Map<String, List<String>> responseHeaders;
        byte[] body;
        try (HttpResponse response = transport.send(aRequest))
        {
            statusCode = response.statusCode();
            responseHeaders = response.headers();
            // Read body() ONCE into a local. Calling it twice - null-check on the
            // first call, dereference on the second - is what SpotBugs 4.10's
            // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE flags: nothing guarantees the
            // second call returns the same non-null value.
            InputStream rawBody = response.body();
            body = rawBody != null ? rawBody.readAllBytes() : null;
        }

        // Cache successful responses when allowed. A blank body - zero-length or
        // whitespace only - is deliberately NOT cached. It carries no content in any
        // format this class serves, and JsonApiClient.getRawJson now throws
        // "Server returned a blank response body" on exactly such a 2xx (Jackson
        // parses it to MissingNode). Cache it and one transient blank response becomes
        // a permanent failure: every later call, in this and in later JVM runs, is
        // served the blank entry and throws, and the server recovering does not help -
        // nothing evicts on content, so it stands until the TTL expires (forever when
        // no CacheValidator is configured, which is the default). isBlank rather
        // than "empty" because a whitespace-only body is just as contentless, and is
        // rejected by that same guard; a plain whitespace test also keeps this
        // format-agnostic class free of JSON semantics - XmlApiClient gets nothing
        // usable out of a whitespace-only body either. Do not "simplify" this back to
        // body != null.
        if (aCacheable && statusCode >= 200 && statusCode < 300 && body != null && !isBlank(body))
        {
            cache.put(aRequest, new CacheEntry(statusCode, responseHeaders, body));
        }

        // Return a new response wrapping the very bytes just read - no copy, and no
        // decode/re-encode round trip through a String.
        InputStream bufferedBody = body != null ? new ByteArrayInputStream(body) : null;
        return new HttpResponse(statusCode, responseHeaders, bufferedBody);
    }


    /**
     * Reports whether a response body carries nothing but whitespace, without decoding it.
     *
     * <p>
     * This is the {@code byte[]} counterpart of {@link String#isBlank()}, and it is what the
     * blank-2xx cache guard in {@link #execute(HttpRequest, boolean)} keys on. Scanning bytes is
     * exact rather than approximate: UTF-8 encodes every ASCII code point as the single byte of the
     * same value and every non-ASCII code point using bytes {@code >= 0x80}, so a byte in the ASCII
     * range can never be part of a multi-byte character and one pass decides the question with no
     * decoding and no allocation.
     * </p>
     *
     * <p>
     * It differs from {@code new String(aBody, UTF_8).isBlank()} in exactly one case: a body
     * consisting <b>entirely</b> of non-ASCII Unicode whitespace (U+2028, say) counts as blank
     * there and as non-blank here, so it stays cacheable. That is deliberate, and it is not the
     * failure this guard exists to stop - Jackson parses ASCII-whitespace-only input to
     * {@code MissingNode}, which is the silently-empty resource the guard was added for, whereas
     * non-ASCII whitespace is not legal JSON and fails loudly with a parse error rather than being
     * mistaken for an empty document.
     * </p>
     *
     * @param aBody
     *            the body bytes.
     * @return {@code true} if the body is zero-length or contains only ASCII whitespace.
     */
    static boolean isBlank(byte[] aBody)
    {
        for (byte b : aBody)
        {
            // A negative byte has the high bit set, i.e. it is part of a multi-byte UTF-8
            // character - never whitespace, and never confusable with an ASCII byte.
            if (b < 0 || !Character.isWhitespace((char) b))
            {
                return false;
            }
        }
        return true;
    }


    /**
     * Reads the response body as a string, returning a placeholder on failure. Used for error
     * responses where the body is informational only.
     *
     * <p>
     * This is the single shared copy for all clients (F-webapi-08 — it previously existed as two
     * independent identical copies in {@code JsonApiClient} and {@code XmlApiClient}, which had
     * already started to drift in coverage). The error-body read is transport-level, not
     * format-level, so it lives here.
     * </p>
     *
     * @param aResponse
     *            the response whose body to read.
     * @return the body as a UTF-8 string, {@code null} if there is no body, or a placeholder if
     *         reading it fails.
     */
    protected static @Nullable String readBodySafe(HttpResponse aResponse)
    {
        // Read body() ONCE into a local: null-checking one call and dereferencing
        // a second is what SpotBugs 4.10 flags as
        // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE.
        InputStream body = aResponse.body();
        if (body == null)
        {
            return null;
        }
        try
        {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException _)
        {
            return "(unable to read response body)";
        }
    }

    // --- Request building helpers ---


    /**
     * Builds a GET request for the given path with the base URL prepended and default headers
     * applied.
     *
     * @param aPath
     *            the endpoint path (e.g., "/mdr/adam/adam-2-1").
     * @return the fully constructed HTTP request.
     */
    protected HttpRequest newGetRequest(String aPath)
    {
        String normalizedPath = ensureLeadingSlash(aPath);
        URI uri = URI.create(baseUrl + normalizedPath);
        HttpRequest.Builder builder = HttpRequest.get(uri);
        applyDefaultHeaders(builder);
        return builder.build();
    }


    /**
     * Applies default headers to a request builder.
     *
     * @param aRequestBuilder
     *            the request builder.
     */
    protected void applyDefaultHeaders(HttpRequest.Builder aRequestBuilder)
    {
        for (HeaderEntry header : defaultHeaders)
        {
            aRequestBuilder.header(header.name(), header.value());
        }
    }

    // --- Accessors ---


    /**
     * Returns the configured base URL.
     */
    public String baseUrl()
    {
        return baseUrl;
    }


    /**
     * Returns the underlying transport.
     */
    public HttpTransport transport()
    {
        return transport;
    }


    /**
     * Returns the cache implementation.
     */
    public ApiCache cache()
    {
        return cache;
    }


    /**
     * Removes <b>one</b> cache entry, named by its storage key.
     *
     * <p>
     * The argument is a {@link ApiCache#toCacheKey(HttpRequest) cache key}, not an endpoint path,
     * and the two are not the same string. A key is the request's full URI path — including the
     * base URL's own path — followed by the normalised query when the request carried one. Against
     * the default CDISC Library base URL {@code https://api.library.cdisc.org/api/}, the entry for
     * endpoint {@code /mdr/adam/adam-2-1} fetched with {@code expand=true} is keyed
     * {@code /api/mdr/adam/adam-2-1?expand=true}, and that whole string is what has to be passed
     * here. Only the leading slash is supplied for you.
     * </p>
     *
     * <p>
     * What it therefore <b>cannot</b> do:
     * </p>
     * <ul>
     * <li>clear a query-bearing entry when given the bare path — since the key rework the query
     * participates in the key, so {@code /api/mdr/adam/adam-2-1} and
     * {@code /api/mdr/adam/adam-2-1?expand=true} are separate entries and this method touches
     * exactly the one named;</li>
     * <li>clear "everything for an endpoint" — there is no prefix, wildcard or per-endpoint form,
     * and no way to enumerate the queries an endpoint was cached under;</li>
     * <li>reach the {@linkplain ApiCache#toLegacyCacheKey(HttpRequest) legacy path-only} companion
     * of a query-bearing entry. {@link ApiCache#invalidate(HttpRequest)} does that, because it has
     * the request and can derive both keys; this overload has only a string.</li>
     * </ul>
     *
     * <p>
     * When the caller holds the request rather than the key, prefer
     * {@link ApiCache#invalidate(HttpRequest)} via {@link #cache()}, which derives the key (and its
     * legacy form) itself.
     * </p>
     *
     * @param aPath
     *            the cache key to remove, e.g. {@code "/api/mdr/adam/adam-2-1?expand=true"}; a
     *            leading slash is added when absent.
     * @return {@code true} if a cache entry was removed.
     * @throws IOException
     *             in case of an I/O error.
     */
    public boolean invalidateCache(String aPath) throws IOException
    {
        return cache.invalidate(ensureLeadingSlash(aPath));
    }

    // --- String utilities ---


    protected static String stripTrailingSlash(String aUrl)
    {
        return aUrl.endsWith("/") ? aUrl.substring(0, aUrl.length() - 1) : aUrl;
    }


    protected static String ensureLeadingSlash(String aPath)
    {
        return aPath.startsWith("/") ? aPath : "/" + aPath;
    }

    // --- Builder ---

    /**
     * Abstract builder base for API clients. Uses the self-type pattern so that subclass builders
     * return the correct builder type from fluent setter methods.
     *
     * @param <B>
     *            the concrete builder type (self-type).
     */
    public abstract static class AbstractBuilder<B extends AbstractBuilder<B>>
    {

        protected @Nullable HttpTransport transport;

        protected @Nullable String baseUrl;

        protected final List<HeaderEntry> defaultHeaders = new ArrayList<>();

        protected @Nullable Path cacheDir;

        protected @Nullable ApiCache cache;

        protected @Nullable CacheValidator cacheValidator;

        protected AbstractBuilder()
        {
        }


        @SuppressWarnings("unchecked")
        protected B self()
        {
            return (B) this;
        }


        public B transport(HttpTransport aTransport)
        {
            this.transport = aTransport;
            return self();
        }


        public B baseUrl(String aBaseUrl)
        {
            this.baseUrl = aBaseUrl;
            return self();
        }


        /**
         * Adds a default header that will be included in every request.
         */
        public B defaultHeader(String aName, String aValue)
        {
            defaultHeaders.add(new HeaderEntry(aName, aValue));
            return self();
        }


        /**
         * Sets the directory for file-system response caching. If {@code null} (the default),
         * caching is disabled. For custom cache implementations, use {@link #cache(ApiCache)}.
         */
        public B cacheDir(@Nullable Path aCacheDir)
        {
            this.cacheDir = aCacheDir;
            return self();
        }


        /**
         * Sets a custom {@link ApiCache} implementation. Takes precedence over
         * {@link #cacheDir(Path)}.
         */
        public B cache(@Nullable ApiCache aCache)
        {
            this.cache = aCache;
            return self();
        }


        /**
         * Sets a {@link CacheValidator} for the file-based cache created from
         * {@link #cacheDir(Path)}. Has no effect when a custom {@link ApiCache} is provided.
         */
        public B cacheValidator(@Nullable CacheValidator aCacheValidator)
        {
            this.cacheValidator = aCacheValidator;
            return self();
        }
    }


    /**
     * Internal record for storing default header name-value pairs.
     */
    protected record HeaderEntry(String name, String value)
    {
    }
}
