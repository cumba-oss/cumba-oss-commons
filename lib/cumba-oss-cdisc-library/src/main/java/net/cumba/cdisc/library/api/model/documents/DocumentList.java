package net.cumba.cdisc.library.api.model.documents;

import java.util.List;

import net.cumba.web.api.ApiResource;

/**
 * A list of documents (response from section detail endpoints like
 * {@code /mdr/documents/{standard}/{version}/{structure}/sections/{section}}).
 */
public interface DocumentList extends ApiResource
{

    /**
     * Returns the documents in this list.
     *
     * @return the documents in this list.
     */
    default List<Document> documents()
    {
        return getList("documents", Document.class);
    }
}
