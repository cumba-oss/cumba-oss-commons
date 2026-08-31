package net.cumba.cdisc.library.api.model.documents;

import java.util.List;

import net.cumba.web.api.ApiResource;

/**
 * A list of use cases (response from {@code /mdr/documents/{standard}/{version}/usecases}).
 */
public interface UseCaseList extends ApiResource
{

    /** Returns the use case names. */
    default List<String> useCases()
    {
        return getStringList("useCases");
    }
}
