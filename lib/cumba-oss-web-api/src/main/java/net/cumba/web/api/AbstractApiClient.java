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
     * The returned {@link HttpResponse} always has a fully buffered body (backed by a
     * {@link ByteArrayInputStream}), so it is safe to read after this method returns. The original
     * network stream is already closed.
     * </p>
     *
     * @param aRequest
     *            the HTTP request to execute.
     * @param aCacheable
     *            {@code true} to allow caching, {@code false} to bypass the cache entirely.
     * @return the HTTP response (from cache or network), with a buffered body.
     * @throws IOException
     *             if a network or I/O error occurs.
     */
    protected HttpResponse execute(HttpRequest aRequest, boolean aCacheable) throws IOException
    {
        // Try cache first
        if (aCacheable)
        {
            Optional<CacheEntry> cached = cache.get(aRequest);
            if (cached.isPresent())
            {
                return cached.get().toHttpResponse();
            }
        }

        // Network call — read body fully and close the transport response
        int statusCode;
        Map<String, List<String>> responseHeaders;
        String body;
        try (HttpResponse response = transport.send(aRequest))
        {
            statusCode = response.statusCode();
            responseHeaders = response.headers();
            // Read body() ONCE into a local. Calling it twice - null-check on the
            // first call, dereference on the second - is what SpotBugs 4.10's
            // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE flags: nothing guarantees the
            // second call returns the same non-null value.
            InputStream rawBody = response.body();
            body = rawBody != null ? new String(rawBody.readAllBytes(), StandardCharsets.UTF_8)
                    : null;
        }

        // Cache successful responses when allowed
        if (aCacheable && statusCode >= 200 && statusCode < 300 && body != null)
        {
            cache.put(aRequest, new CacheEntry(statusCode, responseHeaders, body));
        }

        // Return a new response with a buffered body
        InputStream bufferedBody = body != null
                ? new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
                : null;
        return new HttpResponse(statusCode, responseHeaders, bufferedBody);
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
