package net.cumba.web.api.http;

import java.io.IOException;

/**
 * Transport abstraction for sending HTTP requests.
 *
 * <p>
 * This is the single interface that concrete HTTP client implementations must provide. Swap
 * implementations to switch between {@code java.net.http.HttpClient}, Apache HttpClient, OkHttp, or
 * any other transport.
 * </p>
 *
 * <p>
 * Implementations should be thread-safe and reusable.
 * </p>
 *
 * @see JdkHttpTransport
 */
@FunctionalInterface
public interface HttpTransport
{

    /**
     * Sends an HTTP request and returns the response.
     *
     * <p>
     * The caller is responsible for closing the returned {@link HttpResponse} to release the
     * underlying body stream.
     * </p>
     *
     * @param request
     *            the request to send
     * @return the response
     * @throws IOException
     *             if an I/O error occurs during the request
     */
    HttpResponse send(HttpRequest request) throws IOException;
}
