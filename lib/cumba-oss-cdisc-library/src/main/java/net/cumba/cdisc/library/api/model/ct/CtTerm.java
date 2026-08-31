package net.cumba.cdisc.library.api.model.ct;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A term within a codelist.
 *
 * <p>
 * Represents a single permissible value within a codelist, identified by an NCI Thesaurus concept
 * identifier and characterized by a submission value, definition, and preferred term.
 * </p>
 */
public interface CtTerm extends ApiResource
{

    /**
     * Returns the NCI Thesaurus concept identifier (pattern: {@code C\d+}).
     *
     * @return the NCI Thesaurus concept identifier (pattern: {@code C\d+}).
     */
    default Optional<String> conceptId()
    {
        return getString("conceptId");
    }


    /**
     * Returns the term submission value.
     *
     * @return the term submission value.
     */
    default Optional<String> submissionValue()
    {
        return getString("submissionValue");
    }


    /**
     * Returns the term definition.
     *
     * @return the term definition.
     */
    default Optional<String> definition()
    {
        return getString("definition");
    }


    /**
     * Returns the term preferred term.
     *
     * @return the term preferred term.
     */
    default Optional<String> preferredTerm()
    {
        return getString("preferredTerm");
    }


    /**
     * Returns the term synonyms.
     *
     * @return the term synonyms.
     */
    default List<String> synonyms()
    {
        return getStringList("synonyms");
    }


    /**
     * Returns the link to the parent codelist.
     *
     * @return link to the parent codelist.
     */
    default Optional<Link> parentCodelistLink()
    {
        return getLink("parentCodelist");
    }


    /**
     * Returns the link to the parent package.
     *
     * @return link to the parent package.
     */
    default Optional<Link> parentPackageLink()
    {
        return getLink("parentPackage");
    }


    /**
     * Returns the link to the root item in the terminology hierarchy.
     *
     * @return link to the root item in the terminology hierarchy.
     */
    default Optional<Link> rootItemLink()
    {
        return getLink("rootItem");
    }


    /**
     * Returns the link to the prior version of this term.
     *
     * @return link to the prior version of this term.
     */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
