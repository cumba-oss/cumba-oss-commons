package net.cumba.cdisc.library.api.model.documents;

import java.util.List;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A list of document sections (response from various {@code /mdr/documents/.../sections}
 * endpoints).
 */
public interface DocumentSectionList extends ApiResource
{

    /** Returns the section names. */
    default List<String> sections()
    {
        return getStringList("sections");
    }


    /** Returns links to the sections. */
    default List<Link> sectionLinks()
    {
        return getLinks("sections");
    }
}
