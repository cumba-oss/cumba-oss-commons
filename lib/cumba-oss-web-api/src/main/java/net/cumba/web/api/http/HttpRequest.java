package net.cumba.web.api.http;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An immutable HTTP request representation, decoupled from any specific HTTP client.
 *
 * <p>
 * Instances are created via the {@link Builder}. The request body is optional and provided as an
 * {@link InputStream} for streaming support.
 * </p>
 */
public final class HttpRequest
{

    private final URI uri;

    private final HttpMethod method;

    private final Map<String, List<String>> headers;

    private final @Nullable InputStream body; // nullable

    private HttpRequest(Builder builder)
    {
        this.uri = Objects.requireNonNull(builder.uri, "uri must not be null");
        this.method = Objects.requireNonNull(builder.method, "method must not be null");
        this.headers = Collections.unmodifiableMap(deepCopyHeaders(builder.headers));
        this.body = builder.body;
    }


    public URI uri()
    {
        return uri;
    }


    public HttpMethod method()
    {
        return method;
    }


    /**
     * Returns an unmodifiable view of the request headers. Each header name maps to one or more
     * values.
     */
    public Map<String, List<String>> headers()
    {
        return headers;
    }


    /**
     * Returns the first value for the given header name, or {@code null} if not set.
     */
    public @Nullable String header(String name)
    {
        List<String> values = headers.get(name);
        return values != null && !values.isEmpty() ? values.getFirst() : null;
    }


    /**
     * Returns the optional request body stream, or {@code null} if no body is set.
     */
    public @Nullable InputStream body()
    {
        return body;
    }


    @Override
    public String toString()
    {
        return method + " " + uri;
    }

    // --- Builder ---


    public static Builder newBuilder(URI uri)
    {
        return new Builder().uri(uri);
    }


    public static Builder get(URI uri)
    {
        return newBuilder(uri).method(HttpMethod.GET);
    }


    public static Builder get(String uri)
    {
        return get(URI.create(uri));
    }

    public static final class Builder
    {

        private @Nullable URI uri;

        private HttpMethod method = HttpMethod.GET;

        private final Map<String, List<String>> headers = new LinkedHashMap<>();

        private @Nullable InputStream body;

        private Builder()
        {
        }


        public Builder uri(URI uri)
        {
            this.uri = uri;
            return this;
        }


        public Builder method(HttpMethod method)
        {
            this.method = method;
            return this;
        }


        /**
         * Adds a header value. Multiple values for the same name are accumulated.
         */
        public Builder header(String name, String value)
        {
            headers.computeIfAbsent(name, _ -> new ArrayList<>()).add(value);
            return this;
        }


        /**
         * Sets a header, replacing any previous values for that name.
         */
        public Builder setHeader(String name, String value)
        {
            List<String> list = new ArrayList<>();
            list.add(value);
            headers.put(name, list);
            return this;
        }


        public Builder body(@Nullable InputStream body)
        {
            this.body = body;
            return this;
        }


        public HttpRequest build()
        {
            return new HttpRequest(this);
        }
    }

    private static Map<String, List<String>> deepCopyHeaders(Map<String, List<String>> source)
    {
        Map<String, List<String>> copy = LinkedHashMap.newLinkedHashMap(source.size());
        for (var entry : source.entrySet())
        {
            copy.put(entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return copy;
    }
}
