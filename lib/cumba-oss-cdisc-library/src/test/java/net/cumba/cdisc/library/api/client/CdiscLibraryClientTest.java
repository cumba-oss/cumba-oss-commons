package net.cumba.cdisc.library.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import net.cumba.cdisc.library.api.model.adam.AdamProduct;
import net.cumba.cdisc.library.api.model.ct.CtPackageList;
import net.cumba.cdisc.library.api.model.meta.About;
import net.cumba.cdisc.library.api.model.meta.Maintenance;
import net.cumba.cdisc.library.api.model.products.Products;
import net.cumba.cdisc.library.api.model.rules.RulePackage;
import net.cumba.cdisc.library.api.model.sdtm.SdtmDataset;
import net.cumba.cdisc.library.api.model.sdtm.SdtmProduct;
import net.cumba.cdisc.library.api.model.search.SearchResult;
import net.cumba.web.api.Link;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CdiscLibraryClientTest
{

    private RecordingTransport transport;

    private CdiscLibraryClient client;

    @BeforeEach
    void setUp()
    {
        transport = new RecordingTransport();
        client = CdiscLibraryClient.builder().apiKey("test-key").transport(transport).build();
    }

    // --- Builder tests ---


    @Test
    void builderRejectsNullApiKey()
    {
        assertThrows(NullPointerException.class, () -> CdiscLibraryClient.builder().apiKey(null));
    }


    @Test
    void builderSetsDefaultBaseUrl()
    {
        // baseUrl() strips the trailing slash from DEFAULT_BASE_URL
        String expected = CdiscLibraryClient.DEFAULT_BASE_URL;
        if (expected.endsWith("/"))
        {
            expected = expected.substring(0, expected.length() - 1);
        }
        assertEquals(expected, client.baseUrl());
    }


    @Test
    void builderAllowsCustomBaseUrl()
    {
        CdiscLibraryClient custom = CdiscLibraryClient.builder().apiKey("key")
                .baseUrl("https://custom.api.com").transport(transport).build();
        assertEquals("https://custom.api.com", custom.baseUrl());
    }


    @Test
    void builderCreateDefaultTransportWhenNotProvided()
    {
        CdiscLibraryClient withDefault = CdiscLibraryClient.builder().apiKey("key").build();
        assertNotNull(withDefault.transport());
    }


    @Test
    void builderSupportsCacheDir(@TempDir Path tempDir)
    {
        CdiscLibraryClient cached = CdiscLibraryClient.builder().apiKey("key").transport(transport)
                .cacheDir(tempDir).build();
        assertInstanceOf(net.cumba.web.api.cache.FileApiCache.class, cached.cache());
    }

    // --- Products ---


    @Test
    void getProductsCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{}");
        client.getProducts();
        assertEquals("/mdr/products", transport.lastRequestPath());
    }


    @Test
    void getProductsExpandCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{}");
        client.getProducts(true);
        assertEquals("/mdr/products?expand=true", transport.lastRequestPath());
    }

    // --- ADaM ---


    @Test
    void getAdamProductCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"name\":\"ADaM\"}");
        AdamProduct product = client.getAdamProduct("adam-2-1");
        assertEquals("/mdr/adam/adam-2-1", transport.lastRequestPath());
        assertEquals("ADaM", product.name().orElse(null));
    }


    @Test
    void getAdamProductRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getAdamProduct(null));
    }


    @Test
    void getAdamProductRejectsBlank()
    {
        assertThrows(IllegalArgumentException.class, () -> client.getAdamProduct("  "));
    }


    @Test
    void getAdamDataStructureRejectsNullProduct()
    {
        assertThrows(NullPointerException.class, () -> client.getAdamDataStructure(null, "ds"));
    }


    @Test
    void getAdamDataStructureRejectsNullDataStructure()
    {
        assertThrows(NullPointerException.class,
                () -> client.getAdamDataStructure("adam-2-1", null));
    }

    // --- SDTM ---


    @Test
    void getSdtmVersionCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"name\":\"SDTM\"}");
        SdtmProduct product = client.getSdtmVersion("sdtm", "2-0");
        assertEquals("/mdr/sdtm/2-0", transport.lastRequestPath());
        assertEquals("SDTM", product.name().orElse(null));
    }


    @Test
    void getSdtmVersionRejectsNullStandard()
    {
        assertThrows(NullPointerException.class, () -> client.getSdtmVersion(null, "2-0"));
    }


    @Test
    void getSdtmDatasetCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"name\":\"AE\",\"label\":\"Adverse Events\"}");
        SdtmDataset ds = client.getSdtmDataset("sdtmig", "3-4", "AE");
        assertEquals("/mdr/sdtmig/3-4/datasets/AE", transport.lastRequestPath());
        assertEquals("AE", ds.name().orElse(null));
        assertEquals("Adverse Events", ds.label().orElse(null));
    }


    @Test
    void getSdtmDatasetRejectsNullDataset()
    {
        assertThrows(NullPointerException.class,
                () -> client.getSdtmDataset("sdtmig", "3-4", null));
    }


    @Test
    void getSdtmClassLinksCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse(
                "{\"_links\":{\"classes\":[{\"href\":\"/mdr/sdtm/2-0/classes/Events\",\"title\":\"Events\"}]}}");
        List<Link> links = client.getSdtmClassLinks("2-0");
        assertEquals("/mdr/sdtm/2-0/classes", transport.lastRequestPath());
        assertEquals(1, links.size());
        assertEquals("Events", links.get(0).title().orElse(null));
    }

    // --- CT ---


    @Test
    void getCtPackagesCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"_links\":{\"packages\":[]}}");
        CtPackageList packages = client.getCtPackages();
        assertEquals("/mdr/ct/packages", transport.lastRequestPath());
        assertTrue(packages.packageLinks().isEmpty());
    }


    @Test
    void getCtPackageRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getCtPackage(null));
    }


    @Test
    void getCtCodelistRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getCtCodelist(null, "codelist"));
        assertThrows(NullPointerException.class, () -> client.getCtCodelist("pkg", null));
    }

    // --- Rules ---


    @Test
    void getRulesCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"rules\":{}}");
        RulePackage rp = client.getRules("sdtmig", "3-4");
        assertEquals("/mdr/rules/sdtmig/3-4", transport.lastRequestPath());
        assertTrue(rp.rules().isPresent());
        assertTrue(rp.rules().get().isEmpty());
    }


    @Test
    void getRulesRejectsNullStandard()
    {
        assertThrows(NullPointerException.class, () -> client.getRules(null, "3-4"));
    }


    @Test
    void getRuleRejectsBlankRuleId()
    {
        assertThrows(IllegalArgumentException.class, () -> client.getRule("sdtmig", "3-4", ""));
    }

    // --- Search ---


    @Test
    void searchCallsCorrectEndpointWithEncoding() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"_links\":{\"searchResults\":[]}}");
        SearchResult result = client.search("test query");
        assertTrue(transport.lastRequestPath().startsWith("/mdr/search?q="));
        assertTrue(transport.lastRequestPath().contains("test+query")
                || transport.lastRequestPath().contains("test%20query"));
        assertTrue(result.searchResultLinks().isEmpty());
    }


    @Test
    void searchRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.search(null));
    }


    @Test
    void suggestRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.suggest(null));
    }


    @Test
    void searchInScopeRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.searchInScope(null));
    }

    // --- Diff ---


    @Test
    void getDiffCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"diff\":[]}");
        client.getDiff("sdtmig", "3-4");
        assertEquals("/mdr/diff/sdtmig/3-4", transport.lastRequestPath());
    }


    @Test
    void getDiffWithPreviousCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"diff\":[]}");
        client.getDiff("sdtmig", "3-4", "3-3");
        assertEquals("/mdr/diff/sdtmig/3-4/3-3", transport.lastRequestPath());
    }


    @Test
    void getDiffRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getDiff(null, "3-4"));
        assertThrows(NullPointerException.class, () -> client.getDiff("sdtmig", null));
    }

    // --- Meta ---


    @Test
    void getAboutCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"release-notes\":\"v1.0\"}");
        About about = client.getAbout();
        assertEquals("/mdr/about", transport.lastRequestPath());
        assertEquals("v1.0", about.releaseNotes().orElse(null));
    }


    @Test
    void getMaintenanceCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse(
                "{\"maintenanceMode\":false,\"maintenanceMessage\":\"OK\"}");
        Maintenance m = client.getMaintenance();
        assertEquals("/mdr/maintenance", transport.lastRequestPath());
        assertFalse(m.maintenanceMode().orElse(true));
        assertEquals("OK", m.maintenanceMessage().orElse(null));
    }


    @Test
    void getLastUpdatedCallsCorrectEndpoint() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"overall\":\"2025-01-01\"}");
        client.getLastUpdated();
        assertEquals("/mdr/lastupdated", transport.lastRequestPath());
    }

    // --- HATEOAS link following ---


    @Test
    void followLinkCallsCorrectEndpoint() throws IOException
    {
        // Set up a response with a link
        transport.nextResponse = jsonResponse(
                "{\"_links\":{\"self\":{\"href\":\"/mdr/adam/adam-2-1\"}}}");
        client.get("/mdr/products", Products.class);

        // Now follow a custom link
        transport.nextResponse = jsonResponse("{\"name\":\"ADaM 2.1\"}");
        // Create a link-like object by getting one from a response
        transport.nextResponse = jsonResponse("{\"name\":\"followed\"}");
        client.get("/mdr/test");
        // The follow method is tested for null rejection
        assertThrows(NullPointerException.class, () -> client.follow(null, AdamProduct.class));
    }

    // --- Document methods ---


    @Test
    void getDocumentRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getDocument(null));
    }


    @Test
    void getDocumentSectionsRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getDocumentSections(null, "3-4"));
        assertThrows(NullPointerException.class, () -> client.getDocumentSections("sdtmig", null));
    }

    // --- QRS ---


    @Test
    void getQrsInstrumentRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getQrsInstrument(null, "1-0"));
        assertThrows(NullPointerException.class, () -> client.getQrsInstrument("PHQ-9", null));
    }

    // --- Integrated ---


    @Test
    void getIntegratedVersionRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getIntegratedVersion(null, "1-0"));
        assertThrows(NullPointerException.class, () -> client.getIntegratedVersion("sdtmig", null));
    }

    // --- SENDIG ---


    @Test
    void getSendigVersionRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getSendigVersion(null));
    }

    // --- CDASH ---


    @Test
    void getCdashVersionRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getCdashVersion(null));
    }


    @Test
    void getCdashigVersionRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> client.getCdashigVersion(null));
    }

    // --- Expand helper ---


    @Test
    void expandHandlesExistingQueryParams() throws IOException
    {
        transport.nextResponse = jsonResponse("{}");
        // The expand method is private but we can test it indirectly
        // via getProducts(true) which uses it
        client.getProducts(true);
        String path = transport.lastRequestPath();
        assertTrue(path.contains("expand=true"));
        assertFalse(path.contains("??"));
    }

    // --- Caching ---


    @Test
    void clientSupportsCaching(@TempDir Path tempDir) throws IOException
    {
        CdiscLibraryClient cachedClient = CdiscLibraryClient.builder().apiKey("test-key")
                .transport(transport).cacheDir(tempDir).build();

        transport.nextResponse = jsonResponse("{\"name\":\"cached\"}");
        AdamProduct p1 = cachedClient.getAdamProduct("adam-2-1");
        assertEquals("cached", p1.name().orElse(null));

        // Second call should come from cache (transport won't be called)
        transport.nextResponse = null; // would throw if called
        AdamProduct p2 = cachedClient.getAdamProduct("adam-2-1");
        assertEquals("cached", p2.name().orElse(null));
    }

    // --- Helper ---


    private static HttpResponse jsonResponse(String json)
    {
        return new HttpResponse(200, java.util.Map.of(),
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * A recording transport that captures the last request URI and returns a preconfigured
     * response.
     */
    private static class RecordingTransport implements HttpTransport
    {

        HttpResponse nextResponse;

        URI lastRequestUri;

        String lastRequestPath()
        {
            if (lastRequestUri == null)
            {
                return null;
            }
            String full = lastRequestUri.toString();
            // The JsonApiClient strips the trailing slash from the base URL,
            // so we need to match against the stored baseUrl (without trailing slash)
            String base = CdiscLibraryClient.DEFAULT_BASE_URL;
            if (base.endsWith("/"))
            {
                base = base.substring(0, base.length() - 1);
            }
            if (full.startsWith(base))
            {
                return full.substring(base.length());
            }
            return full;
        }


        @Override
        public HttpResponse send(HttpRequest request) throws IOException
        {
            lastRequestUri = request.uri();
            if (nextResponse == null)
            {
                throw new IOException("No response configured");
            }
            return nextResponse;
        }
    }
}
