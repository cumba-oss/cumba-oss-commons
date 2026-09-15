package net.cumba.cdisc.library.api.model.cdash;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A CDASH field (used in both class-level and domain-level contexts).
 *
 * <p>
 * Represents a data collection field on a case report form, including its question text, prompt,
 * data type, and mapping instructions to SDTM variables. In integrated standards, additional fields
 * like {@code core} and {@code completionInstructions} may be present.
 * </p>
 */
public interface CdashField extends ApiResource
{

    /** Returns the ordinal position of this field. */
    default Optional<String> ordinal()
    {
        return getString("ordinal");
    }


    /** Returns the field name. */
    default Optional<String> name()
    {
        return getString("name");
    }


    /** Returns the field label. */
    default Optional<String> label()
    {
        return getString("label");
    }


    /** Returns the field definition. */
    default Optional<String> definition()
    {
        return getString("definition");
    }


    /** Returns the question text displayed on the CRF. */
    default Optional<String> questionText()
    {
        return getString("questionText");
    }


    /** Returns the prompt text for data entry. */
    default Optional<String> prompt()
    {
        return getString("prompt");
    }


    /** Returns implementation notes for this field. */
    default Optional<String> implementationNotes()
    {
        return getString("implementationNotes");
    }


    /** Returns completion instructions (integrated standards only). */
    default Optional<String> completionInstructions()
    {
        return getString("completionInstructions");
    }


    /** Returns the simple data type (e.g., {@code "Char"}, {@code "Num"}). */
    default Optional<String> simpleDatatype()
    {
        return getString("simpleDatatype");
    }


    /** Returns instructions for mapping this field to SDTM variables. */
    default Optional<String> mappingInstructions()
    {
        return getString("mappingInstructions");
    }


    /**
     * Returns the core classifier ({@code "Perm"}, {@code "Req"}, or {@code "Cond"}; integrated
     * standards only).
     */
    default Optional<String> core()
    {
        return getString("core");
    }


    /**
     * Returns whether this field is domain-specific.
     *
     * <p>
     * Accepts both wire forms, for the same reason and in the same way as
     * {@code CtCodelist.extensible()}. The CDISC Library serialises this field as a JSON
     * <em>string</em> ({@code "domainSpecific": "true"}), which a strict {@code getBoolean}
     * rejects. Measured over the captured response cache
     * (<a href="file:///data/cdisc.metadata.library-cache">861 responses</a>): all <b>65</b>
     * occurrences of {@code domainSpecific} — in {@code /mdr/cdash/1-0}, {@code 1-1}, {@code 1-2}
     * and {@code 1-3} — are the string {@code "true"}, and <b>none</b> is a JSON boolean. So before
     * this was made tolerant the accessor returned an empty result for every real response, and
     * both of its tests fed a JSON boolean, a shape the live API never produces.
     * </p>
     *
     * <p>
     * The leniency is deliberately scoped to this field rather than to
     * {@code ApiResource.getBoolean}, whose strict contract every other model accessor relies on.
     * Values other than {@code true}/{@code false} (case-insensitive) yield an empty result rather
     * than a silent {@code false}.
     * </p>
     *
     * @return whether this field is domain-specific, or empty when the field is absent or not a
     *         recognisable boolean.
     */
    default Optional<Boolean> domainSpecific()
    {
        Optional<Boolean> direct = getBoolean("domainSpecific");
        if (direct.isPresent())
        {
            return direct;
        }
        return getString("domainSpecific")//
                .map(String::trim)//
                .filter(v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v))//
                .map(Boolean::parseBoolean);
    }


    /**
     * Returns the link to the associated codelist, or the <em>first</em> of them when the field is
     * associated with several.
     *
     * <p>
     * ⚠ The live API serves {@code _links.codelist} as an <b>array</b> — in all 6 988 occurrences
     * across the captured response cache — and 169 of those carry more than one codelist. This
     * accessor keeps its historical single-link signature and therefore reports only the first. Use
     * {@link #codelistLinks()} to see all of them.
     * </p>
     */
    default Optional<Link> codelistLink()
    {
        return getLink("codelist");
    }


    /**
     * Returns every codelist link associated with this field.
     *
     * <p>
     * The live API serves {@code _links.codelist} as an array, and a field may legitimately be
     * governed by several codelists — e.g. {@code CMDOSU} in CDASH-IG 2.0 is associated with both
     * {@code /mdr/root/ct/sdtmct/codelists/C71620} and
     * {@code /mdr/root/ct/cdashct/codelists/C78417}. {@link #codelistLink()} sees only the first,
     * so a caller resolving controlled terminology through it validates against an incomplete
     * vocabulary with no error.
     * </p>
     */
    default List<Link> codelistLinks()
    {
        return getLinks("codelist");
    }


    /** Returns the link to the associated value list. */
    default Optional<Link> valuelistLink()
    {
        return getLink("valuelist");
    }


    /** Returns the link to the corresponding CDASH model field. */
    default Optional<Link> modelFieldLink()
    {
        return getLink("modelField");
    }


    /** Returns the link to the parent product. */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /** Returns the link to the parent class. */
    default Optional<Link> parentClassLink()
    {
        return getLink("parentClass");
    }


    /** Returns the link to the parent domain. */
    default Optional<Link> parentDomainLink()
    {
        return getLink("parentDomain");
    }


    /** Returns the link to the parent scenario (CDASHIG only). */
    default Optional<Link> parentScenarioLink()
    {
        return getLink("parentScenario");
    }


    /** Returns the link to the root item in the terminology hierarchy. */
    default Optional<Link> rootItemLink()
    {
        return getLink("rootItem");
    }


    /** Returns the link to the prior version of this field. */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }


    /** Returns the links to SDTM class-level mapping targets. */
    default List<Link> sdtmClassMappingTargetLinks()
    {
        return getLinks("sdtmClassMappingTargets");
    }


    /** Returns the links to SDTM dataset-level mapping targets. */
    default List<Link> sdtmDatasetMappingTargetLinks()
    {
        return getLinks("sdtmDatasetMappingTargets");
    }


    /** Returns the links to SDTM-IG dataset-level mapping targets. */
    default List<Link> sdtmigDatasetMappingTargetLinks()
    {
        return getLinks("sdtmigDatasetMappingTargets");
    }
}
