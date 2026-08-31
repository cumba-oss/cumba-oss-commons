package net.cumba.web.api;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Exception thrown when an API request fails with a non-success HTTP status code.
 *
 * <p>
 * Extends {@link IOException} so that callers only need a single {@code throws IOException} in
 * their signatures. Callers who need to distinguish API errors from transport errors can still
 * catch {@code ApiException} before {@code IOException}.
 * </p>
 */
public class ApiException extends IOException
{

    private static final long serialVersionUID = 8044104052096024524L;

    private final int statusCode;

    private final @Nullable String responseBody;

    public ApiException(int statusCode, @Nullable String responseBody)
    {
        super("HTTP " + statusCode
                + (responseBody != null ? ": " + truncate(responseBody, 200) : ""));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }


    public ApiException(String message, Throwable cause)
    {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }


    public int statusCode()
    {
        return statusCode;
    }


    public @Nullable String responseBody()
    {
        return responseBody;
    }


    /**
     * Returns {@code true} if this was a client error (4xx).
     */
    public boolean isClientError()
    {
        return statusCode >= 400 && statusCode < 500;
    }


    /**
     * Returns {@code true} if this was a server error (5xx).
     */
    public boolean isServerError()
    {
        return statusCode >= 500 && statusCode < 600;
    }


    private static String truncate(String s, int maxLen)
    {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
