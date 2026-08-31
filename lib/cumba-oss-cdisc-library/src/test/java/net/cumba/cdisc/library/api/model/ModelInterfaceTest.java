package net.cumba.cdisc.library.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import net.cumba.cdisc.library.api.model.adam.AdamDataStructure;
import net.cumba.cdisc.library.api.model.adam.AdamProduct;
import net.cumba.cdisc.library.api.model.adam.AdamVariable;
import net.cumba.cdisc.library.api.model.adam.AdamVariableSet;
import net.cumba.cdisc.library.api.model.cdash.CdashField;
import net.cumba.cdisc.library.api.model.cdash.CdashProduct;
import net.cumba.cdisc.library.api.model.cdash.CdashScenario;
import net.cumba.cdisc.library.api.model.ct.CtCodelist;
import net.cumba.cdisc.library.api.model.ct.CtPackage;
import net.cumba.cdisc.library.api.model.ct.CtPackageList;
import net.cumba.cdisc.library.api.model.ct.CtTerm;
import net.cumba.cdisc.library.api.model.diff.DiffEntry;
import net.cumba.cdisc.library.api.model.diff.DiffResult;
import net.cumba.cdisc.library.api.model.documents.Document;
import net.cumba.cdisc.library.api.model.documents.DocumentList;
import net.cumba.cdisc.library.api.model.documents.UseCaseList;
import net.cumba.cdisc.library.api.model.integrated.IntegratedProduct;
import net.cumba.cdisc.library.api.model.meta.About;
import net.cumba.cdisc.library.api.model.meta.Maintenance;
import net.cumba.cdisc.library.api.model.products.LastUpdated;
import net.cumba.cdisc.library.api.model.products.ProductGroup;
import net.cumba.cdisc.library.api.model.products.Products;
import net.cumba.cdisc.library.api.model.qrs.QrsInstrument;
import net.cumba.cdisc.library.api.model.qrs.QrsItem;
import net.cumba.cdisc.library.api.model.qrs.QrsResponse;
import net.cumba.cdisc.library.api.model.rules.ConditionOperator;
import net.cumba.cdisc.library.api.model.rules.OperationOperator;
import net.cumba.cdisc.library.api.model.rules.Rule;
import net.cumba.cdisc.library.api.model.rules.RuleAuthority;
import net.cumba.cdisc.library.api.model.rules.RuleCondition;
import net.cumba.cdisc.library.api.model.rules.RuleExecutability;
import net.cumba.cdisc.library.api.model.rules.RuleMap;
import net.cumba.cdisc.library.api.model.rules.RuleOperation;
import net.cumba.cdisc.library.api.model.rules.RuleScopeFilter;
import net.cumba.cdisc.library.api.model.rules.RuleSensitivity;
import net.cumba.cdisc.library.api.model.rules.RuleType;
import net.cumba.cdisc.library.api.model.sdtm.SdtmClass;
import net.cumba.cdisc.library.api.model.sdtm.SdtmDataset;
import net.cumba.cdisc.library.api.model.sdtm.SdtmProduct;
import net.cumba.cdisc.library.api.model.sdtm.SdtmVariable;
import net.cumba.cdisc.library.api.model.search.SearchResult;
import net.cumba.cdisc.library.api.model.search.SearchScopes;
import net.cumba.web.api.json.JsonNodeResource;
import org.junit.jupiter.api.Test;

class ModelInterfaceTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private <T extends net.cumba.web.api.ApiResource> T create(String json, Class<T> type)
    {
        try
        {
            JsonNode node = MAPPER.readTree(json);
            return JsonNodeResource.of(node, type);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // --- ADaM ---


    @Test
    void adamProductFields()
    {
        AdamProduct p = create("""
                {"name":"ADaM","label":"ADaM 2.1","version":"2-1",
                 "effectiveDate":"2023-01-01","registrationStatus":"Final",
                 "dataStructures":[{"name":"ADSL"}],
                 "_links":{"priorVersion":{"href":"/mdr/adam/adam-1-1"}}}
                """, AdamProduct.class);
        assertEquals("ADaM", p.name().orElse(null));
        assertEquals("ADaM 2.1", p.label().orElse(null));
        assertEquals("2-1", p.version().orElse(null));
        assertEquals("2023-01-01", p.effectiveDate().orElse(null));
        assertEquals(1, p.dataStructures().size());
        assertEquals("ADSL", p.dataStructures().get(0).name().orElse(null));
        assertTrue(p.priorVersionLink().isPresent());
    }


    @Test
    void adamDataStructureFields()
    {
        AdamDataStructure ds = create("""
                {"ordinal":"1","name":"ADSL","label":"Subject Level","description":"desc",
                 "class":"BDS","subClass":null,"status":"Final",
                 "analysisVariableSets":[{"name":"VS1"}],
                 "_links":{"parentProduct":{"href":"/mdr/adam/adam-2-1"}}}
                """, AdamDataStructure.class);
        assertEquals("1", ds.ordinal().orElse(""));
        assertEquals("ADSL", ds.name().orElse(null));
        assertEquals("BDS", ds.className().orElse(null));
        assertEquals(1, ds.analysisVariableSets().size());
        assertTrue(ds.parentProductLink().isPresent());
    }


    @Test
    void adamVariableFields()
    {
        AdamVariable v = create("""
                {"ordinal":"1","name":"USUBJID","label":"Subject ID",
                 "simpleDatatype":"Char","core":"Req",
                 "valueList":["A","B"],
                 "_links":{"codelist":{"href":"/mdr/ct/codelists/C123"}}}
                """, AdamVariable.class);
        assertEquals("1", v.ordinal().orElse("0"));
        assertEquals("USUBJID", v.name().orElse(null));
        assertEquals("Char", v.simpleDatatype().orElse(null));
        assertEquals(List.of("A", "B"), v.valueList());
        assertTrue(v.codelistLink().isPresent());
    }


    @Test
    void adamVariableSetFields()
    {
        AdamVariableSet vs = create("""
                {"ordinal":1,"name":"VS1","label":"Variable Set 1",
                 "analysisVariables":[{"name":"VAR1"},{"name":"VAR2"}]}
                """, AdamVariableSet.class);
        assertEquals("VS1", vs.name().orElse(null));
        assertEquals(2, vs.analysisVariables().size());
    }

    // --- SDTM ---


    @Test
    void sdtmProductFields()
    {
        SdtmProduct p = create("""
                {"name":"SDTM","label":"SDTM 2.0","version":"2-0",
                 "classes":[{"name":"Events"}],
                 "_links":{"model":{"href":"/mdr/sdtm/2-0"}}}
                """, SdtmProduct.class);
        assertEquals("SDTM", p.name().orElse(null));
        assertEquals(1, p.classes().size());
        assertTrue(p.modelLink().isPresent());
    }


    @Test
    void sdtmDatasetFields()
    {
        SdtmDataset ds = create("""
                {"ordinal":1,"name":"AE","label":"Adverse Events",
                 "datasetStructure":"One record per event","status":"Final",
                 "datasetVariables":[{"name":"AETERM"}],
                 "_links":{"parentClass":{"href":"/mdr/sdtm/classes/Events"}}}
                """, SdtmDataset.class);
        assertEquals("AE", ds.name().orElse(null));
        assertEquals("One record per event", ds.datasetStructure().orElse(null));
        assertEquals(1, ds.datasetVariables().size());
        assertTrue(ds.parentClassLink().isPresent());
    }


    @Test
    void sdtmVariableFields()
    {
        SdtmVariable v = create("""
                {"ordinal":1,"name":"AETERM","label":"Reported Term",
                 "role":"Topic","simpleDatatype":"Char","core":"Req",
                 "valueList":[],
                 "_links":{"codelist":{"href":"/c"},"qualifiesVariables":[{"href":"/v1"}]}}
                """, SdtmVariable.class);
        assertEquals("AETERM", v.name().orElse(null));
        assertEquals("Topic", v.role().orElse(null));
        assertEquals("Req", v.core().orElse(null));
        assertTrue(v.codelistLink().isPresent());
        assertEquals(1, v.qualifiesVariableLinks().size());
    }


    @Test
    void sdtmClassFields()
    {
        SdtmClass cls = create("""
                {"ordinal":1,"name":"Events","label":"Events",
                 "classVariables":[{"name":"DOMAIN"}],
                 "datasets":[{"name":"AE"}],
                 "_links":{"subclasses":[{"href":"/s1"}]}}
                """, SdtmClass.class);
        assertEquals("Events", cls.name().orElse(null));
        assertEquals(1, cls.classVariables().size());
        assertEquals(1, cls.datasets().size());
        assertEquals(1, cls.subclassLinks().size());
    }

    // --- CT ---


    @Test
    void ctPackageFields()
    {
        CtPackage pkg = create("""
                {"name":"sdtmct-2023-12-15","effectiveDate":"2023-12-15","version":"2023-12-15",
                 "codelists":[{"conceptId":"C66729","name":"AGE UNIT"}]}
                """, CtPackage.class);
        assertEquals("sdtmct-2023-12-15", pkg.name().orElse(null));
        assertEquals(1, pkg.codelists().size());
        assertEquals("C66729", pkg.codelists().get(0).conceptId().orElse(null));
    }


    @Test
    void ctCodelistFields()
    {
        CtCodelist cl = create("""
                {"conceptId":"C66729","extensible":true,"name":"AGE UNIT",
                 "submissionValue":"AGEU","definition":"Units for age",
                 "synonyms":["syn1"],
                 "terms":[{"submissionValue":"YEARS"}]}
                """, CtCodelist.class);
        assertEquals("C66729", cl.conceptId().orElse(null));
        assertTrue(cl.extensible().orElse(false));
        assertEquals("AGEU", cl.submissionValue().orElse(null));
        assertEquals(List.of("syn1"), cl.synonyms());
        assertEquals(1, cl.terms().size());
    }


    /**
     * Fix B. The CDISC Library serialises {@code extensible} as a JSON <em>string</em>, which a
     * strict boolean read rejects — leaving every live-API codelist with an empty extensibility.
     * Both wire forms must resolve, so the live-API and pickle-backed paths agree.
     */
    @Test
    void ctCodelistExtensibleAcceptsStringAndBooleanWireForms()
    {
        // The form the live API actually sends. orElseThrow asserts presence and value together —
        // before Fix B these were all Optional.empty().
        assertFalse(
                create("{\"extensible\":\"false\"}", CtCodelist.class).extensible().orElseThrow());
        assertTrue(
                create("{\"extensible\":\"true\"}", CtCodelist.class).extensible().orElseThrow());
        // Case-insensitive and tolerant of padding.
        assertTrue(
                create("{\"extensible\":\" TRUE \"}", CtCodelist.class).extensible().orElseThrow());
        // The form the Python engine's pickles carry.
        assertFalse(create("{\"extensible\":false}", CtCodelist.class).extensible().orElseThrow());
        assertTrue(create("{\"extensible\":true}", CtCodelist.class).extensible().orElseThrow());
    }


    /** Anything that is not a recognisable boolean stays empty rather than silently false. */
    @Test
    void ctCodelistExtensibleStaysEmptyForUnusableValues()
    {
        assertTrue(create("{\"conceptId\":\"C1\"}", CtCodelist.class).extensible().isEmpty());
        assertTrue(create("{\"extensible\":\"\"}", CtCodelist.class).extensible().isEmpty());
        assertTrue(create("{\"extensible\":\"yes\"}", CtCodelist.class).extensible().isEmpty());
        assertTrue(create("{\"extensible\":1}", CtCodelist.class).extensible().isEmpty());
    }


    @Test
    void ctTermFields()
    {
        CtTerm t = create("""
                {"conceptId":"C29848","submissionValue":"YEARS",
                 "definition":"A period of time","preferredTerm":"Year",
                 "synonyms":["yr","year"]}
                """, CtTerm.class);
        assertEquals("C29848", t.conceptId().orElse(null));
        assertEquals("YEARS", t.submissionValue().orElse(null));
        assertEquals(List.of("yr", "year"), t.synonyms());
    }


    @Test
    void ctPackageListFields()
    {
        CtPackageList pl = create("""
                {"_links":{"packages":[{"href":"/mdr/ct/packages/sdtmct-2023-12-15"}]}}
                """, CtPackageList.class);
        assertEquals(1, pl.packageLinks().size());
    }

    // --- CDASH ---


    @Test
    void cdashProductFields()
    {
        CdashProduct p = create("""
                {"name":"CDASH","version":"2-2",
                 "classes":[{"name":"Events"}],
                 "domains":[{"name":"AE"}]}
                """, CdashProduct.class);
        assertEquals("CDASH", p.name().orElse(null));
        assertEquals(1, p.classes().size());
        assertEquals(1, p.domains().size());
    }


    @Test
    void cdashFieldFields()
    {
        CdashField f = create("""
                {"ordinal":1,"name":"AETERM","label":"AE Term",
                 "definition":"def","questionText":"What event?",
                 "core":"HR/C","domainSpecific":true,
                 "_links":{"sdtmClassMappingTargets":[{"href":"/v1"}],
                           "sdtmDatasetMappingTargets":[],
                           "sdtmigDatasetMappingTargets":[]}}
                """, CdashField.class);
        assertEquals("AETERM", f.name().orElse(null));
        assertEquals("What event?", f.questionText().orElse(null));
        assertTrue(f.domainSpecific().orElse(false));
        assertEquals(1, f.sdtmClassMappingTargetLinks().size());
    }


    @Test
    void cdashScenarioFields()
    {
        CdashScenario s = create("""
                {"ordinal":1,"domain":"AE","domainName":"Adverse Events",
                 "scenario":"Scenario 1","fields":[{"name":"AETERM"}]}
                """, CdashScenario.class);
        assertEquals("AE", s.domain().orElse(null));
        assertEquals("Scenario 1", s.scenario().orElse(null));
        assertEquals(1, s.fields().size());
    }

    // --- Meta ---


    @Test
    void aboutFields()
    {
        About a = create("""
                {"release-notes":"v1.0","api-documentation":"https://docs.example.com"}
                """, About.class);
        assertEquals("v1.0", a.releaseNotes().orElse(null));
        assertEquals("https://docs.example.com", a.apiDocumentation().orElse(null));
    }


    @Test
    void maintenanceFields()
    {
        Maintenance m = create("""
                {"maintenanceMode":true,"maintenanceMessage":"Under maintenance"}
                """, Maintenance.class);
        assertTrue(m.maintenanceMode().orElse(false));
        assertEquals("Under maintenance", m.maintenanceMessage().orElse(null));
    }

    // --- Products ---


    @Test
    void productsExtractsNestedLinks()
    {
        // Products navigates _links > group > standard, where each standard value is
        // an array of link objects. The _links accessor in ApiResource expects objects
        // with "href" property inside the _links top-level object.
        // Products.adamLinks() does: getObject("_links") -> getObject("data-analysis") ->
        // getLinks("adam")
        // The getLinks("adam") expects _links.adam to be an array inside the data-analysis object.
        // But data-analysis is NOT itself wrapped in _links within the nested object.
        // So we need _links.data-analysis to be an object, and within that object, adam to have
        // _links.adam as links - but that's not how it works. Let me trace the actual
        // implementation:
        // Products.adamLinks() calls getObject("_links", ApiResource.class) which reads the _links
        // field as an ApiResource, then calls getObject("data-analysis", ApiResource.class) on
        // that,
        // then calls getLinks("adam") on that. getLinks looks for _links.adam inside data-analysis
        // obj.
        // So the structure needs to be: _links -> data-analysis -> _links -> adam -> [{href:...}]
        Products p = create(
                """
                        {"_links":{"data-analysis":{"_links":{"adam":[{"href":"/mdr/adam/adam-2-1","title":"ADaM 2.1"}]}},
                                   "data-tabulation":{"_links":{"sdtm":[{"href":"/mdr/sdtm/2-0"}],
                                                      "sdtmig":[{"href":"/mdr/sdtmig/3-4"}],
                                                      "sendig":[]}},
                                   "data-collection":{"_links":{"cdash":[],"cdashig":[]}},
                                   "terminology":{"_links":{"packages":[]}},
                                   "qrs":{"_links":{"instrument":[]}}}}
                        """,
                Products.class);
        assertEquals(1, p.adamLinks().size());
        assertEquals("ADaM 2.1", p.adamLinks().get(0).title().orElse(null));
        assertEquals(1, p.sdtmLinks().size());
        assertEquals(1, p.sdtmigLinks().size());
        assertTrue(p.sendigLinks().isEmpty());
        assertTrue(p.cdashLinks().isEmpty());
        // allLinks should combine all groups
        assertEquals(3, p.allLinks().size());
    }


    @Test
    void productsReturnsEmptyOnMissingStructure()
    {
        Products p = create("{}", Products.class);
        assertTrue(p.adamLinks().isEmpty());
        assertTrue(p.sdtmLinks().isEmpty());
        assertTrue(p.allLinks().isEmpty());
    }


    @Test
    void lastUpdatedFields()
    {
        LastUpdated lu = create("""
                {"overall":"2025-01-01","data-analysis":"2025-01-02",
                 "terminology":"2025-01-03"}
                """, LastUpdated.class);
        assertEquals("2025-01-01", lu.overall().orElse(null));
        assertEquals("2025-01-02", lu.dataAnalysis().orElse(null));
        assertEquals("2025-01-03", lu.terminology().orElse(null));
    }


    @Test
    void productGroupFields()
    {
        ProductGroup pg = create("""
                {"_links":{"adam":[{"href":"/a"}],"sdtm":[],"sdtmig":[],
                           "sendig":[],"cdash":[],"cdashig":[],
                           "packages":[],"instrument":[]}}
                """, ProductGroup.class);
        assertEquals(1, pg.adamLinks().size());
    }

    // --- QRS ---


    @Test
    void qrsInstrumentFields()
    {
        QrsInstrument qi = create("""
                {"name":"PHQ-9","label":"PHQ-9","instrumentType":"PRO",
                 "version":"1-0","copyrightStatus":"Public",
                 "responseGroups":[{"name":"RG1"}],
                 "items":[{"label":"Item 1"}]}
                """, QrsInstrument.class);
        assertEquals("PHQ-9", qi.name().orElse(null));
        assertEquals("PRO", qi.instrumentType().orElse(null));
        assertEquals(1, qi.responseGroups().size());
        assertEquals(1, qi.items().size());
    }


    @Test
    void qrsItemFields()
    {
        QrsItem qi = create("""
                {"ordinal":1,"label":"How often?","questionText":"How often?","itemCode":"Q1"}
                """, QrsItem.class);
        assertEquals(1, qi.ordinal().orElse(-1));
        assertEquals("Q1", qi.itemCode().orElse(null));
    }


    @Test
    void qrsResponseFields()
    {
        QrsResponse qr = create("""
                {"ordinal":0,"isStandardResultNumeric":true}
                """, QrsResponse.class);
        assertEquals(0, qr.ordinal().orElse(-1));
        assertTrue(qr.isStandardResultNumeric().orElse(false));
    }

    // --- Rules ---


    @Test
    void ruleFields()
    {
        Rule r = create("""
                {"id":"uuid-123","Description":"Test rule",
                 "Rule_Type":"Record Data","Sensitivity":"Record",
                 "Executability":"Fully Executable",
                 "Core":{"Id":"CORE-000351","Version":"1","Status":"Published"},
                 "Authorities":[{"Organization":"CDISC"}],
                 "Scope":{"Classes":{"Include":["ALL"],"Exclude":[]}},
                 "Check":{"all":[{"name":"USUBJID","operator":"is_unique_set"}]},
                 "Outcome":{"Message":"USUBJID not unique","Output_Variables":["USUBJID"]},
                 "Grouping_Variables":["STUDYID"],
                 "Operations":[{"id":"op1","operator":"distinct"}]}
                """, Rule.class);
        assertEquals("uuid-123", r.id().orElse(null));
        assertEquals("Test rule", r.description().orElse(null));
        assertEquals(RuleType.RECORD_DATA, r.ruleTypeEnum());
        assertEquals(RuleSensitivity.RECORD, r.sensitivityEnum());
        assertEquals(RuleExecutability.FULLY_EXECUTABLE, r.executabilityEnum());
        assertTrue(r.core().isPresent());
        assertEquals("CORE-000351", r.core().get().id().orElse(null));
        assertEquals(1, r.authorities().size());
        assertTrue(r.scope().isPresent());
        assertTrue(r.check().isPresent());
        assertEquals(1, r.check().get().all().size());
        assertTrue(r.outcome().isPresent());
        assertEquals("USUBJID not unique", r.outcome().get().message().orElse(null));
        assertEquals(List.of("USUBJID"), r.outcome().get().outputVariables());
        assertEquals(List.of("STUDYID"), r.groupingVariables());
        assertEquals(1, r.operations().size());
    }


    @Test
    void ruleConditionFields()
    {
        RuleCondition rc = create("""
                {"name":"USUBJID","operator":"is_unique_set",
                 "value":"test","value_is_literal":true,
                 "negative":false,"within":"STUDYID",
                 "any":[{"name":"sub1"}],
                 "all":[]}
                """, RuleCondition.class);
        assertEquals("USUBJID", rc.name().orElse(null));
        assertEquals(ConditionOperator.IS_UNIQUE_SET, rc.operatorEnum());
        assertEquals("test", rc.valueString().orElse(null));
        assertTrue(rc.valueIsLiteral().orElse(false));
        assertFalse(rc.negative().orElse(true));
        assertEquals("STUDYID", rc.within().orElse(null));
        assertEquals(1, rc.any().size());
        assertTrue(rc.all().isEmpty());
    }


    @Test
    void ruleMapFields()
    {
        RuleMap rm = create("""
                {"rule-1":{"id":"r1","Description":"Rule 1"},
                 "rule-2":{"id":"r2","Description":"Rule 2"}}
                """, RuleMap.class);
        assertEquals(2, rm.size());
        assertFalse(rm.isEmpty());
        assertTrue(rm.containsKey("rule-1"));
        assertFalse(rm.containsKey("rule-3"));
        assertEquals(2, rm.keys().size());
        assertEquals(2, rm.values().size());
        assertTrue(rm.get("rule-1").isPresent());
        assertEquals("r1", rm.get("rule-1").get().id().orElse(null));
        assertTrue(rm.get("nonexistent").isEmpty());
    }


    @Test
    void ruleMapEmpty()
    {
        RuleMap rm = create("{}", RuleMap.class);
        assertEquals(0, rm.size());
        assertTrue(rm.isEmpty());
        assertTrue(rm.keys().isEmpty());
        assertTrue(rm.values().isEmpty());
    }


    @Test
    void ruleOperationFields()
    {
        RuleOperation op = create("""
                {"id":"op1","name":"count","operator":"record_count"}
                """, RuleOperation.class);
        assertEquals("op1", op.id().orElse(null));
        assertEquals(OperationOperator.RECORD_COUNT, op.operatorEnum());
    }


    @Test
    void ruleScopeFilterFields()
    {
        RuleScopeFilter f = create("""
                {"Include":["ALL"],"Exclude":["TS","TA"]}
                """, RuleScopeFilter.class);
        assertEquals(List.of("ALL"), f.include());
        assertEquals(List.of("TS", "TA"), f.exclude());
    }


    @Test
    void ruleAuthorityFields()
    {
        RuleAuthority a = create("""
                {"Organization":"CDISC","Standards":[{"Name":"SDTM","Version":"3.4"}]}
                """, RuleAuthority.class);
        assertEquals("CDISC", a.organization().orElse(null));
        assertEquals(1, a.standards().size());
        assertEquals("SDTM", a.standards().get(0).name().orElse(null));
    }

    // --- Diff ---


    @Test
    void diffResultFields()
    {
        DiffResult dr = create("""
                {"diff":[{"title":"Variables","head":["Name","Change"],
                          "body":[[["A","Added"]]]}]}
                """, DiffResult.class);
        assertEquals(1, dr.diff().size());
        DiffEntry entry = dr.diff().get(0);
        assertEquals("Variables", entry.title().orElse(null));
        assertEquals(List.of("Name", "Change"), entry.head());
        assertTrue(entry.body().isPresent());
    }

    // --- Documents ---


    @Test
    void documentFields()
    {
        Document d = create("""
                {"id":"doc-1","title":"AE Domain","section":"6.1",
                 "html":"<p>Content</p>","children":["c1","c2"],
                 "structures":["Events"]}
                """, Document.class);
        assertEquals("doc-1", d.id().orElse(null));
        assertEquals("AE Domain", d.title().orElse(null));
        assertEquals("<p>Content</p>", d.html().orElse(null));
        assertEquals(List.of("c1", "c2"), d.children());
        assertEquals(List.of("Events"), d.structures());
    }


    @Test
    void documentListFields()
    {
        DocumentList dl = create("""
                {"documents":[{"id":"d1"},{"id":"d2"}]}
                """, DocumentList.class);
        assertEquals(2, dl.documents().size());
    }


    @Test
    void useCaseListFields()
    {
        UseCaseList ul = create("""
                {"useCases":["CLIN","NONCLIN"]}
                """, UseCaseList.class);
        assertEquals(List.of("CLIN", "NONCLIN"), ul.useCases());
    }


    @Test
    void searchScopesFields()
    {
        SearchScopes ss = create("""
                {"scopes":["adam","sdtm","ct"]}
                """, SearchScopes.class);
        assertEquals(List.of("adam", "sdtm", "ct"), ss.scopes());
    }


    @Test
    void searchResultFields()
    {
        SearchResult sr = create("""
                {"_links":{"searchResults":[{"href":"/mdr/adam/adam-2-1","title":"ADaM 2.1"}]}}
                """, SearchResult.class);
        assertEquals(1, sr.searchResultLinks().size());
    }

    // --- Integrated ---


    @Test
    void integratedProductFields()
    {
        IntegratedProduct ip = create("""
                {"name":"SDTM-IG","version":"3-4",
                 "_links":{"standards":[{"href":"/s1"}],"models":[{"href":"/m1"}]}}
                """, IntegratedProduct.class);
        assertEquals("SDTM-IG", ip.name().orElse(null));
        assertEquals(1, ip.standardLinks().size());
        assertEquals(1, ip.modelLinks().size());
    }

    // --- Empty/missing field safety ---


    @Test
    void missingFieldsReturnEmptyOptionals()
    {
        AdamProduct p = create("{}", AdamProduct.class);
        assertTrue(p.name().isEmpty());
        assertTrue(p.label().isEmpty());
        assertTrue(p.description().isEmpty());
        assertTrue(p.version().isEmpty());
        assertTrue(p.priorVersionLink().isEmpty());
        assertTrue(p.dataStructures().isEmpty());
    }


    @Test
    void missingLinkFieldsReturnEmptyLists()
    {
        SdtmClass cls = create("{}", SdtmClass.class);
        assertTrue(cls.subclassLinks().isEmpty());
        assertTrue(cls.classVariables().isEmpty());
        assertTrue(cls.datasets().isEmpty());
        assertTrue(cls.ordinal().isEmpty());
    }


    @Test
    void ruleWithUnknownEnumValues()
    {
        Rule r = create("""
                {"Rule_Type":"Future Type","Sensitivity":"Unknown Level",
                 "Executability":"New Mode"}
                """, Rule.class);
        assertEquals(RuleType.UNKNOWN, r.ruleTypeEnum());
        assertEquals(RuleSensitivity.UNKNOWN, r.sensitivityEnum());
        assertEquals(RuleExecutability.UNKNOWN, r.executabilityEnum());
    }


    @Test
    void ruleWithMissingEnumFields()
    {
        Rule r = create("{}", Rule.class);
        assertEquals(RuleType.UNKNOWN, r.ruleTypeEnum());
        assertEquals(RuleSensitivity.UNKNOWN, r.sensitivityEnum());
        assertEquals(RuleExecutability.UNKNOWN, r.executabilityEnum());
    }
}
