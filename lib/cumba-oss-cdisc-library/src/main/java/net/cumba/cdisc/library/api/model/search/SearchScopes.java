package net.cumba.cdisc.library.api.model.search;

import java.util.List;

import net.cumba.web.api.ApiResource;

/**
 * Available search scopes (response from {@code /mdr/search/scopes}).
 *
 * <p>
 * Lists the field names that can be used to scope a search query (e.g., "name", "label",
 * "description", "conceptId", "submissionValue").
 * </p>
 */
public interface SearchScopes extends ApiResource
{

    /** Returns the available search scope names. */
    default List<String> scopes()
    {
        return getStringList("scopes");
    }
}
