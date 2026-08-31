package net.cumba.cdisc.library.api.model.rules;

import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * A citation within a rule reference, pointing to a specific section of a standard document.
 *
 * <p>
 * Contains the cited guidance text, the document name, and optional item and section identifiers
 * that locate the relevant passage within the referenced standard document.
 * </p>
 */
public interface RuleCitation extends ApiResource
{

    /** Returns the cited guidance text. */
    default Optional<String> citedGuidance()
    {
        return getString("Cited_Guidance");
    }


    /** Returns the name of the cited document. */
    default Optional<String> document()
    {
        return getString("Document");
    }


    /** Returns the item identifier within the document. */
    default Optional<String> item()
    {
        return getString("Item");
    }


    /** Returns the section identifier within the document. */
    default Optional<String> section()
    {
        return getString("Section");
    }
}
