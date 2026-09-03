package net.cumba.web.api.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * An HTTP response representation, decoupled from any specific HTTP client.
 *
 * <p>
 * The response body is provided as an {@link InputStream}. Callers are responsible for closing the
 * response (or the body stream) when done. Use try-with-resources:
 * </p>
 *
 * <pre>
 * try (HttpResponse response = transport.send(request))
 * {
 *     // read response.body()
 * }
 * </pre>
 *
 * <p>
 * The {@code body} component may be {@code null} when the response has no body.
 * </p>
 *
 * <p>
 * Header names are matched case-insensitively (per RFC 9110): the {@code headers} map is stored in
 * a case-insensitive {@link TreeMap}, so both {@link #header(String)} and direct
 * {@code headers().get(name)} lookups ignore case. Where a source map contains entries that differ
 * only by case, the last one wins.
 * </p>
 */
public record HttpResponse(int statusCode, Map<String, List<String>> headers,
        @Nullable InputStream body) implements AutoCloseable
{

    public HttpResponse
    {
        if (headers != null)
        {
            TreeMap<String, List<String>> caseInsensitive = new TreeMap<>(
                    String.CASE_INSENSITIVE_ORDER);
            caseInsensitive.putAll(headers);
            headers = Collections.unmodifiableMap(caseInsensitive);
        }
        else
        {
            headers = Collections.emptyMap();
        }
    }


    /**
     * Returns the first value for the given header name, or {@code null} if not set. The lookup is
     * case-insensitive.
     */
    public @Nullable String header(String name)
    {
        List<String> values = headers.get(name);
        return values != null && !values.isEmpty() ? values.getFirst() : null;
    }


    /**
     * Returns {@code true} if the status code is in the 2xx range.
     */
    public boolean isSuccess()
    {
        return statusCode >= 200 && statusCode < 300;
    }


    @Override
    public void close() throws IOException
    {
        if (body != null)
        {
            body.close();
        }
    }


    @Override
    public String toString()
    {
        return "HttpResponse[status=" + statusCode + "]";
    }
}
