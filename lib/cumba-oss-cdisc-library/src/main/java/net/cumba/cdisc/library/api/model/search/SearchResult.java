package net.cumba.cdisc.library.api.model.search;

import java.util.List;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * Search or suggest result (response from {@code /mdr/search}, {@code /mdr/suggest},
 * {@code /mdr/search/scopes/{scope}}, or {@code /mdr/search/implementedBy}).
 *
 * <p>
 * Results are returned as links in {@code _links.searchResults}.
 * </p>
 */
public interface SearchResult extends ApiResource
{

    /** Links to matching resources. */
    default List<Link> searchResultLinks()
    {
        return getLinks("searchResults");
    }
}
