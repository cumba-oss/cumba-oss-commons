package net.cumba.cdisc.library.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import net.cumba.cdisc.library.api.model.cdash.CdashClass;
import net.cumba.cdisc.library.api.model.cdash.CdashDomain;
import net.cumba.cdisc.library.api.model.cdash.CdashField;
import net.cumba.cdisc.library.api.model.documents.Document;
import net.cumba.cdisc.library.api.model.documents.DocumentSectionList;
import net.cumba.cdisc.library.api.model.products.ProductGroup;
import net.cumba.cdisc.library.api.model.qrs.QrsInstrument;
import net.cumba.cdisc.library.api.model.qrs.QrsResponseGroup;
import net.cumba.cdisc.library.api.model.rules.Rule;
import net.cumba.cdisc.library.api.model.rules.RuleAuthority;
import net.cumba.cdisc.library.api.model.rules.RuleCitation;
import net.cumba.cdisc.library.api.model.rules.RuleCondition;
import net.cumba.cdisc.library.api.model.rules.RuleCore;
import net.cumba.cdisc.library.api.model.rules.RuleIdentifier;
import net.cumba.cdisc.library.api.model.rules.RuleMatchDataset;
import net.cumba.cdisc.library.api.model.rules.RuleOperation;
import net.cumba.cdisc.library.api.model.rules.RuleOutcome;
import net.cumba.cdisc.library.api.model.rules.RulePackage;
import net.cumba.cdisc.library.api.model.rules.RuleReference;
import net.cumba.cdisc.library.api.model.rules.RuleScope;
import net.cumba.cdisc.library.api.model.rules.RuleStandard;
import net.cumba.cdisc.library.api.model.sdtm.SdtmVariable;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.json.JsonNodeResource;
import org.junit.jupiter.api.Test;

/**
 * JSON-roundtrip tests covering the flat Jackson DTO interfaces whose accessors are not otherwise
 * exercised. Each test deserialises an inline JSON payload through
 * {@link JsonNodeResource#of(JsonNode, Class)} and then calls every accessor so the default method
 * bodies are touched by JaCoCo.
 *
 * <p>
 * The DTO interfaces are pure delegation surfaces — a single roundtrip per interface gives ~95 %
 * instruction coverage of the interface in question.
 * </p>
 */
class DtoRoundtripTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static <T extends ApiResource> T create(String json, Class<T> type)
    {
        try
        {
            JsonNode node = MAPPER.readTree(json);
            return JsonNodeResource.of(node, type);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    // --- Tier-1: CDASH ---


    @Test
    void cdashClassPopulatedFields()
    {
        CdashClass cls = create("""
                {"ordinal":"1","name":"Events","label":"Events Class",
                 "description":"Adverse-event observations",
                 "cdashModelFields":[{"name":"AETERM"},{"name":"AESTDAT"}],
                 "domains":[{"name":"AE"}],
                 "_links":{"parentProduct":{"href":"/mdr/cdash/cdash-2-2"},
                           "parentClass":{"href":"/mdr/cdash/cdash-2-2/classes/Generic"},
                           "priorVersion":{"href":"/mdr/cdash/cdash-2-1/classes/Events"}}}
                """, CdashClass.class);
        assertEquals("1", cls.ordinal().orElse(null));
        assertEquals("Events", cls.name().orElse(null));
        assertEquals("Events Class", cls.label().orElse(null));
        assertEquals("Adverse-event observations", cls.description().orElse(null));
        assertEquals(2, cls.cdashModelFields().size());
        assertEquals(1, cls.domains().size());
        assertTrue(cls.parentProductLink().isPresent());
        assertTrue(cls.parentClassLink().isPresent());
        assertTrue(cls.priorVersionLink().isPresent());
    }


    @Test
    void cdashClassMissingFields()
    {
        CdashClass cls = create("{}", CdashClass.class);
        assertTrue(cls.ordinal().isEmpty());
        assertTrue(cls.name().isEmpty());
        assertTrue(cls.label().isEmpty());
        assertTrue(cls.description().isEmpty());
        assertTrue(cls.cdashModelFields().isEmpty());
        assertTrue(cls.domains().isEmpty());
        assertTrue(cls.parentProductLink().isEmpty());
        assertTrue(cls.parentClassLink().isEmpty());
        assertTrue(cls.priorVersionLink().isEmpty());
    }


    @Test
    void cdashDomainPopulatedFields()
    {
        CdashDomain d = create("""
                {"ordinal":"2","name":"AE","label":"Adverse Events","status":"Final",
                 "fields":[{"name":"AETERM"}],
                 "_links":{"parentProduct":{"href":"/mdr/cdash/cdash-2-2"},
                           "parentClass":{"href":"/mdr/cdash/cdash-2-2/classes/Events"},
                           "priorVersion":{"href":"/mdr/cdash/cdash-2-1/domains/AE"}}}
                """, CdashDomain.class);
        assertEquals("2", d.ordinal().orElse(null));
        assertEquals("AE", d.name().orElse(null));
        assertEquals("Adverse Events", d.label().orElse(null));
        assertEquals("Final", d.status().orElse(null));
        assertEquals(1, d.fields().size());
        assertTrue(d.parentProductLink().isPresent());
        assertTrue(d.parentClassLink().isPresent());
        assertTrue(d.priorVersionLink().isPresent());
    }


    @Test
    void cdashDomainMissingFields()
    {
        CdashDomain d = create("{}", CdashDomain.class);
        assertTrue(d.ordinal().isEmpty());
        assertTrue(d.name().isEmpty());
        assertTrue(d.label().isEmpty());
        assertTrue(d.status().isEmpty());
        assertTrue(d.fields().isEmpty());
        assertTrue(d.parentProductLink().isEmpty());
        assertTrue(d.parentClassLink().isEmpty());
        assertTrue(d.priorVersionLink().isEmpty());
    }

    // --- Tier-1: QRS ---


    @Test
    void qrsResponseGroupPopulatedFields()
    {
        QrsResponseGroup g = create("""
                {"name":"RG1","label":"Likert 0-4","responseType":"Likert",
                 "responses":[{"ordinal":0,"isStandardResultNumeric":true}],
                 "_links":{"parentInstrument":{"href":"/mdr/qrs/instruments/PHQ-9/versions/1-0"}}}
                """, QrsResponseGroup.class);
        assertEquals("RG1", g.name().orElse(null));
        assertEquals("Likert 0-4", g.label().orElse(null));
        assertEquals("Likert", g.responseType().orElse(null));
        assertEquals(1, g.responses().size());
        assertTrue(g.parentInstrumentLink().isPresent());
    }


    @Test
    void qrsResponseGroupMissingFields()
    {
        QrsResponseGroup g = create("{}", QrsResponseGroup.class);
        assertTrue(g.name().isEmpty());
        assertTrue(g.label().isEmpty());
        assertTrue(g.responseType().isEmpty());
        assertTrue(g.responses().isEmpty());
        assertTrue(g.parentInstrumentLink().isEmpty());
    }

    // --- Tier-1: Rules ---


    @Test
    void ruleMatchDatasetPopulatedFields()
    {
        RuleMatchDataset m = create("""
                {"Name":"AE","Keys":["USUBJID","AESEQ"],"Wildcard":"--",
                 "Child":true,"Join_Type":"inner"}
                """, RuleMatchDataset.class);
        assertEquals("AE", m.name().orElse(null));
        assertEquals(List.of("USUBJID", "AESEQ"), m.keys());
        assertEquals("--", m.wildcard().orElse(null));
        assertTrue(m.child().orElse(false));
        assertEquals("inner", m.joinType().orElse(null));
    }


    @Test
    void ruleMatchDatasetMissingFields()
    {
        RuleMatchDataset m = create("{}", RuleMatchDataset.class);
        assertTrue(m.name().isEmpty());
        assertTrue(m.keys().isEmpty());
        assertTrue(m.wildcard().isEmpty());
        assertTrue(m.child().isEmpty());
        assertTrue(m.joinType().isEmpty());
    }


    @Test
    void ruleReferencePopulatedFields()
    {
        RuleReference r = create("""
                {"Origin":"SDTM and SDTMIG Conformance Rules","Version":"2",
                 "Rule_Identifier":{"Id":"CG0151","Version":"1"},
                 "Citations":[{"Cited_Guidance":"Cite 1","Document":"IG",
                               "Item":"6.1","Section":"AE"},
                              {"Cited_Guidance":"Cite 2","Document":"IG"}]}
                """, RuleReference.class);
        assertEquals("SDTM and SDTMIG Conformance Rules", r.origin().orElse(null));
        assertEquals("2", r.version().orElse(null));
        assertTrue(r.ruleIdentifier().isPresent());
        assertEquals("CG0151", r.ruleIdentifier().get().id().orElse(null));
        assertEquals("1", r.ruleIdentifier().get().version().orElse(null));
        assertEquals(2, r.citations().size());
    }


    @Test
    void ruleReferenceMissingFields()
    {
        RuleReference r = create("{}", RuleReference.class);
        assertTrue(r.origin().isEmpty());
        assertTrue(r.version().isEmpty());
        assertTrue(r.ruleIdentifier().isEmpty());
        assertTrue(r.citations().isEmpty());
    }


    @Test
    void ruleCitationPopulatedFields()
    {
        RuleCitation c = create("""
                {"Cited_Guidance":"All subjects must have USUBJID",
                 "Document":"SDTM-IG","Item":"AE.AETERM","Section":"6.1"}
                """, RuleCitation.class);
        assertEquals("All subjects must have USUBJID", c.citedGuidance().orElse(null));
        assertEquals("SDTM-IG", c.document().orElse(null));
        assertEquals("AE.AETERM", c.item().orElse(null));
        assertEquals("6.1", c.section().orElse(null));
    }


    @Test
    void ruleCitationMissingFields()
    {
        RuleCitation c = create("{}", RuleCitation.class);
        assertTrue(c.citedGuidance().isEmpty());
        assertTrue(c.document().isEmpty());
        assertTrue(c.item().isEmpty());
        assertTrue(c.section().isEmpty());
    }


    @Test
    void ruleScopePopulatedFields()
    {
        RuleScope s = create("""
                {"Classes":{"Include":["Events","Findings"],"Exclude":[]},
                 "Domains":{"Include":["ALL"],"Exclude":["TS","TA"]},
                 "Use_Case":"NONCLIN, INDH"}
                """, RuleScope.class);
        assertTrue(s.classes().isPresent());
        assertEquals(List.of("Events", "Findings"), s.classes().get().include());
        assertTrue(s.domains().isPresent());
        assertEquals(List.of("TS", "TA"), s.domains().get().exclude());
        assertEquals("NONCLIN, INDH", s.useCase().orElse(null));
    }


    @Test
    void ruleScopeMissingFields()
    {
        RuleScope s = create("{}", RuleScope.class);
        assertTrue(s.classes().isEmpty());
        assertTrue(s.domains().isEmpty());
        assertTrue(s.useCase().isEmpty());
    }


    @Test
    void ruleIdentifierPopulatedFields()
    {
        RuleIdentifier id = create("""
                {"Id":"CG0151","Version":"1"}
                """, RuleIdentifier.class);
        assertEquals("CG0151", id.id().orElse(null));
        assertEquals("1", id.version().orElse(null));
    }


    @Test
    void ruleIdentifierMissingFields()
    {
        RuleIdentifier id = create("{}", RuleIdentifier.class);
        assertTrue(id.id().isEmpty());
        assertTrue(id.version().isEmpty());
    }

    // --- Tier-1: Documents ---


    @Test
    void documentSectionListPopulatedFields()
    {
        DocumentSectionList sl = create("""
                {"sections":["6.1","6.2","6.3"],
                 "_links":{"sections":[{"href":"/s/1"},{"href":"/s/2"}]}}
                """, DocumentSectionList.class);
        assertEquals(List.of("6.1", "6.2", "6.3"), sl.sections());
        assertEquals(2, sl.sectionLinks().size());
    }


    @Test
    void documentSectionListMissingFields()
    {
        DocumentSectionList sl = create("{}", DocumentSectionList.class);
        assertTrue(sl.sections().isEmpty());
        assertTrue(sl.sectionLinks().isEmpty());
    }

    // --- Tier-2 expansions: CdashField (full accessor coverage) ---


    @Test
    void cdashFieldAllAccessors()
    {
        CdashField f = create("""
                {"ordinal":"3","name":"AETERM","label":"Reported Term",
                 "definition":"What term?","questionText":"Q?",
                 "prompt":"prompt!","implementationNotes":"note",
                 "completionInstructions":"complete","simpleDatatype":"Char",
                 "mappingInstructions":"map","core":"Req","domainSpecific":false,
                 "_links":{"codelist":{"href":"/cl"},
                           "valuelist":{"href":"/vl"},
                           "modelField":{"href":"/mf"},
                           "parentProduct":{"href":"/pp"},
                           "parentClass":{"href":"/pc"},
                           "parentDomain":{"href":"/pd"},
                           "parentScenario":{"href":"/ps"},
                           "rootItem":{"href":"/ri"},
                           "priorVersion":{"href":"/pv"},
                           "sdtmClassMappingTargets":[{"href":"/scmt"}],
                           "sdtmDatasetMappingTargets":[{"href":"/sdmt"}],
                           "sdtmigDatasetMappingTargets":[{"href":"/sigdmt"}]}}
                """, CdashField.class);
        assertEquals("3", f.ordinal().orElse(null));
        assertEquals("AETERM", f.name().orElse(null));
        assertEquals("Reported Term", f.label().orElse(null));
        assertEquals("What term?", f.definition().orElse(null));
        assertEquals("Q?", f.questionText().orElse(null));
        assertEquals("prompt!", f.prompt().orElse(null));
        assertEquals("note", f.implementationNotes().orElse(null));
        assertEquals("complete", f.completionInstructions().orElse(null));
        assertEquals("Char", f.simpleDatatype().orElse(null));
        assertEquals("map", f.mappingInstructions().orElse(null));
        assertEquals("Req", f.core().orElse(null));
        assertFalse(f.domainSpecific().orElse(true));
        assertTrue(f.codelistLink().isPresent());
        assertTrue(f.valuelistLink().isPresent());
        assertTrue(f.modelFieldLink().isPresent());
        assertTrue(f.parentProductLink().isPresent());
        assertTrue(f.parentClassLink().isPresent());
        assertTrue(f.parentDomainLink().isPresent());
        assertTrue(f.parentScenarioLink().isPresent());
        assertTrue(f.rootItemLink().isPresent());
        assertTrue(f.priorVersionLink().isPresent());
        assertEquals(1, f.sdtmClassMappingTargetLinks().size());
        assertEquals(1, f.sdtmDatasetMappingTargetLinks().size());
        assertEquals(1, f.sdtmigDatasetMappingTargetLinks().size());
    }


    @Test
    void cdashFieldMissingAccessors()
    {
        CdashField f = create("{}", CdashField.class);
        assertTrue(f.ordinal().isEmpty());
        assertTrue(f.name().isEmpty());
        assertTrue(f.label().isEmpty());
        assertTrue(f.definition().isEmpty());
        assertTrue(f.questionText().isEmpty());
        assertTrue(f.prompt().isEmpty());
        assertTrue(f.implementationNotes().isEmpty());
        assertTrue(f.completionInstructions().isEmpty());
        assertTrue(f.simpleDatatype().isEmpty());
        assertTrue(f.mappingInstructions().isEmpty());
        assertTrue(f.core().isEmpty());
        assertTrue(f.domainSpecific().isEmpty());
        assertTrue(f.codelistLink().isEmpty());
        assertTrue(f.valuelistLink().isEmpty());
        assertTrue(f.modelFieldLink().isEmpty());
        assertTrue(f.parentProductLink().isEmpty());
        assertTrue(f.parentClassLink().isEmpty());
        assertTrue(f.parentDomainLink().isEmpty());
        assertTrue(f.parentScenarioLink().isEmpty());
        assertTrue(f.rootItemLink().isEmpty());
        assertTrue(f.priorVersionLink().isEmpty());
        assertTrue(f.sdtmClassMappingTargetLinks().isEmpty());
        assertTrue(f.sdtmDatasetMappingTargetLinks().isEmpty());
        assertTrue(f.sdtmigDatasetMappingTargetLinks().isEmpty());
    }

    // --- Tier-2 expansions: ProductGroup ---


    @Test
    void productGroupAllAccessors()
    {
        ProductGroup pg = create("""
                {"_links":{"adam":[{"href":"/a"}],
                           "sdtm":[{"href":"/sd"}],
                           "sdtmig":[{"href":"/sdig"}],
                           "sendig":[{"href":"/sendig"}],
                           "cdash":[{"href":"/c"}],
                           "cdashig":[{"href":"/cig"}],
                           "packages":[{"href":"/pkg"}],
                           "instrument":[{"href":"/qrs"}]}}
                """, ProductGroup.class);
        assertEquals(1, pg.adamLinks().size());
        assertEquals(1, pg.sdtmLinks().size());
        assertEquals(1, pg.sdtmigLinks().size());
        assertEquals(1, pg.sendigLinks().size());
        assertEquals(1, pg.cdashLinks().size());
        assertEquals(1, pg.cdashigLinks().size());
        assertEquals(1, pg.packageLinks().size());
        assertEquals(1, pg.qrsLinks().size());
    }


    @Test
    void productGroupMissingAccessors()
    {
        ProductGroup pg = create("{}", ProductGroup.class);
        assertTrue(pg.adamLinks().isEmpty());
        assertTrue(pg.sdtmLinks().isEmpty());
        assertTrue(pg.sdtmigLinks().isEmpty());
        assertTrue(pg.sendigLinks().isEmpty());
        assertTrue(pg.cdashLinks().isEmpty());
        assertTrue(pg.cdashigLinks().isEmpty());
        assertTrue(pg.packageLinks().isEmpty());
        assertTrue(pg.qrsLinks().isEmpty());
    }

    // --- Tier-2 expansions: RuleOperation ---


    @Test
    void ruleOperationAllAccessors()
    {
        RuleOperation op = create("""
                {"id":"op1","name":"min_date","operator":"min_date",
                 "domain":"--","group":["USUBJID"],
                 "filter":{"VAR":"value"},
                 "codelists":["C66728"],"level":"subject",
                 "returntype":"Date","key_name":"key","key_value":"val",
                 "ct_attribute":"CDISC Submission Value","version":"2023-12-15",
                 "ct_package_types":["sdtmct"],"regex":"^[0-9]+$",
                 "value_is_reference":true}
                """, RuleOperation.class);
        assertEquals("op1", op.id().orElse(null));
        assertEquals("min_date", op.name().orElse(null));
        assertEquals("min_date", op.operator().orElse(null));
        assertNotNull(op.operatorEnum());
        assertEquals("--", op.domain().orElse(null));
        assertEquals(List.of("USUBJID"), op.group());
        assertTrue(op.filter().isPresent());
        assertEquals(List.of("C66728"), op.codelists());
        assertEquals("subject", op.level().orElse(null));
        assertEquals("Date", op.returntype().orElse(null));
        assertEquals("key", op.keyName().orElse(null));
        assertEquals("val", op.keyValue().orElse(null));
        assertEquals("CDISC Submission Value", op.ctAttribute().orElse(null));
        assertEquals("2023-12-15", op.version().orElse(null));
        assertEquals(List.of("sdtmct"), op.ctPackageTypes());
        assertEquals("^[0-9]+$", op.regex().orElse(null));
        assertTrue(op.valueIsReference().orElse(false));
    }


    @Test
    void ruleOperationMissingAccessors()
    {
        RuleOperation op = create("{}", RuleOperation.class);
        assertTrue(op.id().isEmpty());
        assertTrue(op.name().isEmpty());
        assertTrue(op.operator().isEmpty());
        assertTrue(op.domain().isEmpty());
        assertTrue(op.group().isEmpty());
        assertTrue(op.filter().isEmpty());
        assertTrue(op.codelists().isEmpty());
        assertTrue(op.level().isEmpty());
        assertTrue(op.returntype().isEmpty());
        assertTrue(op.keyName().isEmpty());
        assertTrue(op.keyValue().isEmpty());
        assertTrue(op.ctAttribute().isEmpty());
        assertTrue(op.version().isEmpty());
        assertTrue(op.ctPackageTypes().isEmpty());
        assertTrue(op.regex().isEmpty());
        assertTrue(op.valueIsReference().isEmpty());
    }

    // --- Extra coverage: SdtmVariable (16.7% baseline) ---


    @Test
    void sdtmVariableAllAccessors()
    {
        SdtmVariable v = create("""
                {"ordinal":"5","name":"AESTDTC","label":"Start Date",
                 "description":"Start of event","role":"Timing",
                 "roleDescription":"Timing of event","simpleDatatype":"Char",
                 "core":"Exp","usageRestrictions":"none","notes":"ISO 8601",
                 "examples":"2024-01-01","describedValueDomain":"ISO 8601 date",
                 "valueList":["A","B","C"],
                 "_links":{"codelist":{"href":"/c"},
                           "parentProduct":{"href":"/pp"},
                           "parentClass":{"href":"/pc"},
                           "parentDataset":{"href":"/pd"},
                           "modelDatasetVariable":{"href":"/mdv"},
                           "rootItem":{"href":"/ri"},
                           "priorVersion":{"href":"/pv"},
                           "qualifiesVariables":[{"href":"/q1"},{"href":"/q2"}]}}
                """, SdtmVariable.class);
        assertEquals("5", v.ordinal().orElse(null));
        assertEquals("AESTDTC", v.name().orElse(null));
        assertEquals("Start Date", v.label().orElse(null));
        assertEquals("Start of event", v.description().orElse(null));
        assertEquals("Timing", v.role().orElse(null));
        assertEquals("Timing of event", v.roleDescription().orElse(null));
        assertEquals("Char", v.simpleDatatype().orElse(null));
        assertEquals("Exp", v.core().orElse(null));
        assertEquals("none", v.usageRestrictions().orElse(null));
        assertEquals("ISO 8601", v.notes().orElse(null));
        assertEquals("2024-01-01", v.examples().orElse(null));
        assertEquals("ISO 8601 date", v.describedValueDomain().orElse(null));
        assertEquals(List.of("A", "B", "C"), v.valueList());
        assertTrue(v.codelistLink().isPresent());
        assertTrue(v.parentProductLink().isPresent());
        assertTrue(v.parentClassLink().isPresent());
        assertTrue(v.parentDatasetLink().isPresent());
        assertTrue(v.modelDatasetVariableLink().isPresent());
        assertTrue(v.rootItemLink().isPresent());
        assertTrue(v.priorVersionLink().isPresent());
        assertEquals(2, v.qualifiesVariableLinks().size());
    }

    // --- Extra coverage: QrsInstrument ---


    @Test
    void qrsInstrumentAllAccessors()
    {
        QrsInstrument qi = create("""
                {"name":"PHQ-9","label":"PHQ-9 Depression Scale",
                 "description":"Depression screening","effectiveDate":"2024-01-01",
                 "untilDate":"2030-01-01","registrationStatus":"Final",
                 "version":"1-0","instrumentType":"Questionnaire",
                 "copyrightStatus":"Public",
                 "responseGroups":[{"name":"RG1"}],
                 "items":[{"label":"Q1"}],
                 "_links":{"priorVersion":{"href":"/pv"},
                           "instrumentCAT":{"href":"/cat"},
                           "instrumentSCAT":{"href":"/scat"}}}
                """, QrsInstrument.class);
        assertEquals("PHQ-9", qi.name().orElse(null));
        assertEquals("PHQ-9 Depression Scale", qi.label().orElse(null));
        assertEquals("Depression screening", qi.description().orElse(null));
        assertEquals("2024-01-01", qi.effectiveDate().orElse(null));
        assertEquals("2030-01-01", qi.untilDate().orElse(null));
        assertEquals("Final", qi.registrationStatus().orElse(null));
        assertEquals("1-0", qi.version().orElse(null));
        assertEquals("Questionnaire", qi.instrumentType().orElse(null));
        assertEquals("Public", qi.copyrightStatus().orElse(null));
        assertEquals(1, qi.responseGroups().size());
        assertEquals(1, qi.items().size());
        assertTrue(qi.priorVersionLink().isPresent());
        assertTrue(qi.instrumentCatLink().isPresent());
        assertTrue(qi.instrumentScatLink().isPresent());
    }

    // --- Extra coverage: Document ---


    @Test
    void documentAllAccessors()
    {
        Document d = create("""
                {"id":"doc-1","pageId":"page-1","title":"AE Domain",
                 "section":"6.1","standard":"sdtmig","version":"3-4",
                 "parent":"parent-doc","html":"<p>x</p>","text":"x",
                 "createdAt":"2024-01-01","updatedAt":"2024-02-01",
                 "children":["c1","c2"],"structures":["AE","CM"],
                 "_links":{"children":[{"href":"/c1"},{"href":"/c2"}],
                           "section":{"href":"/sec"},
                           "parentProduct":{"href":"/pp"},
                           "parentDocument":{"href":"/pd"}}}
                """, Document.class);
        assertEquals("doc-1", d.id().orElse(null));
        assertEquals("page-1", d.pageId().orElse(null));
        assertEquals("AE Domain", d.title().orElse(null));
        assertEquals("6.1", d.section().orElse(null));
        assertEquals("sdtmig", d.standard().orElse(null));
        assertEquals("3-4", d.version().orElse(null));
        assertEquals("parent-doc", d.parent().orElse(null));
        assertEquals("<p>x</p>", d.html().orElse(null));
        assertEquals("x", d.text().orElse(null));
        assertEquals("2024-01-01", d.createdAt().orElse(null));
        assertEquals("2024-02-01", d.updatedAt().orElse(null));
        assertEquals(List.of("c1", "c2"), d.children());
        assertEquals(List.of("AE", "CM"), d.structures());
        assertEquals(2, d.childLinks().size());
        assertTrue(d.sectionLink().isPresent());
        assertTrue(d.parentProductLink().isPresent());
        assertTrue(d.parentDocumentLink().isPresent());
    }

    // --- Extra coverage: RuleCondition (extra accessors) ---


    @Test
    void ruleConditionAllAccessors()
    {
        RuleCondition rc = create("""
                {"name":"AETERM","operator":"equal_to",
                 "value":"DEATH","value_is_literal":true,
                 "value_is_reference":false,
                 "prefix":"AE","suffix":"DTC",
                 "regex":"^X$","within":"USUBJID",
                 "ordering":"asc","negative":false,
                 "type_insensitive":true,
                 "params":{"k":"v"},
                 "any":[{"name":"sub"}],
                 "all":[{"name":"all-sub"}],
                 "not":{"name":"not-sub"}}
                """, RuleCondition.class);
        assertEquals("AETERM", rc.name().orElse(null));
        assertEquals("equal_to", rc.operator().orElse(null));
        assertNotNull(rc.operatorEnum());
        assertEquals("AE", rc.prefix().orElse(null));
        assertEquals("DTC", rc.suffix().orElse(null));
        assertEquals("^X$", rc.regex().orElse(null));
        assertEquals("USUBJID", rc.within().orElse(null));
        assertEquals("asc", rc.ordering().orElse(null));
        assertFalse(rc.negative().orElse(true));
        assertTrue(rc.valueIsLiteral().orElse(false));
        assertFalse(rc.valueIsReference().orElse(true));
        assertTrue(rc.typeInsensitive().orElse(false));
        assertEquals("DEATH", rc.valueString().orElse(null));
        assertTrue(rc.params().isPresent());
        assertEquals(1, rc.any().size());
        assertEquals(1, rc.all().size());
        assertTrue(rc.not().isPresent());
    }


    @Test
    void ruleConditionNumericValueAndIntegerPrefix()
    {
        RuleCondition rc = create("""
                {"value":42,"prefix":3,"suffix":2}
                """, RuleCondition.class);
        assertTrue(rc.valueNumber().isPresent());
        assertEquals(42, rc.valueNumber().get().intValue());
        assertTrue(rc.prefixInt().isPresent());
        assertEquals(3, rc.prefixInt().getAsInt());
        assertEquals(2, rc.suffixInt().getAsInt());
    }


    @Test
    void ruleConditionArrayValueAndObjectValue()
    {
        RuleCondition rcArr = create("""
                {"value":["a","b","c"]}
                """, RuleCondition.class);
        assertEquals(List.of("a", "b", "c"), rcArr.valueList());

        RuleCondition rcObj = create("""
                {"value":{"nested":"v"}}
                """, RuleCondition.class);
        assertTrue(rcObj.valueObject().isPresent());
    }

    // --- Extra coverage: RuleCore / RuleStandard / RuleOutcome / RuleAuthority ---


    @Test
    void ruleCoreAllAccessors()
    {
        RuleCore c = create("""
                {"Id":"CORE-000351","Version":"1","Status":"Published"}
                """, RuleCore.class);
        assertEquals("CORE-000351", c.id().orElse(null));
        assertEquals("1", c.version().orElse(null));
        assertEquals("Published", c.status().orElse(null));
    }


    @Test
    void ruleStandardAllAccessors()
    {
        RuleStandard s = create("""
                {"Name":"SDTM","Version":"3.4","Substandard":"clinical",
                 "References":[{"Origin":"IG","Version":"1"}]}
                """, RuleStandard.class);
        assertEquals("SDTM", s.name().orElse(null));
        assertEquals("3.4", s.version().orElse(null));
        assertEquals("clinical", s.substandard().orElse(null));
        assertEquals(1, s.references().size());
    }


    @Test
    void ruleOutcomeAllAccessors()
    {
        RuleOutcome o = create("""
                {"Message":"Bad","Output_Variables":["USUBJID","AETERM"]}
                """, RuleOutcome.class);
        assertEquals("Bad", o.message().orElse(null));
        assertEquals(List.of("USUBJID", "AETERM"), o.outputVariables());
    }


    @Test
    void ruleAuthorityAllAccessors()
    {
        RuleAuthority a = create("""
                {"Organization":"CDISC",
                 "Standards":[{"Name":"SDTM","Version":"3.4"},
                              {"Name":"ADaM","Version":"1.3"}]}
                """, RuleAuthority.class);
        assertEquals("CDISC", a.organization().orElse(null));
        assertEquals(2, a.standards().size());
    }

    // --- Extra coverage: RulePackage and Rule cross-references ---


    @Test
    void rulePackageAccessors()
    {
        RulePackage rp = create("""
                {"rules":{"r1":{"id":"r1"}},
                 "_links":{"standards":[{"href":"/s1"}],
                           "priorVersion":{"href":"/pv"}}}
                """, RulePackage.class);
        assertTrue(rp.rules().isPresent());
        assertEquals(1, rp.rules().get().size());
        assertEquals(1, rp.standardLinks().size());
        assertTrue(rp.priorVersionLink().isPresent());
    }


    @Test
    void ruleMatchDatasetAndLinkAccessorsOnRule()
    {
        Rule r = create("""
                {"id":"r1","Description":"d",
                 "Match_Datasets":[{"Name":"AE","Keys":["USUBJID"]}],
                 "_links":{"standards":[{"href":"/s1"}],
                           "package":{"href":"/pkg"}}}
                """, Rule.class);
        assertEquals(1, r.matchDatasets().size());
        assertEquals("AE", r.matchDatasets().get(0).name().orElse(null));
        assertEquals(1, r.standardLinks().size());
        assertTrue(r.packageLink().isPresent());
    }
}
