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


    /** Returns whether this field is domain-specific. */
    default Optional<Boolean> domainSpecific()
    {
        return getBoolean("domainSpecific");
    }


    /** Returns the link to the associated codelist. */
    default Optional<Link> codelistLink()
    {
        return getLink("codelist");
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
