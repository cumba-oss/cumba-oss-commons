package net.cumba.web.api.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.CustomLog;
import net.cumba.web.api.AbstractApiClient;
import net.cumba.web.api.ApiException;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
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
@CustomLog
public class XmlApiClient extends AbstractApiClient
{

    private final DocumentBuilderFactory documentBuilderFactory;

    protected XmlApiClient(Builder builder)
    {
        super(builder, ".xml");

        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        // XXE defence in depth (F-webapi-06). newInstance() performs JAXP lookup, so a
        // third-party factory can be substituted via system property or ServiceLoader;
        // each individual feature is best-effort against such a factory, but losing
        // one is no longer losing all of them, and every loss is logged instead of
        // silently degrading to an XXE-capable parser.
        this.documentBuilderFactory.setXIncludeAware(false);
        this.documentBuilderFactory.setExpandEntityReferences(false);
        trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature("http://xml.org/sax/features/external-general-entities", false);
        trySetFeature("http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    }


    private void trySetFeature(String aFeature, boolean aValue)
    {
        try
        {
            this.documentBuilderFactory.setFeature(aFeature, aValue);
        }
        catch (ParserConfigurationException ex)
        {
            LOGGER.log(System.Logger.Level.WARNING,
                    "XML parser factory " + documentBuilderFactory.getClass().getName()
                            + " does not support the security feature \"" + aFeature
                            + "\" — continuing without it: " + ex.getMessage());
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
            // Read body() ONCE into a local: null-checking one call and dereferencing
            // a second is what SpotBugs 4.10 flags as
            // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE.
            InputStream body = response.body();
            if (body == null)
            {
                throw new ApiException(response.statusCode(),
                        "Server returned empty response body");
            }
            // A 2xx whose body is blank — zero bytes, or nothing but whitespace — is
            // not a transport failure but a server one. Fail with ApiException here,
            // symmetrically with JsonApiClient.getRawJson, instead of letting parseXml
            // surface it as a generic IOException("Failed to parse XML response")
            // (F-webapi-05).
            byte[] bytes = body.readAllBytes();
            if (new String(bytes, StandardCharsets.UTF_8).isBlank())
            {
                throw new ApiException(response.statusCode(),
                        "Server returned a blank response body");
            }
            return parseXml(new ByteArrayInputStream(bytes));
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
}
