package net.cumba.web.api.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * {@link HttpTransport} implementation backed by {@link java.net.http.HttpClient}.
 *
 * <p>
 * This is the default transport. It uses the JDK's built-in HTTP client (HTTP/2 capable) with
 * configurable timeouts and redirect policy.
 * </p>
 *
 * <p>
 * Usage:
 *
 * <pre>
 *
 * HttpTransport transport = new JdkHttpTransport();
 *
 * // or with custom config:
 * HttpTransport transport = new JdkHttpTransport(
 *         HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
 * </pre>
 */
public final class JdkHttpTransport implements HttpTransport, AutoCloseable
{

    /**
     * Per-request read timeout applied to every outgoing {@link java.net.http.HttpRequest} built by
     * this transport. Hung sockets used to wedge OSB / CDISC Library calls indefinitely; a finite
     * timeout converts them into a normal {@link IOException} via
     * {@link java.net.http.HttpTimeoutException}.
     */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient client;

    private final Duration requestTimeout;

    /**
     * Creates a transport with a default {@link HttpClient} (30s connect timeout, follow redirects)
     * and the {@linkplain #DEFAULT_REQUEST_TIMEOUT default per-request read timeout}.
     */
    public JdkHttpTransport()
    {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }


    /**
     * Creates a transport with a pre-configured {@link HttpClient} and the
     * {@linkplain #DEFAULT_REQUEST_TIMEOUT default per-request read timeout}.
     */
    public JdkHttpTransport(HttpClient client)
    {
        this(client, DEFAULT_REQUEST_TIMEOUT);
    }


    /**
     * Creates a transport with a pre-configured {@link HttpClient} and a custom per-request read
     * timeout. Pass {@code null} to disable the per-request timeout (not recommended in production
     * code).
     */
    public JdkHttpTransport(HttpClient client, Duration requestTimeout)
    {
        this.client = client;
        this.requestTimeout = requestTimeout;
    }


    @Override
    public HttpResponse send(HttpRequest request) throws IOException
    {
        java.net.http.HttpRequest.Builder jdkBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(request.uri());
        if (requestTimeout != null)
        {
            jdkBuilder.timeout(requestTimeout);
        }

        // Map method + body
        java.net.http.HttpRequest.BodyPublisher bodyPublisher = request.body() != null
                ? java.net.http.HttpRequest.BodyPublishers.ofInputStream(request::body)
                : java.net.http.HttpRequest.BodyPublishers.noBody();

        jdkBuilder.method(request.method().name(), bodyPublisher);

        // Map headers
        for (var entry : request.headers().entrySet())
        {
            for (String value : entry.getValue())
            {
                jdkBuilder.header(entry.getKey(), value);
            }
        }

        // Advertise gzip support unless the caller already set an Accept-Encoding. The JDK
        // HttpClient neither requests nor transparently decodes gzip, so we negotiate it here and
        // decode the response below. Header names are matched case-insensitively so a caller's
        // lowercase header is not duplicated.
        boolean callerSetAcceptEncoding = request.headers().keySet().stream()
                .anyMatch(name -> name.equalsIgnoreCase("Accept-Encoding"));
        if (!callerSetAcceptEncoding)
        {
            jdkBuilder.header("Accept-Encoding", "gzip");
        }

        java.net.http.HttpRequest jdkRequest = jdkBuilder.build();

        try
        {
            java.net.http.HttpResponse<java.io.InputStream> jdkResponse = client.send(jdkRequest,
                    java.net.http.HttpResponse.BodyHandlers.ofInputStream());

            // Convert JDK response headers to our format
            Map<String, List<String>> headers = new HashMap<>(jdkResponse.headers().map());

            // Decode gzip transparently. The check uses the JDK's case-insensitive header view; our
            // own header map copy above is a plain HashMap and would be case-sensitive.
            java.io.InputStream body = jdkResponse.body();
            boolean gzip = jdkResponse.headers().firstValue("Content-Encoding")
                    .filter(value -> value.equalsIgnoreCase("gzip")).isPresent();
            if (gzip && body != null)
            {
                body = new GZIPInputStream(body);
                // The body is now decoded, so headers describing the encoded form no longer apply.
                // Remove them (case-insensitively) to keep the response — and any cache entry
                // derived from it — honest.
                headers.keySet().removeIf(name -> name.equalsIgnoreCase("Content-Encoding")
                        || name.equalsIgnoreCase("Content-Length"));
            }

            return new HttpResponse(jdkResponse.statusCode(), headers, body);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted: " + request, e);
        }
    }


    /**
     * Closes the underlying {@link HttpClient}, releasing any held resources (threads,
     * connections).
     */
    @Override
    public void close()
    {
        client.close();
    }
}
