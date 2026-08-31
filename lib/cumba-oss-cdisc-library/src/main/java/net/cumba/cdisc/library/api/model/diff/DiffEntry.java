package net.cumba.cdisc.library.api.model.diff;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiArrayResource;
import net.cumba.web.api.ApiResource;

/**
 * A single diff entry (table) within a diff result.
 *
 * <p>
 * Represents one comparison table showing differences between two product versions, with column
 * headers and rows of mixed-type values.
 * </p>
 */
public interface DiffEntry extends ApiResource
{

    /**
     * Returns the title of this diff table.
     *
     * @return the title of this diff table.
     */
    default Optional<String> title()
    {
        return getString("title");
    }


    /**
     * Returns the column headers for the diff table.
     *
     * @return column headers for the diff table.
     */
    default List<String> head()
    {
        return getStringList("head");
    }


    /**
     * Returns the body rows of the diff table.
     *
     * @return body rows of the diff table. Each row is an array of mixed types, so the raw array
     *         resource is returned for flexible access.
     */
    default Optional<ApiArrayResource> body()
    {
        return getArray("body", ApiArrayResource.class);
    }
}
