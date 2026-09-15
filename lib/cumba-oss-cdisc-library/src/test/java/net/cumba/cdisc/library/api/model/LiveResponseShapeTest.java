package net.cumba.cdisc.library.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.cumba.cdisc.library.api.model.rules.RuleScopeFilter;
import net.cumba.cdisc.library.api.model.sdtm.SdtmClass;
import net.cumba.cdisc.library.api.model.sdtm.SdtmDataset;
import net.cumba.cdisc.library.api.model.sdtm.SdtmProduct;
import net.cumba.cdisc.library.api.model.sdtm.SdtmVariable;
import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;
import net.cumba.web.api.json.JsonNodeResource;
import org.junit.jupiter.api.Test;

/**
 * Accessors pinned against the shape the <em>live</em> CDISC Library actually serves.
 *
 * <h2>Why this class exists separately</h2>
 *
 * <p>
 * A DTO accessor reads one JSON key. A test that builds its own fixture from that same key asserts
 * the accessor back to itself: it proves the reader can read what the fixture was written to
 * contain, and it cannot detect the one failure that matters here — a <b>wrong key</b>, which
 * returns empty for every real response and reports no error. This module has been bitten by that
 * five times (<code>instrument</code> vs <code>instruments</code>, three numeric
 * <code>ordinal</code> fixtures, a boolean <code>domainSpecific</code> fixture), so a fixture with
 * no independent provenance is treated here as no test at all.
 *
 * <p>
 * Every fixture below is therefore <b>transcribed from a real captured response</b> in
 * <a href="file:///data/cdisc.metadata.library-cache">the CDISC Library response cache</a> — 861
 * responses, of which the large product / CT / ADaM / SDTM / CDASH captures are genuine API
 * traffic. The cache file and the containing object are named per test, so a reader can re-derive
 * the key from the API rather than from this repository's source. Keys are facts about a
 * third-party API; only the handful of bytes needed to pin each one is reproduced.
 *
 * <p>
 * ⚠ Six of the cached entries are 31–59 byte smoke-test stubs written in one minute on 2026-04-21
 * ({@code about}, {@code lastupdated}, {@code maintenance}, {@code test}, and the two {@code diff}
 * entries). They are <b>not</b> API traffic and are never used as an oracle here — see the class
 * javadoc of the accessors they would otherwise appear to settle.
 *
 * <h2>The companion tests</h2>
 *
 * <p>
 * Each relation is pinned <em>from both sides</em>: one test proves the live spelling is read, and
 * one proves a plausible wrong spelling is <b>not</b>. Without the second, a revert of the key
 * could be made green again simply by changing the fixture to agree with it — which is exactly how
 * the {@code instrument}/{@code instruments} defect survived for so long.
 */
class LiveResponseShapeTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private <T extends ApiResource> T create(String json, Class<T> type)
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


    private static List<String> hrefs(List<Link> links)
    {
        return links.stream().map(l -> l.href().orElse(null)).toList();
    }


    private static String href(java.util.Optional<Link> link)
    {
        return link.flatMap(Link::href).orElse(null);
    }

    // ------------------------------------------------------------------
    // priorVersion — the single most widely served relation in the cache
    // ------------------------------------------------------------------


    /**
     * {@code priorVersion} on every product-level and item-level type that serves it.
     *
     * <p>
     * Provenance: the relation occurs 23 787 times across 291 captured responses. The
     * {@code self.type} census attributes it to, among others, {@code Implementation Guide} and
     * {@code Foundational Model} (product roots — {@code /mdr/cdashig/2-1} links back to
     * {@code /mdr/cdashig/2-0}, {@code /mdr/cdash/1-1} to {@code /mdr/cdash/1-0},
     * {@code /mdr/sdtmig/3-4} to {@code /mdr/sdtmig/3-3}), {@code Data Structure} (8),
     * {@code Analysis Variable} (996), {@code Variable Set} (68), {@code CDASH Scenario} (54),
     * {@code Class} (236) and {@code SDTM Dataset} (874) — the eight types this module models with
     * a {@code priorVersionLink()} that no scenario previously reached.
     */
    @Test
    void priorVersionIsTheLiveRelationOnEveryTypeThatServesIt()
    {
        // /mdr/sdtmig/3-4?expand=true, top-level object, verbatim.
        String sdtmigRoot = """
                {"_links":{"priorVersion":{"type":"Implementation Guide",
                  "title":"Study Data Tabulation Model Implementation Guide: Human Clinical Trials",
                  "href":"/mdr/sdtmig/3-3"},
                  "self":{"href":"/mdr/sdtmig/3-4"}}}
                """;
        assertEquals("/mdr/sdtmig/3-3",
                href(create(sdtmigRoot, SdtmProduct.class).priorVersionLink()));

        // /mdr/cdashig/2-1?expand=true, top-level object, verbatim.
        String cdashigRoot = """
                {"_links":{"priorVersion":{"type":"Implementation Guide",
                  "href":"/mdr/cdashig/2-0"},
                  "self":{"href":"/mdr/cdashig/2-1"}}}
                """;
        assertEquals("/mdr/cdashig/2-0",
                href(create(cdashigRoot, CdashProduct.class).priorVersionLink()));

        // /mdr/adam/adam-occds-1-0?expand=true, top-level object, verbatim.
        String adamRoot = """
                {"_links":{"priorVersion":{"type":"Implementation Guide",
                  "href":"/mdr/adam/adam-adae-1-0"},
                  "self":{"href":"/mdr/adam/adam-occds-1-0"}}}
                """;
        assertEquals("/mdr/adam/adam-adae-1-0",
                href(create(adamRoot, AdamProduct.class).priorVersionLink()));

        // /mdr/adam/adam-occds-1-0?expand=true -> dataStructures[0], verbatim.
        String dataStructure = """
                {"name":"OCCDS","ordinal":"1",
                 "_links":{"priorVersion":{"type":"Implementation Guide",
                   "href":"/mdr/adam/adam-adae-1-0/datastructures/ADAE"},
                   "self":{"href":"/mdr/adam/adam-occds-1-0/datastructures/OCCDS"}}}
                """;
        assertEquals("/mdr/adam/adam-adae-1-0/datastructures/ADAE",
                href(create(dataStructure, AdamDataStructure.class).priorVersionLink()));

        // ADaM analysisVariableSets[] / analysisVariables[] carry it too.
        String variableSet = """
                {"name":"Identifier Variables",
                 "_links":{"priorVersion":{"href":"/mdr/adam/adam-adae-1-0/varsets/Identifier"}}}
                """;
        assertEquals("/mdr/adam/adam-adae-1-0/varsets/Identifier",
                href(create(variableSet, AdamVariableSet.class).priorVersionLink()));

        String analysisVariable = """
                {"name":"STUDYID",
                 "_links":{"priorVersion":{"href":"/mdr/adam/adam-adae-1-0/variables/STUDYID"}}}
                """;
        assertEquals("/mdr/adam/adam-adae-1-0/variables/STUDYID",
                href(create(analysisVariable, AdamVariable.class).priorVersionLink()));

        // /mdr/cdashig/...?expand=true -> classes[].scenarios[], type "CDASH Scenario".
        String scenario = """
                {"scenario":"Lab Results",
                 "_links":{"priorVersion":{"href":"/mdr/cdashig/2-0/scenarios/LB.Results"}}}
                """;
        assertEquals("/mdr/cdashig/2-0/scenarios/LB.Results",
                href(create(scenario, CdashScenario.class).priorVersionLink()));

        // SDTM "Class" (236) and "SDTM Dataset" (874).
        String sdtmClass = """
                {"name":"Events",
                 "_links":{"priorVersion":{"href":"/mdr/sdtm/1-8/classes/Events"}}}
                """;
        assertEquals("/mdr/sdtm/1-8/classes/Events",
                href(create(sdtmClass, SdtmClass.class).priorVersionLink()));

        String sdtmDataset = """
                {"name":"AE",
                 "_links":{"priorVersion":{"href":"/mdr/sdtmig/3-3/datasets/AE"}}}
                """;
        assertEquals("/mdr/sdtmig/3-3/datasets/AE",
                href(create(sdtmDataset, SdtmDataset.class).priorVersionLink()));
    }


    /**
     * {@code previousVersion} is NOT the live relation name. Pins {@code priorVersion} from the
     * other side, so a rename could not be made green by an agreeing fixture. The cache contains
     * zero {@code previousVersion} relations.
     */
    @Test
    void thePriorVersionRelationIsNotSpelledPreviousVersion()
    {
        String wrong = """
                {"_links":{"previousVersion":{"href":"/mdr/sdtmig/3-3"}}}
                """;
        assertTrue(create(wrong, SdtmProduct.class).priorVersionLink().isEmpty());
        assertTrue(create(wrong, AdamVariable.class).priorVersionLink().isEmpty());
        assertTrue(create(wrong, CdashScenario.class).priorVersionLink().isEmpty());
        assertTrue(create(wrong, SdtmDataset.class).priorVersionLink().isEmpty());
    }

    // ------------------------------------------------------------------
    // The ADaM parent* family
    // ------------------------------------------------------------------


    /**
     * The four ADaM {@code parent*} relations, each transcribed from the captured ADaM responses
     * that serve it: {@code parentClassDatastructure} (3 occurrences, {@code adam-md-1-0} and
     * {@code adam-occds-1-1}), {@code parentDatastructure} (2 475), {@code parentVariableSet} (2
     * 262) and {@code parentClassVariableSet} (18, {@code adam-occds-1-1}).
     *
     * <p>
     * The spellings matter and are not guessable: the API uses {@code Datastructure} with a
     * lower-case {@code s} in the relation name while spelling the JSON array
     * {@code dataStructures} with a capital one.
     */
    @Test
    void adamParentRelationsMatchTheLiveSpelling()
    {
        // /mdr/adam/adam-md-1-0?expand=true -> dataStructures[] name "MDTTE", verbatim.
        String mdtte = """
                {"name":"MDTTE","ordinal":"4",
                 "_links":{"parentClassDatastructure":{"type":"Data Structure",
                   "title":"Medical Device Basic Data Structure",
                   "href":"/mdr/adam/adam-md-1-0/datastructures/MDBDS"},
                   "self":{"href":"/mdr/adam/adam-md-1-0/datastructures/MDTTE"}}}
                """;
        assertEquals("/mdr/adam/adam-md-1-0/datastructures/MDBDS",
                href(create(mdtte, AdamDataStructure.class).parentClassDatastructureLink()));

        // /mdr/adam/adam-adae-1-0?expand=true -> analysisVariableSets[] "Identifier Variables".
        String varset = """
                {"name":"Identifier Variables","ordinal":"1",
                 "_links":{"parentDatastructure":{"type":"Data Structure",
                   "title":"Adverse Event Analysis Dataset",
                   "href":"/mdr/adam/adam-adae-1-0/datastructures/ADAE"},
                   "self":{"href":"/mdr/adam/adam-adae-1-0/datastructures/ADAE/varsets/IdentifierVariables"}}}
                """;
        assertEquals("/mdr/adam/adam-adae-1-0/datastructures/ADAE",
                href(create(varset, AdamVariableSet.class).parentDatastructureLink()));

        // /mdr/adam/adam-occds-1-1?expand=true -> analysisVariableSets[] "Identifier".
        String classVarset = """
                {"name":"Identifier","ordinal":"1",
                 "_links":{"parentClassVariableSet":{"type":"Variable Set",
                   "title":"OCCDS Identifier Variables",
                   "href":"/mdr/adam/adam-occds-1-1/datastructures/OCCDS/varsets/Identifier"},
                   "self":{"href":"/mdr/adam/adam-occds-1-1/datastructures/AE/varsets/Identifier"}}}
                """;
        assertEquals("/mdr/adam/adam-occds-1-1/datastructures/OCCDS/varsets/Identifier",
                href(create(classVarset, AdamVariableSet.class).parentClassVariableSetLink()));

        // /mdr/adam/adam-occds-1-1?expand=true -> ...analysisVariables[] "STUDYID", verbatim:
        // the same object carries all three of the AdamVariable parent relations.
        String studyid = """
                {"name":"STUDYID","ordinal":"1",
                 "_links":{"parentDatastructure":{"type":"Data Structure",
                   "href":"/mdr/adam/adam-occds-1-1/datastructures/AE"},
                  "parentVariableSet":{"type":"Variable Set",
                   "href":"/mdr/adam/adam-adae-1-0/datastructures/ADAE/varsets/IdentifierVariables"},
                  "parentClassVariable":{"type":"Analysis Variable",
                   "title":"Study Identifier",
                   "href":"/mdr/adam/adam-occds-1-1/datastructures/OCCDS/variables/STUDYID"},
                  "self":{"href":"/mdr/adam/adam-occds-1-1/datastructures/AE/variables/STUDYID"}}}
                """;
        AdamVariable v = create(studyid, AdamVariable.class);
        assertEquals("/mdr/adam/adam-occds-1-1/datastructures/AE",
                href(v.parentDatastructureLink()));
        assertEquals("/mdr/adam/adam-adae-1-0/datastructures/ADAE/varsets/IdentifierVariables",
                href(v.parentVariableSetLink()));
        assertEquals("/mdr/adam/adam-occds-1-1/datastructures/OCCDS/variables/STUDYID",
                href(v.parentClassVariableLink()));
    }


    /**
     * The capitalised {@code parentDataStructure} / {@code parentClassDataStructure} are NOT the
     * live relation names — the API capitalises the JSON array {@code dataStructures} but not the
     * relation. Pins the lower-case spelling from the other side.
     */
    @Test
    void adamParentRelationsAreNotSpelledWithACapitalS()
    {
        String wrong = """
                {"_links":{"parentDataStructure":{"href":"/x"},
                           "parentClassDataStructure":{"href":"/y"},
                           "parentVariableset":{"href":"/z"}}}
                """;
        AdamVariable v = create(wrong, AdamVariable.class);
        assertTrue(v.parentDatastructureLink().isEmpty());
        assertTrue(v.parentVariableSetLink().isEmpty());
        assertTrue(create(wrong, AdamDataStructure.class).parentClassDatastructureLink().isEmpty());
        assertTrue(create(wrong, AdamVariableSet.class).parentDatastructureLink().isEmpty());
    }

    // ------------------------------------------------------------------
    // CDASH parentDomain, on a scenario rather than a field
    // ------------------------------------------------------------------


    /**
     * {@code parentDomain} on a <b>CDASH Scenario</b>.
     *
     * <p>
     * Provenance matters here beyond the spelling: the relation occurs 4 246 times, but 4 133 of
     * those are on {@code Data Collection Field} objects. It is served on a {@code CDASH Scenario}
     * object 113 times — under {@code classes[].scenarios[]} — which is what makes
     * {@code CdashScenario.parentDomainLink()} a real accessor rather than a copy-paste from
     * {@code CdashField}.
     */
    @Test
    void cdashScenarioServesParentDomain()
    {
        String scenario = """
                {"scenario":"Lab Results",
                 "_links":{"parentDomain":{"type":"CDASH Domain","title":"Laboratory Test Results",
                   "href":"/mdr/cdashig/2-1/domains/LB"},
                  "self":{"href":"/mdr/cdashig/2-1/scenarios/LB.Results"}}}
                """;
        assertEquals("/mdr/cdashig/2-1/domains/LB",
                href(create(scenario, CdashScenario.class).parentDomainLink()));
    }


    /** {@code domain} alone is not the relation; pins {@code parentDomain} from the other side. */
    @Test
    void cdashScenarioIgnoresABareDomainRelation()
    {
        String wrong = """
                {"_links":{"domain":{"href":"/mdr/cdashig/2-1/domains/LB"}}}
                """;
        assertTrue(create(wrong, CdashScenario.class).parentDomainLink().isEmpty());
    }

    // ------------------------------------------------------------------
    // Controlled terminology — preferredTerm
    // ------------------------------------------------------------------


    /**
     * {@code preferredTerm} on a CT codelist.
     *
     * <p>
     * Transcribed from {@code /mdr/ct/packages/adamct-2014-09-26?expand=true},
     * {@code codelists[0]}, verbatim. The key occurs on both codelist and term objects throughout
     * the 206 captured CT package responses, so the spelling has provenance independent of this
     * repository. The sibling keys in the same fixture — {@code submissionValue},
     * {@code conceptId}, {@code definition}, {@code synonyms} and the string-valued
     * {@code extensible} — are the real ones the same object carries.
     */
    @Test
    void ctCodelistServesPreferredTermAlongsideItsSiblings()
    {
        String codelist = """
                {"submissionValue":"ANLPURP","synonyms":["Analysis Purpose"],
                 "preferredTerm":"CDISC ADaM Analysis Purpose Terminology",
                 "name":"Analysis Purpose","conceptId":"C117745",
                 "definition":"Purpose of a specific analysis result described in ADaM analysis results metadata.",
                 "extensible":"true"}
                """;
        CtCodelist cl = create(codelist, CtCodelist.class);
        assertEquals("CDISC ADaM Analysis Purpose Terminology", cl.preferredTerm().orElse(null));
        assertEquals("ANLPURP", cl.submissionValue().orElse(null));
        assertEquals("C117745", cl.conceptId().orElse(null));
        assertEquals(List.of("Analysis Purpose"), cl.synonyms());
        // The string wire form of extensible, which a strict getBoolean would reject.
        assertEquals(true, cl.extensible().orElse(null));
    }


    /**
     * {@code preferred_term} — the snake_case spelling the rules half of the API uses for its own
     * fields — is NOT how CT serialises it. Pins the camelCase spelling from the other side.
     */
    @Test
    void ctCodelistDoesNotReadASnakeCasePreferredTerm()
    {
        String wrong = """
                {"preferred_term":"CDISC ADaM Analysis Purpose Terminology"}
                """;
        assertTrue(create(wrong, CtCodelist.class).preferredTerm().isEmpty());
    }

    // ------------------------------------------------------------------
    // Conformance rules — include_split_datasets
    // ------------------------------------------------------------------


    /**
     * {@code include_split_datasets} in a rule's domain scope filter.
     *
     * <p>
     * Transcribed from {@code /mdr/rules/sdtmig/3-2}, where the key occurs as a JSON <b>boolean</b>
     * — 15 occurrences across the captured {@code sdtmig} rules responses, every one {@code true}.
     * Note the snake_case: the rules half of the CDISC Library uses a different naming convention
     * from the metadata half, and the sibling {@code Include} / {@code Exclude} lists in the same
     * object are Capitalised. All three spellings are pinned here together because getting any one
     * of them wrong yields a silently empty scope filter, which widens or narrows the set of
     * domains a conformance rule applies to with no error.
     */
    @Test
    void ruleScopeFilterMatchesTheLiveSnakeCaseAndCapitalisedKeys()
    {
        String filter = """
                {"Include":["ALL"],"Exclude":["RELREC"],"include_split_datasets":true}
                """;
        RuleScopeFilter f = create(filter, RuleScopeFilter.class);
        assertEquals(List.of("ALL"), f.include());
        assertEquals(List.of("RELREC"), f.exclude());
        assertEquals(true, f.includeSplitDatasets().orElse(null));
    }


    /**
     * The camelCase {@code includeSplitDatasets} and the lower-cased {@code include} /
     * {@code exclude} are NOT the live keys. Pins all three from the other side.
     */
    @Test
    void ruleScopeFilterIgnoresTheCamelCaseAndLowerCasedSpellings()
    {
        String wrong = """
                {"include":["ALL"],"exclude":["RELREC"],"includeSplitDatasets":true}
                """;
        RuleScopeFilter f = create(wrong, RuleScopeFilter.class);
        assertEquals(List.of(), f.include());
        assertEquals(List.of(), f.exclude());
        assertTrue(f.includeSplitDatasets().isEmpty());
    }

    // ------------------------------------------------------------------
    // domainSpecific — the string wire form
    // ------------------------------------------------------------------


    /**
     * {@code domainSpecific} is served as a JSON <b>string</b>, never as a boolean.
     *
     * <p>
     * Measured over the whole cache: 65 occurrences, in {@code /mdr/cdash/1-0} … {@code 1-3}, all
     * of them the string {@code "true"} and none a JSON boolean. Before the accessor was made
     * tolerant it returned empty for every one of them, and both of its existing tests fed a JSON
     * boolean — a shape the live API does not produce — so the defect could not be seen. The
     * fixture below is {@code AEACNOYN} from {@code /mdr/cdash/1-0?expand=true}, verbatim.
     */
    @Test
    void cdashFieldDomainSpecificIsServedAsAString()
    {
        String field = """
                {"name":"AEACNOYN","label":"Any Other Actions Taken","domainSpecific":"true"}
                """;
        assertEquals(true, create(field, CdashField.class).domainSpecific().orElse(null));
    }


    /**
     * The boolean wire form still works — the dev/map-backed path and the Python reference engine's
     * pickles carry a real boolean — and a value that is neither yields empty rather than a silent
     * {@code false}.
     */
    @Test
    void cdashFieldDomainSpecificAcceptsBooleanAndRejectsNonsense()
    {
        assertEquals(true, create("{\"domainSpecific\":true}", CdashField.class).domainSpecific()
                .orElse(null));
        assertEquals(false, create("{\"domainSpecific\":\"FALSE\"}", CdashField.class)
                .domainSpecific().orElse(null));
        assertTrue(create("{\"domainSpecific\":\"maybe\"}", CdashField.class).domainSpecific()
                .isEmpty());
        assertTrue(create("{\"domainSpecific\":1}", CdashField.class).domainSpecific().isEmpty());
        assertTrue(create("{}", CdashField.class).domainSpecific().isEmpty());
    }

    // ------------------------------------------------------------------
    // codelist — an array, sometimes with more than one entry
    // ------------------------------------------------------------------


    /**
     * {@code _links.codelist} is an <b>array</b>, and a variable may be governed by several
     * codelists.
     *
     * <p>
     * Measured over the cache: 6 988 occurrences, <b>all</b> of them arrays, of which 169 carry
     * more than one link (154 of size 2, 5 of size 3, 10 of size 5). {@code getLink} takes element
     * zero of an array, so {@code codelistLink()} silently reported one codelist and dropped the
     * rest — on the controlled-terminology path that is a variable validated against an incomplete
     * vocabulary, with nothing to say so. {@code codelistLinks()} was added for that; this test
     * pins both, so a regression that drops the plural accessor, or one that "tidies" the singular
     * one into reporting a different element, fails.
     *
     * <p>
     * The fixtures are verbatim: {@code --SEV} from {@code /mdr/adam/adam-occds-1-1?expand=true}
     * and {@code CMDOSU} from {@code /mdr/cdashig/2-0?expand=true}.
     */
    @Test
    void codelistIsAnArrayAndMayCarryMoreThanOneLink()
    {
        String sev = """
                {"name":"--SEV",
                 "_links":{"codelist":[{"href":"/mdr/root/ct/sdtmct/codelists/C66769"},
                                       {"href":"/mdr/root/ct/sdtmct/codelists/C165643"}]}}
                """;
        AdamVariable av = create(sev, AdamVariable.class);
        assertEquals(List.of("/mdr/root/ct/sdtmct/codelists/C66769",
                "/mdr/root/ct/sdtmct/codelists/C165643"), hrefs(av.codelistLinks()));
        assertEquals("/mdr/root/ct/sdtmct/codelists/C66769", href(av.codelistLink()));

        String cmdosu = """
                {"name":"CMDOSU",
                 "_links":{"codelist":[{"href":"/mdr/root/ct/sdtmct/codelists/C71620"},
                                       {"href":"/mdr/root/ct/cdashct/codelists/C78417"}]}}
                """;
        CdashField cf = create(cmdosu, CdashField.class);
        assertEquals(List.of("/mdr/root/ct/sdtmct/codelists/C71620",
                "/mdr/root/ct/cdashct/codelists/C78417"), hrefs(cf.codelistLinks()));
        assertEquals("/mdr/root/ct/sdtmct/codelists/C71620", href(cf.codelistLink()));

        String sdtmVar = """
                {"name":"AESEV",
                 "_links":{"codelist":[{"href":"/mdr/root/ct/sdtmct/codelists/C66769"}]}}
                """;
        SdtmVariable sv = create(sdtmVar, SdtmVariable.class);
        assertEquals(List.of("/mdr/root/ct/sdtmct/codelists/C66769"), hrefs(sv.codelistLinks()));
        assertEquals("/mdr/root/ct/sdtmct/codelists/C66769", href(sv.codelistLink()));
    }


    /**
     * {@code codelists}, plural, is NOT the relation name — the API spells the relation singular
     * even though its value is an array. Pins the spelling from the other side on all three types.
     */
    @Test
    void theCodelistRelationIsSingularEvenThoughItsValueIsAnArray()
    {
        String wrong = """
                {"_links":{"codelists":[{"href":"/mdr/root/ct/sdtmct/codelists/C66769"}]}}
                """;
        assertEquals(List.of(), create(wrong, AdamVariable.class).codelistLinks());
        assertEquals(List.of(), create(wrong, CdashField.class).codelistLinks());
        assertEquals(List.of(), create(wrong, SdtmVariable.class).codelistLinks());
        assertTrue(create(wrong, AdamVariable.class).codelistLink().isEmpty());
    }
}
