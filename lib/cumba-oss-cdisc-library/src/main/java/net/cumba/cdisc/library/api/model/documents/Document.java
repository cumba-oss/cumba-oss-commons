package net.cumba.cdisc.library.api.model.documents;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * An Implementation Guide (IG) document (response from {@code /mdr/documents/{document_id}}).
 *
 * <p>
 * Represents a single section or page within an IG document, including its HTML and plain-text
 * content, hierarchical structure (parent/children), and related data structures.
 * </p>
 */
public interface Document extends ApiResource
{

    /**
     * Returns the document identifier.
     *
     * @return the document identifier.
     */
    default Optional<String> id()
    {
        return getString("id");
    }


    /**
     * Returns the page identifier within the document.
     *
     * @return the page identifier within the document.
     */
    default Optional<String> pageId()
    {
        return getString("pageId");
    }


    /**
     * Returns the document title.
     *
     * @return the document title.
     */
    default Optional<String> title()
    {
        return getString("title");
    }


    /**
     * Returns the section name.
     *
     * @return the section name.
     */
    default Optional<String> section()
    {
        return getString("section");
    }


    /**
     * Returns the standard this document belongs to (e.g., "sdtmig").
     *
     * @return the standard this document belongs to (e.g., "sdtmig").
     */
    default Optional<String> standard()
    {
        return getString("standard");
    }


    /**
     * Returns the standard version.
     *
     * @return the standard version.
     */
    default Optional<String> version()
    {
        return getString("version");
    }


    /**
     * Returns the parent document identifier.
     *
     * @return the parent document identifier.
     */
    default Optional<String> parent()
    {
        return getString("parent");
    }


    /**
     * Returns the document content as HTML.
     *
     * @return the document content as HTML.
     */
    default Optional<String> html()
    {
        return getString("html");
    }


    /**
     * Returns the document content as plain text.
     *
     * @return the document content as plain text.
     */
    default Optional<String> text()
    {
        return getString("text");
    }


    /**
     * Returns the creation timestamp.
     *
     * @return the creation timestamp.
     */
    default Optional<String> createdAt()
    {
        return getString("createdAt");
    }


    /**
     * Returns the last update timestamp.
     *
     * @return the last update timestamp.
     */
    default Optional<String> updatedAt()
    {
        return getString("updatedAt");
    }


    /**
     * Returns the child document identifiers.
     *
     * @return child document identifiers.
     */
    default List<String> children()
    {
        return getStringList("children");
    }


    /**
     * Returns the related data structure identifiers.
     *
     * @return related data structure identifiers.
     */
    default List<String> structures()
    {
        return getStringList("structures");
    }


    /**
     * Returns links to child documents.
     *
     * @return links to child documents.
     */
    default List<Link> childLinks()
    {
        return getLinks("children");
    }


    /**
     * Returns the link to the section.
     *
     * @return link to the section.
     */
    default Optional<Link> sectionLink()
    {
        return getLink("section");
    }


    /**
     * Returns the link to the parent product.
     *
     * @return link to the parent product.
     */
    default Optional<Link> parentProductLink()
    {
        return getLink("parentProduct");
    }


    /**
     * Returns the link to the parent document.
     *
     * @return link to the parent document.
     */
    default Optional<Link> parentDocumentLink()
    {
        return getLink("parentDocument");
    }
}
