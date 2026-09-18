package net.cumba.web.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the XXE hardening {@link XmlApiClient} applies to its {@link DocumentBuilderFactory}
 * (F-webapi-06).
 *
 * <p>
 * The factory comes from {@code DocumentBuilderFactory.newInstance()}, which performs a JAXP
 * lookup, so a deployment can substitute a third-party factory through the
 * {@code javax.xml.parsers.DocumentBuilderFactory} system property. That is exactly what these
 * tests do: they install a recording factory, build a client, and read back what the client
 * configured. Without this, none of the individual defences is observable — the
 * {@code disallow-doctype-decl} feature alone rejects every document the others would protect
 * against, so five of the six hardening calls could be deleted with no test noticing.
 * </p>
 */
class XmlApiClientFactoryHardeningTest
{

    private static final String PROPERTY = "javax.xml.parsers.DocumentBuilderFactory";

    @AfterEach
    void restoreTheJaxpLookup()
    {
        System.clearProperty(PROPERTY);
        RecordingFactory.reset();
        RejectingFactory.reset();
    }


    private static XmlApiClient buildClient()
    {
        return XmlApiClient.builder().baseUrl("https://example.org").transport(new StubTransport())
                .build();
    }


    @Test
    void everyXxeDefenceIsAppliedToWhateverFactoryJaxpSupplies()
    {
        System.setProperty(PROPERTY, RecordingFactory.class.getName());
        assertNotNull(buildClient());

        assertEquals(true, RecordingFactory.namespaceAware,
                "namespace awareness is what makes local-name matching work at all");
        assertEquals(false, RecordingFactory.xIncludeAware, "XInclude pulls in documents");
        assertEquals(false, RecordingFactory.expandEntityReferences,
                "entity expansion is the billion-laughs vector");

        Map<String, Boolean> features = RecordingFactory.features;
        assertEquals(true, features.get("http://apache.org/xml/features/disallow-doctype-decl"));
        assertEquals(false, features.get("http://xml.org/sax/features/external-general-entities"));
        assertEquals(false,
                features.get("http://xml.org/sax/features/external-parameter-entities"));
        assertEquals(true, features.get(XMLConstants.FEATURE_SECURE_PROCESSING));
    }


    @Test
    void aFactoryThatRefusesAFeatureIsToleratedAndTheOthersStillGoOn()
    {
        System.setProperty(PROPERTY, RejectingFactory.class.getName());

        // Best effort: losing one feature must not abort construction, and must not stop the
        // remaining defences being applied.
        assertNotNull(buildClient(), "a factory refusing a feature must not break the client");
        assertEquals(4, RejectingFactory.attempted.size(),
                "every feature is still attempted after one is refused: "
                        + RejectingFactory.attempted);
        assertTrue(RejectingFactory.attempted
                .contains("http://apache.org/xml/features/disallow-doctype-decl"));
        assertTrue(RejectingFactory.attempted.contains(XMLConstants.FEATURE_SECURE_PROCESSING));
    }


    @Test
    void aDocumentTypeDeclarationIsRejectedByTheRealFactory()
    {
        XmlApiClient client = XmlApiClient.builder().baseUrl("https://example.org")
                .transport(new StubTransport("<!DOCTYPE r [<!ENTITY x 'boom'>]><r>&x;</r>"))
                .build();
        IOException failure = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> client.getRawXml("/r"));
        assertTrue(failure.getMessage().contains("Failed to parse XML response"),
                failure.getMessage());
    }

    // --- stubs ---

    /** Serves one fixed XML body so a client can be built and driven without a network. */
    private static final class StubTransport implements HttpTransport
    {

        private final String body;

        StubTransport()
        {
            this("<r/>");
        }


        StubTransport(String aBody)
        {
            body = aBody;
        }


        @Override
        public HttpResponse send(HttpRequest aRequest)
        {
            InputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            return new HttpResponse(200, Map.of(), in);
        }
    }


    /**
     * Records what the client configures, then delegates to the JDK's built-in factory.
     *
     * <p>
     * ⚠ {@code newDefaultInstance()} rather than {@code newInstance()} — the latter would re-read
     * the system property this class is installed under and recurse forever.
     * </p>
     */
    public static final class RecordingFactory extends DocumentBuilderFactory
    {

        static Boolean namespaceAware;

        static Boolean xIncludeAware;

        static Boolean expandEntityReferences;

        static Map<String, Boolean> features = new LinkedHashMap<>();

        private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newDefaultInstance();

        static void reset()
        {
            namespaceAware = null;
            xIncludeAware = null;
            expandEntityReferences = null;
            features = new LinkedHashMap<>();
        }


        @Override
        public void setNamespaceAware(boolean aValue)
        {
            namespaceAware = aValue;
            delegate.setNamespaceAware(aValue);
        }


        @Override
        public void setXIncludeAware(boolean aValue)
        {
            xIncludeAware = aValue;
            delegate.setXIncludeAware(aValue);
        }


        @Override
        public void setExpandEntityReferences(boolean aValue)
        {
            expandEntityReferences = aValue;
            delegate.setExpandEntityReferences(aValue);
        }


        @Override
        public void setFeature(String aName, boolean aValue) throws ParserConfigurationException
        {
            features.put(aName, aValue);
            delegate.setFeature(aName, aValue);
        }


        @Override
        public boolean getFeature(String aName) throws ParserConfigurationException
        {
            return delegate.getFeature(aName);
        }


        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException
        {
            return delegate.newDocumentBuilder();
        }


        @Override
        public void setAttribute(String aName, Object aValue)
        {
            delegate.setAttribute(aName, aValue);
        }


        @Override
        public Object getAttribute(String aName)
        {
            return delegate.getAttribute(aName);
        }
    }


    /** A factory that supports no security feature at all, so every {@code setFeature} throws. */
    public static final class RejectingFactory extends DocumentBuilderFactory
    {

        static List<String> attempted = new ArrayList<>();

        private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newDefaultInstance();

        static void reset()
        {
            attempted = new ArrayList<>();
        }


        @Override
        public void setFeature(String aName, boolean aValue) throws ParserConfigurationException
        {
            attempted.add(aName);
            throw new ParserConfigurationException("unsupported: " + aName);
        }


        @Override
        public boolean getFeature(String aName)
        {
            return false;
        }


        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException
        {
            return delegate.newDocumentBuilder();
        }


        @Override
        public void setAttribute(String aName, Object aValue)
        {
            delegate.setAttribute(aName, aValue);
        }


        @Override
        public Object getAttribute(String aName)
        {
            return delegate.getAttribute(aName);
        }
    }
}
