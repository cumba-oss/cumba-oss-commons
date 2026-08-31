package net.cumba.cdisc.library.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.cumba.cdisc.library.api.model.adam.AdamProduct;
import net.cumba.cdisc.library.api.model.cdash.CdashProduct;
import net.cumba.cdisc.library.api.model.documents.Document;
import net.cumba.cdisc.library.api.model.documents.DocumentSectionList;
import net.cumba.cdisc.library.api.model.integrated.IntegratedProduct;
import net.cumba.cdisc.library.api.model.products.ProductGroup;
import net.cumba.cdisc.library.api.model.qrs.QrsInstrument;
import net.cumba.cdisc.library.api.model.search.SearchScopes;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import net.cumba.web.api.http.HttpTransport;
import net.cumba.web.api.json.JsonNodeResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Mock-based endpoint coverage tests for {@link CdiscLibraryClient}.
 *
 * <p>
 * The existing {@code CdiscLibraryClientTest} covers a handful of endpoints plus null/blank
 * validation; this complementary suite focuses on broad structural coverage of all endpoint
 * families (ADaM, SDTM, SDTM-IG, SEND-IG, CT, CDASH, CDASH-IG, integrated standards, QRS, rules,
 * documents, diff, search) so the generated request paths are exercised at least once.
 * </p>
 *
 * <p>
 * All HTTP traffic is mocked via {@link RecordingTransport}; no real network calls are made.
 * </p>
 */
class CdiscLibraryClientEndpointTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RecordingTransport transport;

    private CdiscLibraryClient client;

    @BeforeEach
    void setUp()
    {
        transport = new RecordingTransport();
        client = CdiscLibraryClient.builder().apiKey("test-key").transport(transport).build();
    }

    // --- Parameterised endpoint-path roundtrip tests ---

    /**
     * Each row is (description, expected request path, JSON response payload, runnable that invokes
     * the client). The runnable receives the configured client; the path on the captured request
     * must equal the expected path.
     */
    @FunctionalInterface
    interface ClientCall
    {

        void run(CdiscLibraryClient client) throws IOException;
    }

    static List<Arguments> endpointCalls()
    {
        return List.of(
                // Products & groups
                Arguments.of("getProductGroup", "/mdr/products/data-tabulation", "{}",
                        (ClientCall) c -> c.getProductGroup("data-tabulation")),
                Arguments.of("getProductGroupExpand", "/mdr/products/data-tabulation?expand=true",
                        "{}", (ClientCall) c -> c.getProductGroup("data-tabulation", true)),
                // ADaM
                Arguments.of("getAdamProductExpand", "/mdr/adam/adam-2-1?expand=true", "{}",
                        (ClientCall) c -> c.getAdamProduct("adam-2-1", true)),
                Arguments.of("getAdamDataStructure", "/mdr/adam/adam-2-1/datastructures/ADSL", "{}",
                        (ClientCall) c -> c.getAdamDataStructure("adam-2-1", "ADSL")),
                Arguments.of("getAdamDataStructureLinks", "/mdr/adam/adam-2-1/datastructures",
                        "{\"_links\":{\"dataStructures\":[]}}",
                        (ClientCall) c -> c.getAdamDataStructureLinks("adam-2-1")),
                Arguments.of("getAdamDataStructureLinksExpand",
                        "/mdr/adam/adam-2-1/datastructures?expand=true",
                        "{\"_links\":{\"dataStructures\":[]}}",
                        (ClientCall) c -> c.getAdamDataStructureLinks("adam-2-1", true)),
                Arguments.of("getAdamVariableSetLinks",
                        "/mdr/adam/adam-2-1/datastructures/ADSL/varsets",
                        "{\"_links\":{\"analysisVariableSets\":[]}}",
                        (ClientCall) c -> c.getAdamVariableSetLinks("adam-2-1", "ADSL")),
                Arguments.of("getAdamVariableSetLinksExpand",
                        "/mdr/adam/adam-2-1/datastructures/ADSL/varsets?expand=true",
                        "{\"_links\":{\"analysisVariableSets\":[]}}",
                        (ClientCall) c -> c.getAdamVariableSetLinks("adam-2-1", "ADSL", true)),
                Arguments.of("getAdamVariableLinks",
                        "/mdr/adam/adam-2-1/datastructures/ADSL/variables",
                        "{\"_links\":{\"analysisVariables\":[]}}",
                        (ClientCall) c -> c.getAdamVariableLinks("adam-2-1", "ADSL")),
                Arguments.of("getAdamVariable",
                        "/mdr/adam/adam-2-1/datastructures/ADSL/variables/USUBJID", "{}",
                        (ClientCall) c -> c.getAdamVariable("adam-2-1", "ADSL", "USUBJID")),
                Arguments.of("getAdamVariableSet",
                        "/mdr/adam/adam-2-1/datastructures/ADSL/varsets/VS1", "{}",
                        (ClientCall) c -> c.getAdamVariableSet("adam-2-1", "ADSL", "VS1")),
                // SDTM
                Arguments.of("getSdtmVersionExpand", "/mdr/sdtm/2-0?expand=true", "{}",
                        (ClientCall) c -> c.getSdtmVersion("sdtm", "2-0", true)),
                Arguments.of("getSdtmClass", "/mdr/sdtm/2-0/classes/Events", "{}",
                        (ClientCall) c -> c.getSdtmClass("2-0", "Events")),
                Arguments.of("getSdtmClassVariableLinks", "/mdr/sdtm/2-0/classes/Events/variables",
                        "{\"_links\":{\"classVariables\":[]}}",
                        (ClientCall) c -> c.getSdtmClassVariableLinks("2-0", "Events")),
                Arguments.of("getSdtmClassVariable",
                        "/mdr/sdtm/2-0/classes/Events/variables/DOMAIN", "{}",
                        (ClientCall) c -> c.getSdtmClassVariable("2-0", "Events", "DOMAIN")),
                Arguments.of("getSdtmClassDatasetLinks", "/mdr/sdtm/2-0/classes/Events/datasets",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getSdtmClassDatasetLinks("2-0", "Events")),
                Arguments.of("getSdtmigClassLinks", "/mdr/sdtmig/3-4/classes",
                        "{\"_links\":{\"classes\":[]}}",
                        (ClientCall) c -> c.getSdtmigClassLinks("3-4")),
                Arguments.of("getSdtmigClass", "/mdr/sdtmig/3-4/classes/Events", "{}",
                        (ClientCall) c -> c.getSdtmigClass("3-4", "Events")),
                Arguments.of("getSdtmigClassDatasetLinks",
                        "/mdr/sdtmig/3-4/classes/Events/datasets", "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getSdtmigClassDatasetLinks("3-4", "Events")),
                Arguments.of("getSdtmVariable", "/mdr/sdtmig/3-4/datasets/AE/variables/AETERM",
                        "{}", (ClientCall) c -> c.getSdtmVariable("sdtmig", "3-4", "AE", "AETERM")),
                Arguments.of("getSdtmDatasetLinks", "/mdr/sdtmig/3-4/datasets",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getSdtmDatasetLinks("sdtmig", "3-4")),
                Arguments.of("getSdtmDatasetLinksExpand", "/mdr/sdtmig/3-4/datasets?expand=true",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getSdtmDatasetLinks("sdtmig", "3-4", true)),
                Arguments.of("getSdtmDatasetVariableLinks", "/mdr/sdtmig/3-4/datasets/AE/variables",
                        "{\"_links\":{\"datasetVariables\":[]}}",
                        (ClientCall) c -> c.getSdtmDatasetVariableLinks("sdtmig", "3-4", "AE")),
                // Root (versionless) SDTM
                Arguments.of("getRootSdtmClassVariable",
                        "/mdr/root/sdtm/classes/Events/variables/DOMAIN", "{}",
                        (ClientCall) c -> c.getRootSdtmClassVariable("Events", "DOMAIN")),
                Arguments.of("getRootSdtmDatasetVariable",
                        "/mdr/root/sdtm/datasets/AE/variables/AETERM", "{}",
                        (ClientCall) c -> c.getRootSdtmDatasetVariable("AE", "AETERM")),
                Arguments.of("getRootSdtmigDatasetVariable",
                        "/mdr/root/sdtmig/datasets/AE/variables/AETERM", "{}",
                        (ClientCall) c -> c.getRootSdtmigDatasetVariable("AE", "AETERM")),
                // CT
                Arguments.of("getCtPackageExpand", "/mdr/ct/packages/sdtmct-2023-12-15?expand=true",
                        "{}", (ClientCall) c -> c.getCtPackage("sdtmct-2023-12-15", true)),
                Arguments.of("getCtCodelistLinks", "/mdr/ct/packages/sdtmct-2023-12-15/codelists",
                        "{\"_links\":{\"codelists\":[]}}",
                        (ClientCall) c -> c.getCtCodelistLinks("sdtmct-2023-12-15")),
                Arguments.of("getCtCodelistLinksExpand",
                        "/mdr/ct/packages/sdtmct-2023-12-15/codelists?expand=true",
                        "{\"_links\":{\"codelists\":[]}}",
                        (ClientCall) c -> c.getCtCodelistLinks("sdtmct-2023-12-15", true)),
                Arguments.of("getCtCodelist", "/mdr/ct/packages/sdtmct-2023-12-15/codelists/C66729",
                        "{}", (ClientCall) c -> c.getCtCodelist("sdtmct-2023-12-15", "C66729")),
                Arguments.of("getCtTermLinks",
                        "/mdr/ct/packages/sdtmct-2023-12-15/codelists/C66729/terms",
                        "{\"_links\":{\"terms\":[]}}",
                        (ClientCall) c -> c.getCtTermLinks("sdtmct-2023-12-15", "C66729")),
                Arguments.of("getCtTerm",
                        "/mdr/ct/packages/sdtmct-2023-12-15/codelists/C66729/terms/C29848", "{}",
                        (ClientCall) c -> c.getCtTerm("sdtmct-2023-12-15", "C66729", "C29848")),
                Arguments.of("getRootCtCodelist", "/mdr/root/ct/sdtmct/codelists/C66729", "{}",
                        (ClientCall) c -> c.getRootCtCodelist("sdtmct", "C66729")),
                Arguments.of("getRootCtTerm", "/mdr/root/ct/sdtmct/codelists/C66729/terms/C29848",
                        "{}", (ClientCall) c -> c.getRootCtTerm("sdtmct", "C66729", "C29848")),
                // CDASH
                Arguments.of("getCdashVersion", "/mdr/cdash/cdash-2-2", "{}",
                        (ClientCall) c -> c.getCdashVersion("cdash-2-2")),
                Arguments.of("getCdashVersionExpand", "/mdr/cdash/cdash-2-2?expand=true", "{}",
                        (ClientCall) c -> c.getCdashVersion("cdash-2-2", true)),
                Arguments.of("getCdashClassLinks", "/mdr/cdash/cdash-2-2/classes",
                        "{\"_links\":{\"classes\":[]}}",
                        (ClientCall) c -> c.getCdashClassLinks("cdash-2-2")),
                Arguments.of("getCdashClass", "/mdr/cdash/cdash-2-2/classes/Events", "{}",
                        (ClientCall) c -> c.getCdashClass("cdash-2-2", "Events")),
                Arguments.of("getCdashClassDomainLinks",
                        "/mdr/cdash/cdash-2-2/classes/Events/domains",
                        "{\"_links\":{\"domains\":[]}}",
                        (ClientCall) c -> c.getCdashClassDomainLinks("cdash-2-2", "Events")),
                Arguments.of("getCdashClassField",
                        "/mdr/cdash/cdash-2-2/classes/Events/fields/AETERM", "{}",
                        (ClientCall) c -> c.getCdashClassField("cdash-2-2", "Events", "AETERM")),
                Arguments.of("getCdashDomainLinks", "/mdr/cdash/cdash-2-2/domains",
                        "{\"_links\":{\"domains\":[]}}",
                        (ClientCall) c -> c.getCdashDomainLinks("cdash-2-2")),
                Arguments.of("getCdashDomain", "/mdr/cdash/cdash-2-2/domains/AE", "{}",
                        (ClientCall) c -> c.getCdashDomain("cdash-2-2", "AE")),
                Arguments.of("getCdashDomainFieldLinks", "/mdr/cdash/cdash-2-2/domains/AE/fields",
                        "{\"_links\":{\"fields\":[]}}",
                        (ClientCall) c -> c.getCdashDomainFieldLinks("cdash-2-2", "AE")),
                Arguments.of("getCdashDomainField", "/mdr/cdash/cdash-2-2/domains/AE/fields/AETERM",
                        "{}", (ClientCall) c -> c.getCdashDomainField("cdash-2-2", "AE", "AETERM")),
                Arguments.of("getRootCdashClassField",
                        "/mdr/root/cdash/classes/Events/fields/AETERM", "{}",
                        (ClientCall) c -> c.getRootCdashClassField("Events", "AETERM")),
                Arguments.of("getRootCdashDomainField", "/mdr/root/cdash/domains/AE/fields/AETERM",
                        "{}", (ClientCall) c -> c.getRootCdashDomainField("AE", "AETERM")),
                // CDASHIG
                Arguments.of("getCdashigVersion", "/mdr/cdashig/cdashig-2-2", "{}",
                        (ClientCall) c -> c.getCdashigVersion("cdashig-2-2")),
                Arguments.of("getCdashigVersionExpand", "/mdr/cdashig/cdashig-2-2?expand=true",
                        "{}", (ClientCall) c -> c.getCdashigVersion("cdashig-2-2", true)),
                Arguments.of("getCdashigClassLinks", "/mdr/cdashig/cdashig-2-2/classes",
                        "{\"_links\":{\"classes\":[]}}",
                        (ClientCall) c -> c.getCdashigClassLinks("cdashig-2-2")),
                Arguments.of("getCdashigClass", "/mdr/cdashig/cdashig-2-2/classes/Events", "{}",
                        (ClientCall) c -> c.getCdashigClass("cdashig-2-2", "Events")),
                Arguments.of("getCdashigClassDomainLinks",
                        "/mdr/cdashig/cdashig-2-2/classes/Events/domains",
                        "{\"_links\":{\"domains\":[]}}",
                        (ClientCall) c -> c.getCdashigClassDomainLinks("cdashig-2-2", "Events")),
                Arguments.of("getCdashigClassScenarioLinks",
                        "/mdr/cdashig/cdashig-2-2/classes/Events/scenarios",
                        "{\"_links\":{\"scenarios\":[]}}",
                        (ClientCall) c -> c.getCdashigClassScenarioLinks("cdashig-2-2", "Events")),
                Arguments.of("getCdashigDomainLinks", "/mdr/cdashig/cdashig-2-2/domains",
                        "{\"_links\":{\"domains\":[]}}",
                        (ClientCall) c -> c.getCdashigDomainLinks("cdashig-2-2")),
                Arguments.of("getCdashigDomain", "/mdr/cdashig/cdashig-2-2/domains/AE", "{}",
                        (ClientCall) c -> c.getCdashigDomain("cdashig-2-2", "AE")),
                Arguments.of("getCdashigDomainFieldLinks",
                        "/mdr/cdashig/cdashig-2-2/domains/AE/fields",
                        "{\"_links\":{\"fields\":[]}}",
                        (ClientCall) c -> c.getCdashigDomainFieldLinks("cdashig-2-2", "AE")),
                Arguments.of("getCdashigDomainField",
                        "/mdr/cdashig/cdashig-2-2/domains/AE/fields/AETERM", "{}",
                        (ClientCall) c -> c.getCdashigDomainField("cdashig-2-2", "AE", "AETERM")),
                Arguments.of("getCdashigScenarioLinks", "/mdr/cdashig/cdashig-2-2/scenarios",
                        "{\"_links\":{\"scenarios\":[]}}",
                        (ClientCall) c -> c.getCdashigScenarioLinks("cdashig-2-2")),
                Arguments.of("getCdashigScenario", "/mdr/cdashig/cdashig-2-2/scenarios/sc1", "{}",
                        (ClientCall) c -> c.getCdashigScenario("cdashig-2-2", "sc1")),
                Arguments.of("getCdashigScenarioFieldLinks",
                        "/mdr/cdashig/cdashig-2-2/scenarios/sc1/fields",
                        "{\"_links\":{\"fields\":[]}}",
                        (ClientCall) c -> c.getCdashigScenarioFieldLinks("cdashig-2-2", "sc1")),
                Arguments.of("getCdashigScenarioField",
                        "/mdr/cdashig/cdashig-2-2/scenarios/sc1/fields/AETERM", "{}",
                        (ClientCall) c -> c.getCdashigScenarioField("cdashig-2-2", "sc1",
                                "AETERM")),
                Arguments.of("getRootCdashigDomainField",
                        "/mdr/root/cdashig/domains/AE/fields/AETERM", "{}",
                        (ClientCall) c -> c.getRootCdashigDomainField("AE", "AETERM")),
                Arguments.of("getRootCdashigScenarioField",
                        "/mdr/root/cdashig/scenarios/sc1/fields/AETERM", "{}",
                        (ClientCall) c -> c.getRootCdashigScenarioField("sc1", "AETERM")),
                // SENDIG
                Arguments.of("getSendigVersion", "/mdr/sendig/3-1-1", "{}",
                        (ClientCall) c -> c.getSendigVersion("3-1-1")),
                Arguments.of("getSendigVersionExpand", "/mdr/sendig/3-1-1?expand=true", "{}",
                        (ClientCall) c -> c.getSendigVersion("3-1-1", true)),
                Arguments.of("getSendigClassLinks", "/mdr/sendig/3-1-1/classes",
                        "{\"_links\":{\"classes\":[]}}",
                        (ClientCall) c -> c.getSendigClassLinks("3-1-1")),
                Arguments.of("getSendigClass", "/mdr/sendig/3-1-1/classes/Events", "{}",
                        (ClientCall) c -> c.getSendigClass("3-1-1", "Events")),
                Arguments.of("getSendigClassDatasetLinks",
                        "/mdr/sendig/3-1-1/classes/Events/datasets",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getSendigClassDatasetLinks("3-1-1", "Events")),
                Arguments.of("getSendigDatasetLinks", "/mdr/sendig/3-1-1/datasets",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getSendigDatasetLinks("3-1-1")),
                Arguments.of("getSendigDataset", "/mdr/sendig/3-1-1/datasets/AE", "{}",
                        (ClientCall) c -> c.getSendigDataset("3-1-1", "AE")),
                Arguments.of("getSendigDatasetVariableLinks",
                        "/mdr/sendig/3-1-1/datasets/AE/variables",
                        "{\"_links\":{\"datasetVariables\":[]}}",
                        (ClientCall) c -> c.getSendigDatasetVariableLinks("3-1-1", "AE")),
                Arguments.of("getSendigVariable", "/mdr/sendig/3-1-1/datasets/AE/variables/AETERM",
                        "{}", (ClientCall) c -> c.getSendigVariable("3-1-1", "AE", "AETERM")),
                Arguments.of("getRootSendigVariable",
                        "/mdr/root/sendig/datasets/AE/variables/AETERM", "{}",
                        (ClientCall) c -> c.getRootSendigVariable("AE", "AETERM")),
                // Integrated / SDTM
                Arguments.of("getIntegratedSdtm", "/mdr/integrated/sdtmig/3-4/sdtm", "{}",
                        (ClientCall) c -> c.getIntegratedSdtm("sdtmig", "3-4")),
                Arguments.of("getIntegratedSdtmClassLinks",
                        "/mdr/integrated/sdtmig/3-4/sdtm/classes", "{\"_links\":{\"classes\":[]}}",
                        (ClientCall) c -> c.getIntegratedSdtmClassLinks("sdtmig", "3-4")),
                Arguments.of("getIntegratedSdtmClass",
                        "/mdr/integrated/sdtmig/3-4/sdtm/classes/Events", "{}",
                        (ClientCall) c -> c.getIntegratedSdtmClass("sdtmig", "3-4", "Events")),
                Arguments.of("getIntegratedSdtmClassDatasetLinks",
                        "/mdr/integrated/sdtmig/3-4/sdtm/classes/Events/datasets",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getIntegratedSdtmClassDatasetLinks("sdtmig", "3-4",
                                "Events")),
                Arguments.of("getIntegratedSdtmDatasetLinks",
                        "/mdr/integrated/sdtmig/3-4/sdtm/datasets",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getIntegratedSdtmDatasetLinks("sdtmig", "3-4")),
                Arguments.of("getIntegratedSdtmDataset",
                        "/mdr/integrated/sdtmig/3-4/sdtm/datasets/AE", "{}",
                        (ClientCall) c -> c.getIntegratedSdtmDataset("sdtmig", "3-4", "AE")),
                Arguments.of("getIntegratedSdtmDatasetVariableLinks",
                        "/mdr/integrated/sdtmig/3-4/sdtm/datasets/AE/variables",
                        "{\"_links\":{\"datasetVariables\":[]}}",
                        (ClientCall) c -> c.getIntegratedSdtmDatasetVariableLinks("sdtmig", "3-4",
                                "AE")),
                Arguments.of("getIntegratedSdtmVariable",
                        "/mdr/integrated/sdtmig/3-4/sdtm/datasets/AE/variables/AETERM", "{}",
                        (ClientCall) c -> c.getIntegratedSdtmVariable("sdtmig", "3-4", "AE",
                                "AETERM")),
                Arguments.of("getRootIntegratedSdtmVariable",
                        "/mdr/root/integrated/sdtmig/sdtm/datasets/AE/variables/AETERM", "{}",
                        (ClientCall) c -> c.getRootIntegratedSdtmVariable("sdtmig", "AE",
                                "AETERM")),
                // Integrated / SEND
                Arguments.of("getIntegratedSend", "/mdr/integrated/sendig/3-1-1/send", "{}",
                        (ClientCall) c -> c.getIntegratedSend("sendig", "3-1-1")),
                Arguments.of("getIntegratedSendClassLinks",
                        "/mdr/integrated/sendig/3-1-1/send/classes",
                        "{\"_links\":{\"classes\":[]}}",
                        (ClientCall) c -> c.getIntegratedSendClassLinks("sendig", "3-1-1")),
                Arguments.of("getIntegratedSendClass",
                        "/mdr/integrated/sendig/3-1-1/send/classes/Events", "{}",
                        (ClientCall) c -> c.getIntegratedSendClass("sendig", "3-1-1", "Events")),
                Arguments.of("getIntegratedSendClassDatasetLinks",
                        "/mdr/integrated/sendig/3-1-1/send/classes/Events/datasets",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getIntegratedSendClassDatasetLinks("sendig", "3-1-1",
                                "Events")),
                Arguments.of("getIntegratedSendDatasetLinks",
                        "/mdr/integrated/sendig/3-1-1/send/datasets",
                        "{\"_links\":{\"datasets\":[]}}",
                        (ClientCall) c -> c.getIntegratedSendDatasetLinks("sendig", "3-1-1")),
                Arguments.of("getIntegratedSendDataset",
                        "/mdr/integrated/sendig/3-1-1/send/datasets/AE", "{}",
                        (ClientCall) c -> c.getIntegratedSendDataset("sendig", "3-1-1", "AE")),
                Arguments.of("getIntegratedSendDatasetVariableLinks",
                        "/mdr/integrated/sendig/3-1-1/send/datasets/AE/variables",
                        "{\"_links\":{\"datasetVariables\":[]}}",
                        (ClientCall) c -> c.getIntegratedSendDatasetVariableLinks("sendig", "3-1-1",
                                "AE")),
                Arguments.of("getIntegratedSendVariable",
                        "/mdr/integrated/sendig/3-1-1/send/datasets/AE/variables/AETERM", "{}",
                        (ClientCall) c -> c.getIntegratedSendVariable("sendig", "3-1-1", "AE",
                                "AETERM")),
                Arguments.of("getRootIntegratedSendVariable",
                        "/mdr/root/integrated/sendig/send/datasets/AE/variables/AETERM", "{}",
                        (ClientCall) c -> c.getRootIntegratedSendVariable("sendig", "AE",
                                "AETERM")),
                // Integrated / ADaM
                Arguments.of("getIntegratedAdam", "/mdr/integrated/adam/1-3/adam", "{}",
                        (ClientCall) c -> c.getIntegratedAdam("adam", "1-3")),
                Arguments.of("getIntegratedAdamDataStructureLinks",
                        "/mdr/integrated/adam/1-3/adam/datastructures",
                        "{\"_links\":{\"dataStructures\":[]}}",
                        (ClientCall) c -> c.getIntegratedAdamDataStructureLinks("adam", "1-3")),
                Arguments.of("getIntegratedAdamDataStructure",
                        "/mdr/integrated/adam/1-3/adam/datastructures/ADSL", "{}",
                        (ClientCall) c -> c.getIntegratedAdamDataStructure("adam", "1-3", "ADSL")),
                Arguments.of("getIntegratedAdamVariableLinks",
                        "/mdr/integrated/adam/1-3/adam/datastructures/ADSL/variables",
                        "{\"_links\":{\"analysisVariables\":[]}}",
                        (ClientCall) c -> c.getIntegratedAdamVariableLinks("adam", "1-3", "ADSL")),
                Arguments.of("getIntegratedAdamVariable",
                        "/mdr/integrated/adam/1-3/adam/datastructures/ADSL/variables/USUBJID", "{}",
                        (ClientCall) c -> c.getIntegratedAdamVariable("adam", "1-3", "ADSL",
                                "USUBJID")),
                Arguments.of("getIntegratedAdamVariableSetLinks",
                        "/mdr/integrated/adam/1-3/adam/datastructures/ADSL/varsets",
                        "{\"_links\":{\"analysisVariableSets\":[]}}",
                        (ClientCall) c -> c.getIntegratedAdamVariableSetLinks("adam", "1-3",
                                "ADSL")),
                Arguments.of("getIntegratedAdamVariableSet",
                        "/mdr/integrated/adam/1-3/adam/datastructures/ADSL/varsets/VS1", "{}",
                        (ClientCall) c -> c.getIntegratedAdamVariableSet("adam", "1-3", "ADSL",
                                "VS1")),
                // Integrated / CDASH
                Arguments.of("getIntegratedCdash", "/mdr/integrated/cdashig/2-3/cdash", "{}",
                        (ClientCall) c -> c.getIntegratedCdash("cdashig", "2-3")),
                Arguments.of("getIntegratedCdashClassLinks",
                        "/mdr/integrated/cdashig/2-3/cdash/classes",
                        "{\"_links\":{\"classes\":[]}}",
                        (ClientCall) c -> c.getIntegratedCdashClassLinks("cdashig", "2-3")),
                Arguments.of("getIntegratedCdashClass",
                        "/mdr/integrated/cdashig/2-3/cdash/classes/Events", "{}",
                        (ClientCall) c -> c.getIntegratedCdashClass("cdashig", "2-3", "Events")),
                Arguments.of("getIntegratedCdashClassDomainLinks",
                        "/mdr/integrated/cdashig/2-3/cdash/classes/Events/domains",
                        "{\"_links\":{\"domains\":[]}}",
                        (ClientCall) c -> c.getIntegratedCdashClassDomainLinks("cdashig", "2-3",
                                "Events")),
                Arguments.of("getIntegratedCdashClassScenarioLinks",
                        "/mdr/integrated/cdashig/2-3/cdash/classes/Events/scenarios",
                        "{\"_links\":{\"scenarios\":[]}}",
                        (ClientCall) c -> c.getIntegratedCdashClassScenarioLinks("cdashig", "2-3",
                                "Events")),
                Arguments.of("getIntegratedCdashDomainLinks",
                        "/mdr/integrated/cdashig/2-3/cdash/domains",
                        "{\"_links\":{\"domains\":[]}}",
                        (ClientCall) c -> c.getIntegratedCdashDomainLinks("cdashig", "2-3")),
                Arguments.of("getIntegratedCdashDomain",
                        "/mdr/integrated/cdashig/2-3/cdash/domains/AE", "{}",
                        (ClientCall) c -> c.getIntegratedCdashDomain("cdashig", "2-3", "AE")),
                Arguments.of("getIntegratedCdashDomainFieldLinks",
                        "/mdr/integrated/cdashig/2-3/cdash/domains/AE/fields",
                        "{\"_links\":{\"fields\":[]}}",
                        (ClientCall) c -> c.getIntegratedCdashDomainFieldLinks("cdashig", "2-3",
                                "AE")),
                Arguments.of("getIntegratedCdashDomainField",
                        "/mdr/integrated/cdashig/2-3/cdash/domains/AE/fields/AETERM", "{}",
                        (ClientCall) c -> c.getIntegratedCdashDomainField("cdashig", "2-3", "AE",
                                "AETERM")),
                Arguments.of("getRootIntegratedCdashDomainField",
                        "/mdr/root/integrated/cdashig/cdash/domains/AE/fields/AETERM", "{}",
                        (ClientCall) c -> c.getRootIntegratedCdashDomainField("cdashig", "AE",
                                "AETERM")),
                Arguments.of("getIntegratedCdashScenarioLinks",
                        "/mdr/integrated/cdashig/2-3/cdash/scenarios",
                        "{\"_links\":{\"scenarios\":[]}}",
                        (ClientCall) c -> c.getIntegratedCdashScenarioLinks("cdashig", "2-3")),
                Arguments.of("getIntegratedCdashScenario",
                        "/mdr/integrated/cdashig/2-3/cdash/scenarios/sc1", "{}",
                        (ClientCall) c -> c.getIntegratedCdashScenario("cdashig", "2-3", "sc1")),
                Arguments.of("getIntegratedCdashScenarioFieldLinks",
                        "/mdr/integrated/cdashig/2-3/cdash/scenarios/sc1/fields",
                        "{\"_links\":{\"fields\":[]}}",
                        (ClientCall) c -> c.getIntegratedCdashScenarioFieldLinks("cdashig", "2-3",
                                "sc1")),
                Arguments.of("getIntegratedCdashScenarioField",
                        "/mdr/integrated/cdashig/2-3/cdash/scenarios/sc1/fields/AETERM", "{}",
                        (ClientCall) c -> c.getIntegratedCdashScenarioField("cdashig", "2-3", "sc1",
                                "AETERM")),
                Arguments.of("getRootIntegratedCdashScenarioField",
                        "/mdr/root/integrated/cdashig/cdash/scenarios/sc1/fields/AETERM", "{}",
                        (ClientCall) c -> c.getRootIntegratedCdashScenarioField("cdashig", "sc1",
                                "AETERM")),
                // QRS
                Arguments.of("getQrsInstrument", "/mdr/qrs/instruments/PHQ-9/versions/1-0", "{}",
                        (ClientCall) c -> c.getQrsInstrument("PHQ-9", "1-0")),
                Arguments.of("getQrsResponseGroupLinks",
                        "/mdr/qrs/instruments/PHQ-9/versions/1-0/responseGroups",
                        "{\"_links\":{\"responseGroups\":[]}}",
                        (ClientCall) c -> c.getQrsResponseGroupLinks("PHQ-9", "1-0")),
                Arguments.of("getQrsResponseGroup",
                        "/mdr/qrs/instruments/PHQ-9/versions/1-0/responseGroups/RG1", "{}",
                        (ClientCall) c -> c.getQrsResponseGroup("PHQ-9", "1-0", "RG1")),
                Arguments.of("getQrsItemLinks", "/mdr/qrs/instruments/PHQ-9/versions/1-0/items",
                        "{\"_links\":{\"items\":[]}}",
                        (ClientCall) c -> c.getQrsItemLinks("PHQ-9", "1-0")),
                Arguments.of("getQrsItem", "/mdr/qrs/instruments/PHQ-9/versions/1-0/items/q1", "{}",
                        (ClientCall) c -> c.getQrsItem("PHQ-9", "1-0", "q1")),
                Arguments.of("getRootQrsInstrument", "/mdr/root/qrs/instruments/PHQ-9", "{}",
                        (ClientCall) c -> c.getRootQrsInstrument("PHQ-9")),
                // Rules
                Arguments.of("getRuleCatalogs", "/mdr/rules", "{}",
                        (ClientCall) c -> c.getRuleCatalogs()),
                Arguments.of("getRuleCatalogsExpand", "/mdr/rules?expand=true", "{}",
                        (ClientCall) c -> c.getRuleCatalogs(true)),
                Arguments.of("getRule", "/mdr/rules/sdtmig/3-4/rule/uuid-abc", "{}",
                        (ClientCall) c -> c.getRule("sdtmig", "3-4", "uuid-abc")),
                // Integrated versionless
                Arguments.of("getIntegratedVersion", "/mdr/integrated/sdtmig/3-4", "{}",
                        (ClientCall) c -> c.getIntegratedVersion("sdtmig", "3-4")),
                // Documents
                Arguments.of("getDocumentSections", "/mdr/documents/sdtmig/3-4/sections", "{}",
                        (ClientCall) c -> c.getDocumentSections("sdtmig", "3-4")),
                Arguments.of("getDocumentStructureSections",
                        "/mdr/documents/sdtmig/3-4/AE/sections", "{}",
                        (ClientCall) c -> c.getDocumentStructureSections("sdtmig", "3-4", "AE")),
                Arguments.of("getDocumentStructureSection",
                        "/mdr/documents/sdtmig/3-4/AE/sections/6.1", "{}",
                        (ClientCall) c -> c.getDocumentStructureSection("sdtmig", "3-4", "AE",
                                "6.1")),
                Arguments.of("getDocumentUseCases", "/mdr/documents/sdtmig/3-4/usecases", "{}",
                        (ClientCall) c -> c.getDocumentUseCases("sdtmig", "3-4")),
                Arguments.of("getDocumentUseCaseSections",
                        "/mdr/documents/sdtmig/3-4/usecases/CLIN/sections", "{}",
                        (ClientCall) c -> c.getDocumentUseCaseSections("sdtmig", "3-4", "CLIN")),
                Arguments.of("getDocumentUseCaseSection",
                        "/mdr/documents/sdtmig/3-4/usecases/CLIN/sections/6.1", "{}",
                        (ClientCall) c -> c.getDocumentUseCaseSection("sdtmig", "3-4", "CLIN",
                                "6.1")),
                Arguments.of("getIntegratedDocumentSections",
                        "/mdr/documents/integrated/sdtmig/3-4/sdtm/sections", "{}",
                        (ClientCall) c -> c.getIntegratedDocumentSections("sdtmig", "3-4", "sdtm")),
                Arguments.of("getIntegratedDocumentStructureSections",
                        "/mdr/documents/integrated/sdtmig/3-4/sdtm/AE/sections", "{}",
                        (ClientCall) c -> c.getIntegratedDocumentStructureSections("sdtmig", "3-4",
                                "sdtm", "AE")),
                Arguments.of("getIntegratedDocumentStructureSection",
                        "/mdr/documents/integrated/sdtmig/3-4/sdtm/AE/sections/6.1", "{}",
                        (ClientCall) c -> c.getIntegratedDocumentStructureSection("sdtmig", "3-4",
                                "sdtm", "AE", "6.1")),
                Arguments.of("getIntegratedDocumentUseCaseSection",
                        "/mdr/documents/integrated/sdtmig/3-4/sdtm/usecases/CLIN/sections/6.1",
                        "{}",
                        (ClientCall) c -> c.getIntegratedDocumentUseCaseSection("sdtmig", "3-4",
                                "sdtm", "CLIN", "6.1")),
                // Search
                Arguments.of("getSearchScopes", "/mdr/search/scopes", "{}",
                        (ClientCall) c -> c.getSearchScopes()),
                Arguments.of("suggest", "/mdr/suggest?q=test", "{}",
                        (ClientCall) c -> c.suggest("test")),
                Arguments.of("searchImplementedBy",
                        "/mdr/search/implementedBy?href=%2Fmdr%2Fadam%2Fadam-2-1", "{}",
                        (ClientCall) c -> c.searchImplementedBy("/mdr/adam/adam-2-1")));
    }


    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("endpointCalls")
    void endpointHitsExpectedPath(String description, String expectedPath, String responseJson,
            ClientCall call)
        throws IOException
    {
        // description is required by @MethodSource for the display-name slot.
        assertNotNull(description);
        transport.nextResponse = jsonResponse(responseJson);
        call.run(client);
        assertEquals(expectedPath, transport.lastRequestPath());
    }

    // --- Non-parameterised tests for behaviours that don't fit a single roundtrip ---


    @Test
    void searchEncodesQueryParameters() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"_links\":{\"searchResults\":[]}}");
        client.search("hello world");
        String path = transport.lastRequestPath();
        assertTrue(path.startsWith("/mdr/search?q="));
        assertTrue(path.contains("hello%20world") || path.contains("hello+world"));
    }


    @Test
    void searchInScopeHitsCorrectPath() throws IOException
    {
        transport.nextResponse = jsonResponse("{}");
        client.searchInScope("adam");
        assertEquals("/mdr/search/scopes/adam", transport.lastRequestPath());
    }


    @Test
    void followLinkUsesHref() throws IOException
    {
        transport.nextResponse = jsonResponse(
                "{\"_links\":{\"self\":{\"href\":\"/mdr/adam/adam-2-1\"}}}");
        ApiResource root = client.get("/mdr/products", ApiResource.class);
        Link self = root.getLink("self").orElseThrow();

        transport.nextResponse = jsonResponse("{\"name\":\"ADaM 2.1\"}");
        AdamProduct followed = client.follow(self, AdamProduct.class);
        assertEquals("/mdr/adam/adam-2-1", transport.lastRequestPath());
        assertEquals("ADaM 2.1", followed.name().orElse(null));

        // Untyped follow returns plain ApiResource.
        transport.nextResponse = jsonResponse("{\"x\":1}");
        ApiResource res = client.follow(self);
        assertEquals("/mdr/adam/adam-2-1", transport.lastRequestPath());
        assertNotNull(res);
    }


    @Test
    void followRejectsNullLink()
    {
        assertThrows(NullPointerException.class, () -> client.follow(null));
    }


    @Test
    void followRejectsLinkWithoutHref() throws IOException
    {
        JsonNode emptyLinkNode = MAPPER.readTree("{}");
        Link linkNoHref = JsonNodeResource.of(emptyLinkNode, Link.class);
        assertThrows(IOException.class, () -> client.follow(linkNoHref));
        assertThrows(IOException.class, () -> client.follow(linkNoHref, AdamProduct.class));
    }


    @Test
    void getDocumentReturnsTypedResource() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"id\":\"doc-1\",\"title\":\"AE\"}");
        Document d = client.getDocument("doc-1");
        assertEquals("/mdr/documents/doc-1", transport.lastRequestPath());
        assertEquals("doc-1", d.id().orElse(null));
        assertEquals("AE", d.title().orElse(null));
    }


    @Test
    void getDocumentSectionsRoundtrip() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"sections\":[\"6.1\",\"6.2\"]}");
        DocumentSectionList sl = client.getDocumentSections("sdtmig", "3-4");
        assertEquals("/mdr/documents/sdtmig/3-4/sections", transport.lastRequestPath());
        assertEquals(List.of("6.1", "6.2"), sl.sections());
    }


    @Test
    void getProductGroupRoundtrip() throws IOException
    {
        transport.nextResponse = jsonResponse(
                "{\"_links\":{\"adam\":[{\"href\":\"/mdr/adam/adam-2-1\"}]}}");
        ProductGroup pg = client.getProductGroup("data-analysis");
        assertEquals("/mdr/products/data-analysis", transport.lastRequestPath());
        assertEquals(1, pg.adamLinks().size());
    }


    @Test
    void getQrsInstrumentRoundtrip() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"name\":\"PHQ-9\",\"version\":\"1-0\"}");
        QrsInstrument qi = client.getQrsInstrument("PHQ-9", "1-0");
        assertEquals("/mdr/qrs/instruments/PHQ-9/versions/1-0", transport.lastRequestPath());
        assertEquals("PHQ-9", qi.name().orElse(null));
    }


    @Test
    void getCdashVersionRoundtrip() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"name\":\"CDASH\",\"version\":\"2-2\"}");
        CdashProduct p = client.getCdashVersion("cdash-2-2");
        assertEquals("/mdr/cdash/cdash-2-2", transport.lastRequestPath());
        assertEquals("CDASH", p.name().orElse(null));
    }


    @Test
    void getIntegratedVersionRoundtrip() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"name\":\"SDTM-IG\",\"version\":\"3-4\"}");
        IntegratedProduct ip = client.getIntegratedVersion("sdtmig", "3-4");
        assertEquals("/mdr/integrated/sdtmig/3-4", transport.lastRequestPath());
        assertEquals("SDTM-IG", ip.name().orElse(null));
    }


    @Test
    void getSearchScopesRoundtrip() throws IOException
    {
        transport.nextResponse = jsonResponse("{\"scopes\":[\"adam\",\"sdtm\"]}");
        SearchScopes ss = client.getSearchScopes();
        assertEquals("/mdr/search/scopes", transport.lastRequestPath());
        assertEquals(List.of("adam", "sdtm"), ss.scopes());
    }

    // --- Blank-argument rejection (complements null checks already in CdiscLibraryClientTest) ---


    @Test
    void blankArgumentsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> client.getProductGroup(""));
        assertThrows(IllegalArgumentException.class, () -> client.getCdashVersion("  "));
        assertThrows(IllegalArgumentException.class, () -> client.getSendigVersion(""));
        assertThrows(IllegalArgumentException.class, () -> client.getDocument(""));
        assertThrows(IllegalArgumentException.class, () -> client.search("  "));
        assertThrows(IllegalArgumentException.class, () -> client.suggest(""));
        assertThrows(IllegalArgumentException.class, () -> client.searchInScope(""));
        assertThrows(IllegalArgumentException.class, () -> client.searchImplementedBy(""));
        assertThrows(IllegalArgumentException.class, () -> client.getQrsItem("", "1-0", "q1"));
        assertThrows(IllegalArgumentException.class, () -> client.getRootQrsInstrument(""));
    }

    // --- Builder behaviour (extra coverage) ---


    @Test
    void baseUrlHonouredOnRequest() throws IOException
    {
        CdiscLibraryClient custom = CdiscLibraryClient.builder().apiKey("k")
                .baseUrl("https://custom.example.com/api").transport(transport).build();
        transport.nextResponse = jsonResponse("{}");
        custom.getAbout();
        assertTrue(
                transport.lastRequestUri.toString().startsWith("https://custom.example.com/api"));
    }

    // --- Helpers ---


    private static HttpResponse jsonResponse(String json)
    {
        return new HttpResponse(200, java.util.Map.of(),
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    /** Recording transport: captures the most recent request and returns a canned response. */
    static class RecordingTransport implements HttpTransport
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
            String base = CdiscLibraryClient.DEFAULT_BASE_URL;
            if (base.endsWith("/"))
            {
                base = base.substring(0, base.length() - 1);
            }
            return full.startsWith(base) ? full.substring(base.length()) : full;
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
