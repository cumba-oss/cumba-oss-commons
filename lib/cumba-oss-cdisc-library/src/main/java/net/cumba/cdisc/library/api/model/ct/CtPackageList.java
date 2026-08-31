package net.cumba.cdisc.library.api.model.ct;

import java.util.List;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * List of Controlled Terminology (CT) packages (response from {@code /mdr/ct/packages}).
 *
 * <p>
 * Provides HATEOAS links to all available CT packages in the CDISC Library.
 * </p>
 */
public interface CtPackageList extends ApiResource
{

    /**
     * Returns links to all available CT packages.
     *
     * @return links to all available CT packages.
     */
    default List<Link> packageLinks()
    {
        return getLinks("packages");
    }
}
