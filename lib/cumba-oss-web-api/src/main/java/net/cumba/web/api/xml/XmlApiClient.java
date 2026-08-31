package net.cumba.web.api.xml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.cumba.web.api.AbstractApiClient;
import net.cumba.web.api.ApiException;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * HTTP client for XML-based APIs that extends {@link AbstractApiClient} with XML-specific parsing.
 *
 * <p>
 * Fetches XML responses, parses them with a namespace-aware {@link DocumentBuilder}, and wraps the
 * root element as an {@link XmlElementResource}. All transport, caching, and header management is
 * handled by the abstract base class.
 * </p>
 *
 * <p>
 * Usage:
 *
 * <pre>
 *
 * XmlApiClient client = XmlApiClient.builder().transport(new JdkHttpTransport())
 *         .baseUrl("https://api.example.com").defaultHeader("Accept", "application/xml").build();
 *
 * ItemGroupDef igDef = client.get("/define/IG.DM", ItemGroupDef.class);
 * </pre>
 */
public class XmlApiClient extends AbstractApiClient
{

    private final DocumentBuilderFactory documentBuilderFactory;

    @SuppressWarnings("PMD.EmptyCatchBlock")
    protected XmlApiClient(Builder builder)
    {
        super(builder, ".xml");

        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        // Disable external entities for security
        try
        {
            this.documentBuilderFactory
                    .setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        }
        catch (ParserConfigurationException _)
        {
            // Not all parsers support this feature — continue without it
        }
    }

    // --- Public API ---


    /**
     * Performs a GET request and returns the response as a typed {@link ApiResource}.
     *
     * @param aPath
     *            the path relative to the base URL
     * @param aType
     *            the target ApiResource interface
     * @param <T>
     *            the target type
     * @return the parsed resource
     * @throws ApiException
     *             if the server returns a non-2xx status
     * @throws IOException
     *             if an I/O or XML parsing error occurs
     */
    public <T extends ApiResource> T get(String aPath, Class<T> aType) throws IOException
    {
        Element root = getRawXml(aPath);
        return XmlElementResource.of(root, aType);
    }


    /**
     * Performs a GET request and returns the response as a plain {@link ApiResource}.
     */
    public ApiResource get(String aPath) throws IOException
    {
        Element root = getRawXml(aPath);
        return XmlElementResource.of(root);
    }


    /**
     * Performs a GET request and returns the root {@link Element} of the parsed XML document. If
     * caching is enabled, serves from cache when available and writes new responses to the cache.
     */
    public Element getRawXml(String aPath) throws IOException
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
            return parseXml(response.body());
        }
    }

    // --- XML parsing ---


    private Element parseXml(InputStream aInput) throws IOException
    {
        try
        {
            DocumentBuilder db = documentBuilderFactory.newDocumentBuilder();
            Document doc = db.parse(aInput);
            return doc.getDocumentElement();
        }
        catch (ParserConfigurationException | SAXException ex)
        {
            throw new IOException("Failed to parse XML response", ex);
        }
    }

    // --- Builder ---


    public static Builder builder()
    {
        return new Builder()
        {

            @Override
            public XmlApiClient build()
            {
                return new XmlApiClient(this);
            }
        };
    }

    public abstract static class Builder extends AbstractBuilder<Builder>
    {

        protected Builder()
        {
        }


        public abstract XmlApiClient build();
    }

    // --- Internal ---

    /**
     * Reads the response body as a string, returning a placeholder on failure.
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
}
