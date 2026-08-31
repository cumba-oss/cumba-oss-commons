package net.cumba.web.api.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.cumba.web.api.AbstractApiClient;
import net.cumba.web.api.ApiException;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import org.jspecify.annotations.Nullable;

/**
 * JSON API client that extends {@link AbstractApiClient} with JSON-specific parsing via Jackson.
 *
 * <p>
 * Handles JSON parsing and wraps responses as {@link ApiResource} views backed by
 * {@link JsonNodeResource}. All transport, caching, and header management is handled by the
 * abstract base class.
 * </p>
 *
 * <p>
 * Usage:
 *
 * <pre>
 *
 * JsonApiClient client = JsonApiClient.builder().transport(new JdkHttpTransport())
 *         .baseUrl("https://api.example.com").defaultHeader("Accept", "application/json")
 *         .cacheDir(Path.of("cache")).build();
 *
 * AdamProduct product = client.get("/mdr/adam/adam-2-1", AdamProduct.class);
 * </pre>
 */
public class JsonApiClient extends AbstractApiClient
{

    private final ObjectMapper objectMapper;

    protected JsonApiClient(Builder builder)
    {
        super(builder, ".json");
        this.objectMapper = builder.objectMapper != null ? builder.objectMapper
                : new ObjectMapper();
    }

    // --- Public API ---


    /**
     * Performs a GET request and returns the response as a typed {@link ApiResource}.
     *
     * @param aPath
     *            the path relative to the base URL (e.g. "/mdr/adam/adam-2-1")
     * @param aType
     *            the target ApiResource interface
     * @param <T>
     *            the target type
     * @return the parsed resource
     * @throws ApiException
     *             if the server returns a non-2xx status
     * @throws IOException
     *             if an I/O error occurs
     */
    public <T extends ApiResource> T get(String aPath, Class<T> aType) throws IOException
    {
        JsonNode node = getRawJson(aPath);
        return JsonNodeResource.of(node, aType);
    }


    /**
     * Performs a GET request and returns the response as a plain {@link ApiResource}.
     */
    public ApiResource get(String aPath) throws IOException
    {
        JsonNode node = getRawJson(aPath);
        return JsonNodeResource.of(node);
    }


    /**
     * Performs a GET request and returns the raw {@link JsonNode}. If caching is enabled, serves
     * from cache when available and writes new responses to the cache.
     */
    public JsonNode getRawJson(String aPath) throws IOException
    {
        HttpRequest request = newGetRequest(aPath);
        try (HttpResponse response = execute(request, true))
        {
            if (!response.isSuccess())
            {
                String body = readBodySafe(response);
                throw new ApiException(response.statusCode(), body);
            }
            if (response.body() == null)
            {
                throw new ApiException(response.statusCode(),
                        "Server returned empty response body");
            }
            return objectMapper.readTree(response.body());
        }
    }


    /**
     * Returns the ObjectMapper used for JSON parsing.
     */
    public ObjectMapper objectMapper()
    {
        return objectMapper;
    }

    // --- Builder ---


    public static Builder builder()
    {
        return new Builder()
        {

            @Override
            public JsonApiClient build()
            {
                return new JsonApiClient(this);
            }
        };
    }

    public abstract static class Builder extends AbstractBuilder<Builder>
    {

        protected @Nullable ObjectMapper objectMapper;

        protected Builder()
        {
        }


        public Builder objectMapper(ObjectMapper aObjectMapper)
        {
            this.objectMapper = aObjectMapper;
            return self();
        }


        public abstract JsonApiClient build();
    }

    // --- Internal ---

    /**
     * Reads the response body as a string, returning a placeholder on failure. Used for error
     * responses where the body is informational only.
     */
    protected static @Nullable String readBodySafe(HttpResponse aResponse)
    {
        if (aResponse.body() == null)
        {
            return null;
        }
        try
        {
            return new String(aResponse.body().readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException _)
        {
            return "(unable to read response body)";
        }
    }
}
