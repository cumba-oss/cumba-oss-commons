package net.cumba.cdisc.library.api.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.cumba.cdisc.library.api.model.adam.AdamDataStructure;
import net.cumba.cdisc.library.api.model.adam.AdamProduct;
import net.cumba.cdisc.library.api.model.adam.AdamVariable;
import net.cumba.cdisc.library.api.model.adam.AdamVariableSet;
import net.cumba.cdisc.library.api.model.cdash.CdashClass;
import net.cumba.cdisc.library.api.model.cdash.CdashDomain;
import net.cumba.cdisc.library.api.model.cdash.CdashField;
import net.cumba.cdisc.library.api.model.cdash.CdashProduct;
import net.cumba.cdisc.library.api.model.cdash.CdashScenario;
import net.cumba.cdisc.library.api.model.ct.CtCodelist;
import net.cumba.cdisc.library.api.model.ct.CtPackage;
import net.cumba.cdisc.library.api.model.ct.CtPackageList;
import net.cumba.cdisc.library.api.model.ct.CtTerm;
import net.cumba.cdisc.library.api.model.diff.DiffResult;
import net.cumba.cdisc.library.api.model.documents.Document;
import net.cumba.cdisc.library.api.model.documents.DocumentList;
import net.cumba.cdisc.library.api.model.documents.DocumentSectionList;
import net.cumba.cdisc.library.api.model.documents.UseCaseList;
import net.cumba.cdisc.library.api.model.integrated.IntegratedProduct;
import net.cumba.cdisc.library.api.model.meta.About;
import net.cumba.cdisc.library.api.model.meta.Maintenance;
import net.cumba.cdisc.library.api.model.products.LastUpdated;
import net.cumba.cdisc.library.api.model.products.ProductGroup;
import net.cumba.cdisc.library.api.model.products.Products;
import net.cumba.cdisc.library.api.model.qrs.QrsInstrument;
import net.cumba.cdisc.library.api.model.qrs.QrsItem;
import net.cumba.cdisc.library.api.model.qrs.QrsResponseGroup;
import net.cumba.cdisc.library.api.model.rules.Rule;
import net.cumba.cdisc.library.api.model.rules.RulePackage;
import net.cumba.cdisc.library.api.model.sdtm.SdtmClass;
import net.cumba.cdisc.library.api.model.sdtm.SdtmDataset;
import net.cumba.cdisc.library.api.model.sdtm.SdtmProduct;
import net.cumba.cdisc.library.api.model.sdtm.SdtmVariable;
import net.cumba.cdisc.library.api.model.search.SearchResult;
import net.cumba.cdisc.library.api.model.search.SearchScopes;
import net.cumba.datatable.help.CDT;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import net.cumba.web.api.cache.ApiCache;
import net.cumba.web.api.cache.GzipFileApiCache;
import net.cumba.web.api.http.HttpTransport;
import net.cumba.web.api.http.JdkHttpTransport;
import net.cumba.web.api.json.JsonApiClient;
import org.jspecify.annotations.Nullable;

/**
 * Client for the CDISC Library API ({@code https://api.library.cdisc.org/api}).
 *
 * <p>
 * Provides typed access to CDISC Library endpoints. Requires an API key which is sent as the
 * {@code api-key} header on every request.
 * </p>
 *
 * <p>
 * Usage:
 *
 * <pre>
 * CdiscLibraryClient cdisc = CdiscLibraryClient.builder().apiKey("your-api-key")
 *         .cacheDir(Path.of("cache")).build();
 *
 * // List available products
 * Products products = cdisc.getProducts();
 * products.adamLinks().forEach(link -> System.out.println(link.title()));
 *
 * // Get a specific ADaM version
 * AdamProduct adam21 = cdisc.getAdamProduct("adam-2-1");
 *
 * // Get an SDTM-IG dataset
 * SdtmDataset ae = cdisc.getSdtmDataset("sdtmig", "3-4", "AE");
 *
 * // Follow any HATEOAS link from a response
 * AdamDataStructure ds = cdisc.follow(link, AdamDataStructure.class);
 * </pre>
 */
public class CdiscLibraryClient extends JsonApiClient
{

    public static final String ENV_CDISC_API_KEY = "CDISC_API_KEY";

    public static final String SP_CDISC_API_KEY = "cdisc.library.api.key";

    public static final String ENV_CDISC_API_URL = "CDISC_API_URL";

    public static final String SP_CDISC_API_URL = "cdisc.library.api.url";

    public static final String ENV_CDISC_API_CACHE = "CDISC_API_CACHE";

    public static final String SP_CDISC_API_CACHE = "cdisc.library.api.cache";

    public static final String DEFAULT_BASE_URL = "https://api.library.cdisc.org/api/";

    // --- Internal URL fragment constants (avoid duplicate string literals) ---

    // Base paths
    private static final String PATH_MDR = "/mdr/";

    private static final String PATH_MDR_ADAM = PATH_MDR + "adam/";

    private static final String PATH_MDR_SDTM = PATH_MDR + "sdtm/";

    private static final String PATH_MDR_SDTMIG = PATH_MDR + "sdtmig/";

    private static final String PATH_MDR_SENDIG = PATH_MDR + "sendig/";

    private static final String PATH_MDR_CDASH = PATH_MDR + "cdash/";

    private static final String PATH_MDR_CDASHIG = PATH_MDR + "cdashig/";

    private static final String PATH_MDR_INTEGRATED = PATH_MDR + "integrated/";

    private static final String PATH_MDR_ROOT_INTEGRATED = PATH_MDR + "root/integrated/";

    private static final String PATH_MDR_CT_PACKAGES = PATH_MDR + "ct/packages/";

    private static final String PATH_MDR_DOCUMENTS = PATH_MDR + "documents/";

    private static final String PATH_MDR_DOCUMENTS_INTEGRATED = PATH_MDR_DOCUMENTS + "integrated/";

    private static final String PATH_MDR_QRS_INSTRUMENTS = PATH_MDR + "qrs/instruments/";

    // Path segments (with leading slash, trailing slash variant where present)
    private static final String SEG_DATASTRUCTURES_SLASH = "/datastructures/";

    private static final String SEG_VARIABLES = "/variables";

    private static final String SEG_VARIABLES_SLASH = SEG_VARIABLES + "/";

    private static final String SEG_DATASETS = "/datasets";

    private static final String SEG_DATASETS_SLASH = SEG_DATASETS + "/";

    private static final String SEG_CLASSES = "/classes";

    private static final String SEG_CLASSES_SLASH = SEG_CLASSES + "/";

    private static final String SEG_CODELISTS_SLASH = "/codelists/";

    private static final String SEG_FIELDS = "/fields";

    private static final String SEG_FIELDS_SLASH = SEG_FIELDS + "/";

    private static final String SEG_DOMAINS = "/domains";

    private static final String SEG_DOMAINS_SLASH = SEG_DOMAINS + "/";

    private static final String SEG_SCENARIOS = "/scenarios";

    private static final String SEG_SCENARIOS_SLASH = SEG_SCENARIOS + "/";

    private static final String SEG_SECTIONS = "/sections";

    private static final String SEG_SECTIONS_SLASH = SEG_SECTIONS + "/";

    private static final String SEG_USECASES_SLASH = "/usecases/";

    private static final String SEG_VERSIONS_SLASH = "/versions/";

    // Compositional path segments for integrated paths
    private static final String SEG_CDASH = "/cdash";

    private static final String SEG_ADAM_DATASTRUCTURES_SLASH = "/adam" + SEG_DATASTRUCTURES_SLASH;

    private static final String SEG_SDTM_DATASETS_SLASH = "/sdtm" + SEG_DATASETS_SLASH;

    private static final String SEG_CDASH_CLASSES_SLASH = SEG_CDASH + SEG_CLASSES_SLASH;

    private static final String SEG_CDASH_DOMAINS_SLASH = SEG_CDASH + SEG_DOMAINS_SLASH;

    private static final String SEG_CDASH_SCENARIOS_SLASH = SEG_CDASH + SEG_SCENARIOS_SLASH;

    private static final String SEG_SEND_DATASETS_SLASH = "/send" + SEG_DATASETS_SLASH;

    // HATEOAS link keys returned by ApiResource.getLinks(...)
    private static final String LINK_CLASSES = "classes";

    private static final String LINK_DATASETS = "datasets";

    private static final String LINK_DATASET_VARIABLES = "datasetVariables";

    private static final String LINK_FIELDS = "fields";

    private static final String LINK_DOMAINS = "domains";

    private static final String LINK_SCENARIOS = "scenarios";

    // Resource-type tokens used for cache namespacing and proxy creation
    private static final String TYPE_PRODUCT = "product";

    private static final String TYPE_DATA_STRUCTURE = "dataStructure";

    private static final String TYPE_VARIABLE = "variable";

    private static final String TYPE_STANDARD = "standard";

    private static final String TYPE_VERSION = "version";

    private static final String TYPE_CLASS_NAME = "className";

    private static final String TYPE_DATASET = "dataset";

    private static final String TYPE_PACKAGE_ID = "packageId";

    private static final String TYPE_CODELIST = "codelist";

    private static final String TYPE_FIELD = "field";

    private static final String TYPE_DOMAIN = "domain";

    private static final String TYPE_SCENARIO = "scenario";

    private static final String TYPE_INSTRUMENT = "instrument";

    private static final String TYPE_STRUCTURE = "structure";

    private static final String TYPE_SECTION = "section";

    private static final String TYPE_USE_CASE = "useCase";

    private static final String TYPE_SUBTYPE = "subtype";

    public static @Nullable String retrieveSystemProperty(String aEnvVarName, String aPropertyName)
    {
        String res = System.getenv(aEnvVarName);
        if (!CDT.isBlankOrNull(res))
        {
            return res;
        }
        return System.getProperty(aPropertyName);
    }


    public static @Nullable String getApiKey()
    {
        return retrieveSystemProperty(ENV_CDISC_API_KEY, SP_CDISC_API_KEY);
    }


    public static String getApiUrl()
    {
        String apiUrl = retrieveSystemProperty(ENV_CDISC_API_URL, SP_CDISC_API_URL);
        if (CDT.isBlankOrNull(apiUrl))
        {
            return DEFAULT_BASE_URL;
        }
        // isBlankOrNull == false guarantees a non-null value; NullAway can't model the guard.
        return Objects.requireNonNull(apiUrl);
    }


    public static ApiCache getCache()
    {
        String configured = retrieveSystemProperty(ENV_CDISC_API_CACHE, SP_CDISC_API_CACHE);
        String cacheDir;
        if (CDT.isBlankOrNull(configured))
        {
            cacheDir = new File(System.getProperty("user.home"), ".cdiscApiCache")
                    .getAbsolutePath();
        }
        else
        {
            // isBlankOrNull == false guarantees a non-null value; NullAway can't model the guard.
            cacheDir = Objects.requireNonNull(configured);
        }
        Path cachePath = new File(cacheDir).toPath();

        return new GzipFileApiCache(cachePath, ".json");
    }


    private CdiscLibraryClient(CdiscBuilder builder)
    {
        super(builder);
    }

    // --- Products ---


    /** {@code GET /mdr/products} — returns the full products catalog. */
    public Products getProducts() throws IOException
    {
        return getProducts(false);
    }


    /** {@code GET /mdr/products} with optional expand. */
    public Products getProducts(boolean expand) throws IOException
    {
        return get(expand("/mdr/products", expand), Products.class);
    }


    /** {@code GET /mdr/products/{group}} — returns products in a group. */
    public ProductGroup getProductGroup(String group) throws IOException
    {
        return getProductGroup(group, false);
    }


    /** {@code GET /mdr/products/{group}} with optional expand. */
    public ProductGroup getProductGroup(String group, boolean expand) throws IOException
    {
        requireNonEmpty(group, "group");
        return get(expand("/mdr/products/" + group, expand), ProductGroup.class);
    }

    // --- ADaM ---


    /** {@code GET /mdr/adam/{product}} — returns an ADaM product version. */
    public AdamProduct getAdamProduct(String product) throws IOException
    {
        return getAdamProduct(product, false);
    }


    /** {@code GET /mdr/adam/{product}} with optional expand. */
    public AdamProduct getAdamProduct(String product, boolean expand) throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        return get(expand(PATH_MDR_ADAM + product, expand), AdamProduct.class);
    }


    /** {@code GET /mdr/adam/{product}/datastructures/{datastructure}} */
    public AdamDataStructure getAdamDataStructure(String product, String dataStructure)
        throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        return get(PATH_MDR_ADAM + product + SEG_DATASTRUCTURES_SLASH + dataStructure,
                AdamDataStructure.class);
    }


    /** {@code GET /mdr/adam/{product}/datastructures} — lists ADaM data structures. */
    public List<Link> getAdamDataStructureLinks(String product) throws IOException
    {
        return getAdamDataStructureLinks(product, false);
    }


    /** {@code GET /mdr/adam/{product}/datastructures} with optional expand. */
    public List<Link> getAdamDataStructureLinks(String product, boolean expand) throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        ApiResource ds = get(expand(PATH_MDR_ADAM + product + "/datastructures", expand));
        return ds.getLinks("dataStructures");
    }


    /** {@code GET /mdr/adam/{product}/datastructures/{ds}/varsets} — lists ADaM variable sets. */
    public List<Link> getAdamVariableSetLinks(String product, String dataStructure)
        throws IOException
    {
        return getAdamVariableSetLinks(product, dataStructure, false);
    }


    /** {@code GET /mdr/adam/{product}/datastructures/{ds}/varsets} with optional expand. */
    public List<Link> getAdamVariableSetLinks(String product, String dataStructure, boolean expand)
        throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        ApiResource vs = get(expand(
                PATH_MDR_ADAM + product + SEG_DATASTRUCTURES_SLASH + dataStructure + "/varsets",
                expand));
        return vs.getLinks("analysisVariableSets");
    }


    /** {@code GET /mdr/adam/{product}/datastructures/{ds}/variables} — returns variable links. */
    public List<Link> getAdamVariableLinks(String product, String dataStructure) throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        ApiResource res = get(
                PATH_MDR_ADAM + product + SEG_DATASTRUCTURES_SLASH + dataStructure + SEG_VARIABLES);
        return res.getLinks("analysisVariables");
    }


    /** {@code GET /mdr/adam/{product}/datastructures/{ds}/variables/{var}} */
    public AdamVariable getAdamVariable(String product, String dataStructure, String variable)
        throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR_ADAM + product + SEG_DATASTRUCTURES_SLASH + dataStructure
                + SEG_VARIABLES_SLASH + variable, AdamVariable.class);
    }


    /** {@code GET /mdr/adam/{product}/datastructures/{ds}/varsets/{varset}} */
    public AdamVariableSet getAdamVariableSet(String product, String dataStructure, String varset)
        throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        requireNonEmpty(varset, "varset");
        return get(PATH_MDR_ADAM + product + SEG_DATASTRUCTURES_SLASH + dataStructure + "/varsets/"
                + varset, AdamVariableSet.class);
    }

    // --- SDTM / SDTMIG ---


    /**
     * {@code GET /mdr/sdtm/{version}} or {@code GET /mdr/sdtmig/{version}}.
     *
     * @param standard
     *            "sdtm" or "sdtmig"
     * @param version
     *            e.g. "2-0" or "3-4"
     */
    public SdtmProduct getSdtmVersion(String standard, String version) throws IOException
    {
        return getSdtmVersion(standard, version, false);
    }


    /** {@code GET /mdr/sdtm/{version}} or {@code /mdr/sdtmig/{version}} with optional expand. */
    public SdtmProduct getSdtmVersion(String standard, String version, boolean expand)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(expand(PATH_MDR + standard + "/" + version, expand), SdtmProduct.class);
    }


    /** {@code GET /mdr/sdtm/{version}/classes} — returns class links. */
    public List<Link> getSdtmClassLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        ApiResource res = get(PATH_MDR_SDTM + version + SEG_CLASSES);
        return res.getLinks(LINK_CLASSES);
    }


    /** {@code GET /mdr/sdtm/{version}/classes/{className}} */
    public SdtmClass getSdtmClass(String version, String className) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_SDTM + version + SEG_CLASSES_SLASH + className, SdtmClass.class);
    }


    /**
     * {@code GET /mdr/sdtm/{version}/classes/{className}/variables} — returns class variable links.
     */
    public List<Link> getSdtmClassVariableLinks(String version, String className) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        ApiResource res = get(
                PATH_MDR_SDTM + version + SEG_CLASSES_SLASH + className + SEG_VARIABLES);
        return res.getLinks("classVariables");
    }


    /** {@code GET /mdr/sdtm/{version}/classes/{className}/variables/{var}} */
    public SdtmVariable getSdtmClassVariable(String version, String className, String variable)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR_SDTM + version + SEG_CLASSES_SLASH + className + SEG_VARIABLES_SLASH
                + variable, SdtmVariable.class);
    }


    /**
     * {@code GET /mdr/sdtm/{version}/classes/{className}/datasets} — returns dataset links for a
     * class.
     */
    public List<Link> getSdtmClassDatasetLinks(String version, String className) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        ApiResource res = get(
                PATH_MDR_SDTM + version + SEG_CLASSES_SLASH + className + SEG_DATASETS);
        return res.getLinks(LINK_DATASETS);
    }


    /** {@code GET /mdr/sdtmig/{version}/classes} — returns SDTM-IG class links. */
    public List<Link> getSdtmigClassLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        ApiResource res = get(PATH_MDR_SDTMIG + version + SEG_CLASSES);
        return res.getLinks(LINK_CLASSES);
    }


    /** {@code GET /mdr/sdtmig/{version}/classes/{className}} */
    public SdtmClass getSdtmigClass(String version, String className) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_SDTMIG + version + SEG_CLASSES_SLASH + className, SdtmClass.class);
    }


    /** {@code GET /mdr/sdtmig/{version}/classes/{className}/datasets} — returns dataset links. */
    public List<Link> getSdtmigClassDatasetLinks(String version, String className)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        ApiResource res = get(
                PATH_MDR_SDTMIG + version + SEG_CLASSES_SLASH + className + SEG_DATASETS);
        return res.getLinks(LINK_DATASETS);
    }


    /** {@code GET /mdr/sdtm/{version}/datasets/{dataset}} or SDTM-IG equivalent. */
    public SdtmDataset getSdtmDataset(String standard, String version, String dataset)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        return get(PATH_MDR + standard + "/" + version + SEG_DATASETS_SLASH + dataset,
                SdtmDataset.class);
    }


    /** {@code GET /mdr/sdtm/{version}/datasets/{dataset}/variables/{var}} or SDTM-IG equivalent. */
    public SdtmVariable getSdtmVariable(String standard, String version, String dataset,
            String variable)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR + standard + "/" + version + SEG_DATASETS_SLASH + dataset
                + SEG_VARIABLES_SLASH + variable, SdtmVariable.class);
    }


    /** Convenience: returns all dataset links for an SDTM/SDTM-IG version. */
    public List<Link> getSdtmDatasetLinks(String standard, String version) throws IOException
    {
        return getSdtmDatasetLinks(standard, version, false);
    }


    /** Returns all dataset links for an SDTM/SDTM-IG version with optional expand. */
    public List<Link> getSdtmDatasetLinks(String standard, String version, boolean expand)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        ApiResource datasets = get(
                expand(PATH_MDR + standard + "/" + version + SEG_DATASETS, expand));
        return datasets.getLinks(LINK_DATASETS);
    }


    /**
     * {@code GET /mdr/sdtm/{version}/datasets/{dataset}/variables} or SDTM-IG equivalent — returns
     * variable links.
     */
    public List<Link> getSdtmDatasetVariableLinks(String standard, String version, String dataset)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        ApiResource res = get(
                PATH_MDR + standard + "/" + version + SEG_DATASETS_SLASH + dataset + SEG_VARIABLES);
        return res.getLinks(LINK_DATASET_VARIABLES);
    }

    // --- SDTM / SDTMIG Root (versionless) ---


    /**
     * {@code GET /mdr/root/sdtm/classes/{className}/variables/{var}} — versionless class variable.
     */
    public ApiResource getRootSdtmClassVariable(String className, String variable)
        throws IOException
    {
        requireNonEmpty(className, TYPE_CLASS_NAME);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get("/mdr/root/sdtm/classes/" + className + SEG_VARIABLES_SLASH + variable);
    }


    /**
     * {@code GET /mdr/root/sdtm/datasets/{dataset}/variables/{var}} — versionless dataset variable.
     */
    public ApiResource getRootSdtmDatasetVariable(String dataset, String variable)
        throws IOException
    {
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get("/mdr/root/sdtm/datasets/" + dataset + SEG_VARIABLES_SLASH + variable);
    }


    /**
     * {@code GET /mdr/root/sdtmig/datasets/{dataset}/variables/{var}} — versionless SDTM-IG dataset
     * variable.
     */
    public ApiResource getRootSdtmigDatasetVariable(String dataset, String variable)
        throws IOException
    {
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get("/mdr/root/sdtmig/datasets/" + dataset + SEG_VARIABLES_SLASH + variable);
    }

    // --- CT (Controlled Terminology) ---


    /** {@code GET /mdr/ct/packages} — lists all CT packages. */
    public CtPackageList getCtPackages() throws IOException
    {
        return get("/mdr/ct/packages", CtPackageList.class);
    }


    /** {@code GET /mdr/ct/packages/{package}} */
    public CtPackage getCtPackage(String packageId) throws IOException
    {
        return getCtPackage(packageId, false);
    }


    /** {@code GET /mdr/ct/packages/{package}} with optional expand. */
    public CtPackage getCtPackage(String packageId, boolean expand) throws IOException
    {
        requireNonEmpty(packageId, TYPE_PACKAGE_ID);
        return get(expand(PATH_MDR_CT_PACKAGES + packageId, expand), CtPackage.class);
    }


    /** Convenience: returns codelist links for a CT package. */
    public List<Link> getCtCodelistLinks(String packageId) throws IOException
    {
        return getCtCodelistLinks(packageId, false);
    }


    /** Returns codelist links for a CT package with optional expand. */
    public List<Link> getCtCodelistLinks(String packageId, boolean expand) throws IOException
    {
        requireNonEmpty(packageId, TYPE_PACKAGE_ID);
        ApiResource codelists = get(
                expand(PATH_MDR_CT_PACKAGES + packageId + "/codelists", expand));
        return codelists.getLinks("codelists");
    }


    /** {@code GET /mdr/ct/packages/{package}/codelists/{codelist}} */
    public CtCodelist getCtCodelist(String packageId, String codelist) throws IOException
    {
        requireNonEmpty(packageId, TYPE_PACKAGE_ID);
        requireNonEmpty(codelist, TYPE_CODELIST);
        return get(PATH_MDR_CT_PACKAGES + packageId + SEG_CODELISTS_SLASH + codelist,
                CtCodelist.class);
    }


    /** {@code GET /mdr/ct/packages/{package}/codelists/{codelist}/terms} — lists terms as links. */
    public List<Link> getCtTermLinks(String packageId, String codelist) throws IOException
    {
        requireNonEmpty(packageId, TYPE_PACKAGE_ID);
        requireNonEmpty(codelist, TYPE_CODELIST);
        ApiResource terms = get(
                PATH_MDR_CT_PACKAGES + packageId + SEG_CODELISTS_SLASH + codelist + "/terms");
        return terms.getLinks("terms");
    }


    /** {@code GET /mdr/ct/packages/{package}/codelists/{codelist}/terms/{term}} */
    public CtTerm getCtTerm(String packageId, String codelist, String term) throws IOException
    {
        requireNonEmpty(packageId, TYPE_PACKAGE_ID);
        requireNonEmpty(codelist, TYPE_CODELIST);
        requireNonEmpty(term, "term");
        return get(PATH_MDR_CT_PACKAGES + packageId + SEG_CODELISTS_SLASH + codelist + "/terms/"
                + term, CtTerm.class);
    }

    // --- CT Root (versionless) ---


    /** {@code GET /mdr/root/ct/{package-type}/codelists/{codelist}} — versionless codelist. */
    public CtCodelist getRootCtCodelist(String packageType, String codelist) throws IOException
    {
        requireNonEmpty(packageType, "packageType");
        requireNonEmpty(codelist, TYPE_CODELIST);
        return get("/mdr/root/ct/" + packageType + SEG_CODELISTS_SLASH + codelist,
                CtCodelist.class);
    }


    /**
     * {@code GET /mdr/root/ct/{package-type}/codelists/{codelist}/terms/{term}} — versionless term.
     */
    public CtTerm getRootCtTerm(String packageType, String codelist, String term) throws IOException
    {
        requireNonEmpty(packageType, "packageType");
        requireNonEmpty(codelist, TYPE_CODELIST);
        requireNonEmpty(term, "term");
        return get(
                "/mdr/root/ct/" + packageType + SEG_CODELISTS_SLASH + codelist + "/terms/" + term,
                CtTerm.class);
    }

    // --- CDASH ---


    /** {@code GET /mdr/cdash/{version}} */
    public CdashProduct getCdashVersion(String version) throws IOException
    {
        return getCdashVersion(version, false);
    }


    /** {@code GET /mdr/cdash/{version}} with optional expand. */
    public CdashProduct getCdashVersion(String version, boolean expand) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(expand(PATH_MDR_CDASH + version, expand), CdashProduct.class);
    }


    /** {@code GET /mdr/cdash/{version}/classes} — returns class links. */
    public List<Link> getCdashClassLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_CDASH + version + SEG_CLASSES).getLinks(LINK_CLASSES);
    }


    /** {@code GET /mdr/cdash/{version}/classes/{className}} */
    public CdashClass getCdashClass(String version, String className) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_CDASH + version + SEG_CLASSES_SLASH + className, CdashClass.class);
    }


    /** {@code GET /mdr/cdash/{version}/classes/{className}/domains} — returns domain links. */
    public List<Link> getCdashClassDomainLinks(String version, String className) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_CDASH + version + SEG_CLASSES_SLASH + className + SEG_DOMAINS)
                .getLinks(LINK_DOMAINS);
    }


    /** {@code GET /mdr/cdash/{version}/classes/{className}/fields/{field}} */
    public CdashField getCdashClassField(String version, String className, String field)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        requireNonEmpty(field, TYPE_FIELD);
        return get(
                PATH_MDR_CDASH + version + SEG_CLASSES_SLASH + className + SEG_FIELDS_SLASH + field,
                CdashField.class);
    }


    /** {@code GET /mdr/cdash/{version}/domains} — returns domain links. */
    public List<Link> getCdashDomainLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_CDASH + version + SEG_DOMAINS).getLinks(LINK_DOMAINS);
    }


    /** {@code GET /mdr/cdash/{version}/domains/{domain}} */
    public CdashDomain getCdashDomain(String version, String domain) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        return get(PATH_MDR_CDASH + version + SEG_DOMAINS_SLASH + domain, CdashDomain.class);
    }


    /** {@code GET /mdr/cdash/{version}/domains/{domain}/fields} — returns field links. */
    public List<Link> getCdashDomainFieldLinks(String version, String domain) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        return get(PATH_MDR_CDASH + version + SEG_DOMAINS_SLASH + domain + SEG_FIELDS)
                .getLinks(LINK_FIELDS);
    }


    /** {@code GET /mdr/cdash/{version}/domains/{domain}/fields/{field}} */
    public CdashField getCdashDomainField(String version, String domain, String field)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        requireNonEmpty(field, TYPE_FIELD);
        return get(PATH_MDR_CDASH + version + SEG_DOMAINS_SLASH + domain + SEG_FIELDS_SLASH + field,
                CdashField.class);
    }

    // --- CDASH Root (versionless) ---


    /** {@code GET /mdr/root/cdash/classes/{className}/fields/{field}} */
    public ApiResource getRootCdashClassField(String className, String field) throws IOException
    {
        requireNonEmpty(className, TYPE_CLASS_NAME);
        requireNonEmpty(field, TYPE_FIELD);
        return get("/mdr/root/cdash/classes/" + className + SEG_FIELDS_SLASH + field);
    }


    /** {@code GET /mdr/root/cdash/domains/{domain}/fields/{field}} */
    public ApiResource getRootCdashDomainField(String domain, String field) throws IOException
    {
        requireNonEmpty(domain, TYPE_DOMAIN);
        requireNonEmpty(field, TYPE_FIELD);
        return get("/mdr/root/cdash/domains/" + domain + SEG_FIELDS_SLASH + field);
    }

    // --- CDASHIG ---


    /** {@code GET /mdr/cdashig/{version}} */
    public CdashProduct getCdashigVersion(String version) throws IOException
    {
        return getCdashigVersion(version, false);
    }


    /** {@code GET /mdr/cdashig/{version}} with optional expand. */
    public CdashProduct getCdashigVersion(String version, boolean expand) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(expand(PATH_MDR_CDASHIG + version, expand), CdashProduct.class);
    }


    /** {@code GET /mdr/cdashig/{version}/classes} */
    public List<Link> getCdashigClassLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_CDASHIG + version + SEG_CLASSES).getLinks(LINK_CLASSES);
    }


    /** {@code GET /mdr/cdashig/{version}/classes/{className}} */
    public CdashClass getCdashigClass(String version, String className) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_CDASHIG + version + SEG_CLASSES_SLASH + className, CdashClass.class);
    }


    /** {@code GET /mdr/cdashig/{version}/classes/{className}/domains} */
    public List<Link> getCdashigClassDomainLinks(String version, String className)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_CDASHIG + version + SEG_CLASSES_SLASH + className + SEG_DOMAINS)
                .getLinks(LINK_DOMAINS);
    }


    /** {@code GET /mdr/cdashig/{version}/classes/{className}/scenarios} */
    public List<Link> getCdashigClassScenarioLinks(String version, String className)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_CDASHIG + version + SEG_CLASSES_SLASH + className + SEG_SCENARIOS)
                .getLinks(LINK_SCENARIOS);
    }


    /** {@code GET /mdr/cdashig/{version}/domains} */
    public List<Link> getCdashigDomainLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_CDASHIG + version + SEG_DOMAINS).getLinks(LINK_DOMAINS);
    }


    /** {@code GET /mdr/cdashig/{version}/domains/{domain}} */
    public CdashDomain getCdashigDomain(String version, String domain) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        return get(PATH_MDR_CDASHIG + version + SEG_DOMAINS_SLASH + domain, CdashDomain.class);
    }


    /** {@code GET /mdr/cdashig/{version}/domains/{domain}/fields} */
    public List<Link> getCdashigDomainFieldLinks(String version, String domain) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        return get(PATH_MDR_CDASHIG + version + SEG_DOMAINS_SLASH + domain + SEG_FIELDS)
                .getLinks(LINK_FIELDS);
    }


    /** {@code GET /mdr/cdashig/{version}/domains/{domain}/fields/{field}} */
    public CdashField getCdashigDomainField(String version, String domain, String field)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        requireNonEmpty(field, TYPE_FIELD);
        return get(
                PATH_MDR_CDASHIG + version + SEG_DOMAINS_SLASH + domain + SEG_FIELDS_SLASH + field,
                CdashField.class);
    }


    /** {@code GET /mdr/cdashig/{version}/scenarios} */
    public List<Link> getCdashigScenarioLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_CDASHIG + version + SEG_SCENARIOS).getLinks(LINK_SCENARIOS);
    }


    /** {@code GET /mdr/cdashig/{version}/scenarios/{scenario}} */
    public CdashScenario getCdashigScenario(String version, String scenario) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(scenario, TYPE_SCENARIO);
        return get(PATH_MDR_CDASHIG + version + SEG_SCENARIOS_SLASH + scenario,
                CdashScenario.class);
    }


    /** {@code GET /mdr/cdashig/{version}/scenarios/{scenario}/fields} */
    public List<Link> getCdashigScenarioFieldLinks(String version, String scenario)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(scenario, TYPE_SCENARIO);
        return get(PATH_MDR_CDASHIG + version + SEG_SCENARIOS_SLASH + scenario + SEG_FIELDS)
                .getLinks(LINK_FIELDS);
    }


    /** {@code GET /mdr/cdashig/{version}/scenarios/{scenario}/fields/{field}} */
    public CdashField getCdashigScenarioField(String version, String scenario, String field)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(scenario, TYPE_SCENARIO);
        requireNonEmpty(field, TYPE_FIELD);
        return get(PATH_MDR_CDASHIG + version + SEG_SCENARIOS_SLASH + scenario + SEG_FIELDS_SLASH
                + field, CdashField.class);
    }

    // --- CDASHIG Root (versionless) ---


    /** {@code GET /mdr/root/cdashig/domains/{domain}/fields/{field}} */
    public ApiResource getRootCdashigDomainField(String domain, String field) throws IOException
    {
        requireNonEmpty(domain, TYPE_DOMAIN);
        requireNonEmpty(field, TYPE_FIELD);
        return get("/mdr/root/cdashig/domains/" + domain + SEG_FIELDS_SLASH + field);
    }


    /** {@code GET /mdr/root/cdashig/scenarios/{scenario}/fields/{field}} */
    public ApiResource getRootCdashigScenarioField(String scenario, String field) throws IOException
    {
        requireNonEmpty(scenario, TYPE_SCENARIO);
        requireNonEmpty(field, TYPE_FIELD);
        return get("/mdr/root/cdashig/scenarios/" + scenario + SEG_FIELDS_SLASH + field);
    }

    // --- SENDIG ---


    /** {@code GET /mdr/sendig/{version}} */
    public SdtmProduct getSendigVersion(String version) throws IOException
    {
        return getSendigVersion(version, false);
    }


    /** {@code GET /mdr/sendig/{version}} with optional expand. */
    public SdtmProduct getSendigVersion(String version, boolean expand) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(expand(PATH_MDR_SENDIG + version, expand), SdtmProduct.class);
    }


    /** {@code GET /mdr/sendig/{version}/classes} */
    public List<Link> getSendigClassLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_SENDIG + version + SEG_CLASSES).getLinks(LINK_CLASSES);
    }


    /** {@code GET /mdr/sendig/{version}/classes/{className}} */
    public SdtmClass getSendigClass(String version, String className) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_SENDIG + version + SEG_CLASSES_SLASH + className, SdtmClass.class);
    }


    /** {@code GET /mdr/sendig/{version}/classes/{className}/datasets} */
    public List<Link> getSendigClassDatasetLinks(String version, String className)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_SENDIG + version + SEG_CLASSES_SLASH + className + SEG_DATASETS)
                .getLinks(LINK_DATASETS);
    }


    /** {@code GET /mdr/sendig/{version}/datasets} */
    public List<Link> getSendigDatasetLinks(String version) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_SENDIG + version + SEG_DATASETS).getLinks(LINK_DATASETS);
    }


    /** {@code GET /mdr/sendig/{version}/datasets/{dataset}} */
    public SdtmDataset getSendigDataset(String version, String dataset) throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        return get(PATH_MDR_SENDIG + version + SEG_DATASETS_SLASH + dataset, SdtmDataset.class);
    }


    /** {@code GET /mdr/sendig/{version}/datasets/{dataset}/variables} */
    public List<Link> getSendigDatasetVariableLinks(String version, String dataset)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        return get(PATH_MDR_SENDIG + version + SEG_DATASETS_SLASH + dataset + SEG_VARIABLES)
                .getLinks(LINK_DATASET_VARIABLES);
    }


    /** {@code GET /mdr/sendig/{version}/datasets/{dataset}/variables/{var}} */
    public SdtmVariable getSendigVariable(String version, String dataset, String variable)
        throws IOException
    {
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR_SENDIG + version + SEG_DATASETS_SLASH + dataset + SEG_VARIABLES_SLASH
                + variable, SdtmVariable.class);
    }

    // --- SENDIG Root (versionless) ---


    /** {@code GET /mdr/root/sendig/datasets/{dataset}/variables/{var}} */
    public ApiResource getRootSendigVariable(String dataset, String variable) throws IOException
    {
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get("/mdr/root/sendig/datasets/" + dataset + SEG_VARIABLES_SLASH + variable);
    }

    // --- Integrated Standards ---


    /** {@code GET /mdr/integrated/{standard}/{version}} */
    public IntegratedProduct getIntegratedVersion(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version, IntegratedProduct.class);
    }

    // --- Integrated / SDTM ---


    /** {@code GET /mdr/integrated/{standard}/{version}/sdtm} */
    public SdtmProduct getIntegratedSdtm(String standard, String version) throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/sdtm", SdtmProduct.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/sdtm/classes} */
    public List<Link> getIntegratedSdtmClassLinks(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/sdtm/classes")
                .getLinks(LINK_CLASSES);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/sdtm/classes/{className}} */
    public SdtmClass getIntegratedSdtmClass(String standard, String version, String className)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/sdtm/classes/" + className,
                SdtmClass.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/sdtm/classes/{className}/datasets} */
    public List<Link> getIntegratedSdtmClassDatasetLinks(String standard, String version,
            String className)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/sdtm/classes/" + className
                + SEG_DATASETS).getLinks(LINK_DATASETS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/sdtm/datasets} */
    public List<Link> getIntegratedSdtmDatasetLinks(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/sdtm/datasets")
                .getLinks(LINK_DATASETS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/sdtm/datasets/{dataset}} */
    public SdtmDataset getIntegratedSdtmDataset(String standard, String version, String dataset)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        return get(
                PATH_MDR_INTEGRATED + standard + "/" + version + SEG_SDTM_DATASETS_SLASH + dataset,
                SdtmDataset.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/sdtm/datasets/{dataset}/variables} */
    public List<Link> getIntegratedSdtmDatasetVariableLinks(String standard, String version,
            String dataset)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_SDTM_DATASETS_SLASH
                + dataset + SEG_VARIABLES).getLinks(LINK_DATASET_VARIABLES);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/sdtm/datasets/{dataset}/variables/{var}} */
    public SdtmVariable getIntegratedSdtmVariable(String standard, String version, String dataset,
            String variable)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_SDTM_DATASETS_SLASH
                + dataset + SEG_VARIABLES_SLASH + variable, SdtmVariable.class);
    }


    /** {@code GET /mdr/root/integrated/{standard}/sdtm/datasets/{dataset}/variables/{var}} */
    public ApiResource getRootIntegratedSdtmVariable(String standard, String dataset,
            String variable)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR_ROOT_INTEGRATED + standard + SEG_SDTM_DATASETS_SLASH + dataset
                + SEG_VARIABLES_SLASH + variable);
    }

    // --- Integrated / SEND ---


    /** {@code GET /mdr/integrated/{standard}/{version}/send} */
    public SdtmProduct getIntegratedSend(String standard, String version) throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/send", SdtmProduct.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/send/classes} */
    public List<Link> getIntegratedSendClassLinks(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/send/classes")
                .getLinks(LINK_CLASSES);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/send/classes/{className}} */
    public SdtmClass getIntegratedSendClass(String standard, String version, String className)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/send/classes/" + className,
                SdtmClass.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/send/classes/{className}/datasets} */
    public List<Link> getIntegratedSendClassDatasetLinks(String standard, String version,
            String className)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/send/classes/" + className
                + SEG_DATASETS).getLinks(LINK_DATASETS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/send/datasets} */
    public List<Link> getIntegratedSendDatasetLinks(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/send/datasets")
                .getLinks(LINK_DATASETS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/send/datasets/{dataset}} */
    public SdtmDataset getIntegratedSendDataset(String standard, String version, String dataset)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        return get(
                PATH_MDR_INTEGRATED + standard + "/" + version + SEG_SEND_DATASETS_SLASH + dataset,
                SdtmDataset.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/send/datasets/{dataset}/variables} */
    public List<Link> getIntegratedSendDatasetVariableLinks(String standard, String version,
            String dataset)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_SEND_DATASETS_SLASH
                + dataset + SEG_VARIABLES).getLinks(LINK_DATASET_VARIABLES);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/send/datasets/{dataset}/variables/{var}} */
    public SdtmVariable getIntegratedSendVariable(String standard, String version, String dataset,
            String variable)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_SEND_DATASETS_SLASH
                + dataset + SEG_VARIABLES_SLASH + variable, SdtmVariable.class);
    }


    /** {@code GET /mdr/root/integrated/{standard}/send/datasets/{dataset}/variables/{var}} */
    public ApiResource getRootIntegratedSendVariable(String standard, String dataset,
            String variable)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(dataset, TYPE_DATASET);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR_ROOT_INTEGRATED + standard + SEG_SEND_DATASETS_SLASH + dataset
                + SEG_VARIABLES_SLASH + variable);
    }

    // --- Integrated / ADaM ---


    /** {@code GET /mdr/integrated/{standard}/{version}/adam} */
    public AdamProduct getIntegratedAdam(String standard, String version) throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/adam", AdamProduct.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/adam/datastructures} */
    public List<Link> getIntegratedAdamDataStructureLinks(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/adam/datastructures")
                .getLinks("dataStructures");
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/adam/datastructures/{ds}} */
    public AdamDataStructure getIntegratedAdamDataStructure(String standard, String version,
            String dataStructure)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_ADAM_DATASTRUCTURES_SLASH
                + dataStructure, AdamDataStructure.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/adam/datastructures/{ds}/variables} */
    public List<Link> getIntegratedAdamVariableLinks(String standard, String version,
            String dataStructure)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_ADAM_DATASTRUCTURES_SLASH
                + dataStructure + SEG_VARIABLES).getLinks("analysisVariables");
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/adam/datastructures/{ds}/variables/{var}} */
    public AdamVariable getIntegratedAdamVariable(String standard, String version,
            String dataStructure, String variable)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        requireNonEmpty(variable, TYPE_VARIABLE);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_ADAM_DATASTRUCTURES_SLASH
                + dataStructure + SEG_VARIABLES_SLASH + variable, AdamVariable.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/adam/datastructures/{ds}/varsets} */
    public List<Link> getIntegratedAdamVariableSetLinks(String standard, String version,
            String dataStructure)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_ADAM_DATASTRUCTURES_SLASH
                + dataStructure + "/varsets").getLinks("analysisVariableSets");
    }


    /**
     * {@code GET /mdr/integrated/{standard}/{version}/adam/datastructures/{ds}/varsets/{varset}}
     */
    public AdamVariableSet getIntegratedAdamVariableSet(String standard, String version,
            String dataStructure, String varset)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(dataStructure, TYPE_DATA_STRUCTURE);
        requireNonEmpty(varset, "varset");
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_ADAM_DATASTRUCTURES_SLASH
                + dataStructure + "/varsets/" + varset, AdamVariableSet.class);
    }

    // --- Integrated / CDASH ---


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash} */
    public CdashProduct getIntegratedCdash(String standard, String version) throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH, CdashProduct.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/classes} */
    public List<Link> getIntegratedCdashClassLinks(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/cdash/classes")
                .getLinks(LINK_CLASSES);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/classes/{className}} */
    public CdashClass getIntegratedCdashClass(String standard, String version, String className)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_CLASSES_SLASH
                + className, CdashClass.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/classes/{className}/domains} */
    public List<Link> getIntegratedCdashClassDomainLinks(String standard, String version,
            String className)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_CLASSES_SLASH
                + className + SEG_DOMAINS).getLinks(LINK_DOMAINS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/classes/{className}/scenarios} */
    public List<Link> getIntegratedCdashClassScenarioLinks(String standard, String version,
            String className)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(className, TYPE_CLASS_NAME);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_CLASSES_SLASH
                + className + SEG_SCENARIOS).getLinks(LINK_SCENARIOS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/domains} */
    public List<Link> getIntegratedCdashDomainLinks(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/cdash/domains")
                .getLinks(LINK_DOMAINS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/domains/{domain}} */
    public CdashDomain getIntegratedCdashDomain(String standard, String version, String domain)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        return get(
                PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_DOMAINS_SLASH + domain,
                CdashDomain.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/domains/{domain}/fields} */
    public List<Link> getIntegratedCdashDomainFieldLinks(String standard, String version,
            String domain)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_DOMAINS_SLASH + domain
                + SEG_FIELDS).getLinks(LINK_FIELDS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/domains/{domain}/fields/{field}} */
    public CdashField getIntegratedCdashDomainField(String standard, String version, String domain,
            String field)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(domain, TYPE_DOMAIN);
        requireNonEmpty(field, TYPE_FIELD);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_DOMAINS_SLASH + domain
                + SEG_FIELDS_SLASH + field, CdashField.class);
    }


    /** {@code GET /mdr/root/integrated/{standard}/cdash/domains/{domain}/fields/{field}} */
    public ApiResource getRootIntegratedCdashDomainField(String standard, String domain,
            String field)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(domain, TYPE_DOMAIN);
        requireNonEmpty(field, TYPE_FIELD);
        return get(PATH_MDR_ROOT_INTEGRATED + standard + SEG_CDASH_DOMAINS_SLASH + domain
                + SEG_FIELDS_SLASH + field);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/scenarios} */
    public List<Link> getIntegratedCdashScenarioLinks(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + "/cdash/scenarios")
                .getLinks(LINK_SCENARIOS);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/scenarios/{scenario}} */
    public CdashScenario getIntegratedCdashScenario(String standard, String version,
            String scenario)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(scenario, TYPE_SCENARIO);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_SCENARIOS_SLASH
                + scenario, CdashScenario.class);
    }


    /** {@code GET /mdr/integrated/{standard}/{version}/cdash/scenarios/{scenario}/fields} */
    public List<Link> getIntegratedCdashScenarioFieldLinks(String standard, String version,
            String scenario)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(scenario, TYPE_SCENARIO);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_SCENARIOS_SLASH
                + scenario + SEG_FIELDS).getLinks(LINK_FIELDS);
    }


    /**
     * {@code GET /mdr/integrated/{standard}/{version}/cdash/scenarios/{scenario}/fields/{field}}
     */
    public CdashField getIntegratedCdashScenarioField(String standard, String version,
            String scenario, String field)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(scenario, TYPE_SCENARIO);
        requireNonEmpty(field, TYPE_FIELD);
        return get(PATH_MDR_INTEGRATED + standard + "/" + version + SEG_CDASH_SCENARIOS_SLASH
                + scenario + SEG_FIELDS_SLASH + field, CdashField.class);
    }


    /** {@code GET /mdr/root/integrated/{standard}/cdash/scenarios/{scenario}/fields/{field}} */
    public ApiResource getRootIntegratedCdashScenarioField(String standard, String scenario,
            String field)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(scenario, TYPE_SCENARIO);
        requireNonEmpty(field, TYPE_FIELD);
        return get(PATH_MDR_ROOT_INTEGRATED + standard + SEG_CDASH_SCENARIOS_SLASH + scenario
                + SEG_FIELDS_SLASH + field);
    }

    // --- QRS (Questionnaires, Ratings & Scales) ---


    /** {@code GET /mdr/qrs/instruments/{instrument}/versions/{version}} */
    public QrsInstrument getQrsInstrument(String instrument, String version) throws IOException
    {
        requireNonEmpty(instrument, TYPE_INSTRUMENT);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_QRS_INSTRUMENTS + instrument + SEG_VERSIONS_SLASH + version,
                QrsInstrument.class);
    }


    /** {@code GET /mdr/qrs/instruments/{instrument}/versions/{version}/responseGroups} */
    public List<Link> getQrsResponseGroupLinks(String instrument, String version) throws IOException
    {
        requireNonEmpty(instrument, TYPE_INSTRUMENT);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_QRS_INSTRUMENTS + instrument + SEG_VERSIONS_SLASH + version
                + "/responseGroups").getLinks("responseGroups");
    }


    /**
     * {@code GET
     * /mdr/qrs/instruments/{instrument}/versions/{version}/responseGroups/{responseGroup}}
     */
    public QrsResponseGroup getQrsResponseGroup(String instrument, String version,
            String responseGroup)
        throws IOException
    {
        requireNonEmpty(instrument, TYPE_INSTRUMENT);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(responseGroup, "responseGroup");
        return get(PATH_MDR_QRS_INSTRUMENTS + instrument + SEG_VERSIONS_SLASH + version
                + "/responseGroups/" + responseGroup, QrsResponseGroup.class);
    }


    /** {@code GET /mdr/qrs/instruments/{instrument}/versions/{version}/items} */
    public List<Link> getQrsItemLinks(String instrument, String version) throws IOException
    {
        requireNonEmpty(instrument, TYPE_INSTRUMENT);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_QRS_INSTRUMENTS + instrument + SEG_VERSIONS_SLASH + version + "/items")
                .getLinks("items");
    }


    /** {@code GET /mdr/qrs/instruments/{instrument}/versions/{version}/items/{item}} */
    public QrsItem getQrsItem(String instrument, String version, String item) throws IOException
    {
        requireNonEmpty(instrument, TYPE_INSTRUMENT);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(item, "item");
        return get(PATH_MDR_QRS_INSTRUMENTS + instrument + SEG_VERSIONS_SLASH + version + "/items/"
                + item, QrsItem.class);
    }


    /** {@code GET /mdr/root/qrs/instruments/{instrument}} — versionless instrument. */
    public ApiResource getRootQrsInstrument(String instrument) throws IOException
    {
        requireNonEmpty(instrument, TYPE_INSTRUMENT);
        return get("/mdr/root/qrs/instruments/" + instrument);
    }

    // --- Rules ---


    /** {@code GET /mdr/rules} — returns all rule catalogs. */
    public ApiResource getRuleCatalogs() throws IOException
    {
        return getRuleCatalogs(false);
    }


    /** {@code GET /mdr/rules} with optional expand. */
    public ApiResource getRuleCatalogs(boolean expand) throws IOException
    {
        return get(expand("/mdr/rules", expand));
    }


    /** {@code GET /mdr/rules/{standard}/{version}} */
    public RulePackage getRules(String standard, String version) throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get("/mdr/rules/" + standard + "/" + version, RulePackage.class);
    }


    /** {@code GET /mdr/rules/{standard}/{version}/rule/{rule_id}} */
    public Rule getRule(String standard, String version, String ruleId) throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(ruleId, "ruleId");
        return get("/mdr/rules/" + standard + "/" + version + "/rule/" + ruleId, Rule.class);
    }

    // --- Search ---


    /**
     * {@code GET /mdr/search} — full-text search across CDISC Library.
     *
     * @param query
     *            the search query
     * @return search results as links
     */
    public SearchResult search(String query) throws IOException
    {
        requireNonEmpty(query, "query");
        return get("/mdr/search?q=" + urlEncode(query), SearchResult.class);
    }


    /**
     * {@code GET /mdr/suggest} — search query suggestions.
     *
     * @param query
     *            the suggestion query
     */
    public SearchResult suggest(String query) throws IOException
    {
        requireNonEmpty(query, "query");
        return get("/mdr/suggest?q=" + urlEncode(query), SearchResult.class);
    }


    /** {@code GET /mdr/search/scopes} — returns available search scopes. */
    public SearchScopes getSearchScopes() throws IOException
    {
        return get("/mdr/search/scopes", SearchScopes.class);
    }


    /** {@code GET /mdr/search/scopes/{scope}} — search within a specific scope. */
    public SearchResult searchInScope(String scope) throws IOException
    {
        requireNonEmpty(scope, "scope");
        return get("/mdr/search/scopes/" + scope, SearchResult.class);
    }


    /**
     * {@code GET /mdr/search/implementedBy} — find resources that implement a given href.
     *
     * @param href
     *            the resource href to find implementations for
     */
    public SearchResult searchImplementedBy(String href) throws IOException
    {
        requireNonEmpty(href, "href");
        return get("/mdr/search/implementedBy?href=" + urlEncode(href), SearchResult.class);
    }

    // --- IG Documents ---


    /** {@code GET /mdr/documents/{document_id}} */
    public Document getDocument(String documentId) throws IOException
    {
        requireNonEmpty(documentId, "documentId");
        return get(PATH_MDR_DOCUMENTS + documentId, Document.class);
    }


    /** {@code GET /mdr/documents/{standard}/{version}/sections} */
    public DocumentSectionList getDocumentSections(String standard, String version)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_DOCUMENTS + standard + "/" + version + SEG_SECTIONS,
                DocumentSectionList.class);
    }


    /** {@code GET /mdr/documents/{standard}/{version}/{structure}/sections} */
    public DocumentSectionList getDocumentStructureSections(String standard, String version,
            String structure)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(structure, TYPE_STRUCTURE);
        return get(PATH_MDR_DOCUMENTS + standard + "/" + version + "/" + structure + SEG_SECTIONS,
                DocumentSectionList.class);
    }


    /** {@code GET /mdr/documents/{standard}/{version}/{structure}/sections/{section}} */
    public DocumentList getDocumentStructureSection(String standard, String version,
            String structure, String section)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(structure, TYPE_STRUCTURE);
        requireNonEmpty(section, TYPE_SECTION);
        return get(PATH_MDR_DOCUMENTS + standard + "/" + version + "/" + structure
                + SEG_SECTIONS_SLASH + section, DocumentList.class);
    }


    /** {@code GET /mdr/documents/{standard}/{version}/usecases} */
    public UseCaseList getDocumentUseCases(String standard, String version) throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        return get(PATH_MDR_DOCUMENTS + standard + "/" + version + "/usecases", UseCaseList.class);
    }


    /** {@code GET /mdr/documents/{standard}/{version}/usecases/{usecase}/sections} */
    public DocumentSectionList getDocumentUseCaseSections(String standard, String version,
            String useCase)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(useCase, TYPE_USE_CASE);
        return get(PATH_MDR_DOCUMENTS + standard + "/" + version + SEG_USECASES_SLASH + useCase
                + SEG_SECTIONS, DocumentSectionList.class);
    }


    /** {@code GET /mdr/documents/{standard}/{version}/usecases/{usecase}/sections/{section}} */
    public DocumentList getDocumentUseCaseSection(String standard, String version, String useCase,
            String section)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(useCase, TYPE_USE_CASE);
        requireNonEmpty(section, TYPE_SECTION);
        return get(PATH_MDR_DOCUMENTS + standard + "/" + version + SEG_USECASES_SLASH + useCase
                + SEG_SECTIONS_SLASH + section, DocumentList.class);
    }

    // --- Integrated IG Documents ---


    /** {@code GET /mdr/documents/integrated/{standard}/{version}/{subtype}/sections} */
    public DocumentSectionList getIntegratedDocumentSections(String standard, String version,
            String subtype)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(subtype, TYPE_SUBTYPE);
        return get(PATH_MDR_DOCUMENTS_INTEGRATED + standard + "/" + version + "/" + subtype
                + SEG_SECTIONS, DocumentSectionList.class);
    }


    /** {@code GET /mdr/documents/integrated/{standard}/{version}/{subtype}/{structure}/sections} */
    public DocumentSectionList getIntegratedDocumentStructureSections(String standard,
            String version, String subtype, String structure)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(subtype, TYPE_SUBTYPE);
        requireNonEmpty(structure, TYPE_STRUCTURE);
        return get(PATH_MDR_DOCUMENTS_INTEGRATED + standard + "/" + version + "/" + subtype + "/"
                + structure + SEG_SECTIONS, DocumentSectionList.class);
    }


    /**
     * {@code GET
     * /mdr/documents/integrated/{standard}/{version}/{subtype}/{structure}/sections/{section}}
     */
    public DocumentList getIntegratedDocumentStructureSection(String standard, String version,
            String subtype, String structure, String section)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(subtype, TYPE_SUBTYPE);
        requireNonEmpty(structure, TYPE_STRUCTURE);
        requireNonEmpty(section, TYPE_SECTION);
        return get(PATH_MDR_DOCUMENTS_INTEGRATED + standard + "/" + version + "/" + subtype + "/"
                + structure + SEG_SECTIONS_SLASH + section, DocumentList.class);
    }


    /**
     * {@code GET
     * /mdr/documents/integrated/{standard}/{version}/{subtype}/usecases/{usecase}/sections/{section}}
     */
    public DocumentList getIntegratedDocumentUseCaseSection(String standard, String version,
            String subtype, String useCase, String section)
        throws IOException
    {
        requireNonEmpty(standard, TYPE_STANDARD);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(subtype, TYPE_SUBTYPE);
        requireNonEmpty(useCase, TYPE_USE_CASE);
        requireNonEmpty(section, TYPE_SECTION);
        return get(
                PATH_MDR_DOCUMENTS_INTEGRATED + standard + "/" + version + "/" + subtype
                        + SEG_USECASES_SLASH + useCase + SEG_SECTIONS_SLASH + section,
                DocumentList.class);
    }

    // --- Diff ---


    /** {@code GET /mdr/diff/{product}/{version}} — diff against prior version. */
    public DiffResult getDiff(String product, String version) throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        requireNonEmpty(version, TYPE_VERSION);
        return get("/mdr/diff/" + product + "/" + version, DiffResult.class);
    }


    /**
     * {@code GET /mdr/diff/{product}/{version}/{previous}} — diff against a specific previous
     * version.
     */
    public DiffResult getDiff(String product, String version, String previous) throws IOException
    {
        requireNonEmpty(product, TYPE_PRODUCT);
        requireNonEmpty(version, TYPE_VERSION);
        requireNonEmpty(previous, "previous");
        return get("/mdr/diff/" + product + "/" + version + "/" + previous, DiffResult.class);
    }

    // --- HATEOAS link following ---


    /**
     * Follows a HATEOAS link and returns the result as a typed resource.
     *
     * @param link
     *            the link to follow (typically obtained from an ApiResource)
     * @param type
     *            the target ApiResource interface
     * @param <T>
     *            the target type
     */
    public <T extends ApiResource> T follow(Link link, Class<T> type) throws IOException
    {
        Objects.requireNonNull(link, "link must not be null");
        String href = link.href().orElseThrow(() -> new IOException("link href must not be null"));
        return get(href, type);
    }


    /** Follows a HATEOAS link and returns the result as a plain {@link ApiResource}. */
    public ApiResource follow(Link link) throws IOException
    {
        Objects.requireNonNull(link, "link must not be null");
        String href = link.href().orElseThrow(() -> new IOException("link href must not be null"));
        return get(href);
    }

    // --- Meta ---


    /** {@code GET /mdr/lastupdated} */
    public LastUpdated getLastUpdated() throws IOException
    {
        return get("/mdr/lastupdated", LastUpdated.class);
    }


    /** {@code GET /mdr/about} */
    public About getAbout() throws IOException
    {
        return get("/mdr/about", About.class);
    }


    /** {@code GET /mdr/maintenance} */
    public Maintenance getMaintenance() throws IOException
    {
        return get("/mdr/maintenance", Maintenance.class);
    }

    // --- Internal ---


    private static String expand(String endpoint, boolean expand)
    {
        if (!expand)
        {
            return endpoint;
        }
        return endpoint.contains("?") ? endpoint + "&expand=true" : endpoint + "?expand=true";
    }


    private static String urlEncode(String value)
    {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }


    private static String requireNonEmpty(String value, String paramName)
    {
        Objects.requireNonNull(value, paramName + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
        return value;
    }

    // --- Builder ---


    public static CdiscBuilder builder()
    {
        CdiscBuilder b = new CdiscBuilder().baseUrl(getApiUrl());
        String key = getApiKey();
        if (key != null)
        {
            b.apiKey(key);
        }
        return b;
    }

    public static final class CdiscBuilder extends Builder
    {

        // Optional until build(), which enforces non-null via Objects.requireNonNull.
        private @Nullable String apiKey;

        private CdiscBuilder()
        {
            baseUrl(DEFAULT_BASE_URL);
            defaultHeader("Accept", "application/json");
        }


        /** Sets the CDISC Library API key (required). */
        public CdiscBuilder apiKey(String apiKey)
        {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
            return this;
        }


        @Override
        public CdiscBuilder transport(HttpTransport transport)
        {
            super.transport(transport);
            return this;
        }


        @Override
        public CdiscBuilder baseUrl(String baseUrl)
        {
            super.baseUrl(baseUrl);
            return this;
        }


        @Override
        public CdiscBuilder cacheDir(@Nullable Path cacheDir)
        {
            super.cacheDir(cacheDir);
            return this;
        }


        @Override
        public CdiscBuilder cache(@Nullable ApiCache cache)
        {
            super.cache(cache);
            return this;
        }


        @Override
        public CdiscLibraryClient build()
        {
            Objects.requireNonNull(apiKey, "apiKey is required for CDISC Library API");
            defaultHeader("api-key", apiKey);

            if (transport == null)
            {
                transport = new JdkHttpTransport();
            }

            return new CdiscLibraryClient(this);
        }
    }
}
