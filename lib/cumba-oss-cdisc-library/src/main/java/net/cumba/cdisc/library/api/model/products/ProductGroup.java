package net.cumba.cdisc.library.api.model.products;

import java.util.List;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A product group (response from {@code /mdr/products/{product-group}}).
 *
 * <p>
 * Contains links to all product versions within the group. The available link relations depend on
 * the group (e.g., "adam" for data-analysis, "sdtm"/"sdtmig"/"sendig" for data-tabulation).
 * </p>
 */
public interface ProductGroup extends ApiResource
{

    /** Returns links to ADaM product versions. */
    default List<Link> adamLinks()
    {
        return getLinks("adam");
    }


    /** Returns links to SDTM product versions. */
    default List<Link> sdtmLinks()
    {
        return getLinks("sdtm");
    }


    /** Returns links to SDTM-IG product versions. */
    default List<Link> sdtmigLinks()
    {
        return getLinks("sdtmig");
    }


    /** Returns links to SEND-IG product versions. */
    default List<Link> sendigLinks()
    {
        return getLinks("sendig");
    }


    /** Returns links to CDASH product versions. */
    default List<Link> cdashLinks()
    {
        return getLinks("cdash");
    }


    /** Returns links to CDASH-IG product versions. */
    default List<Link> cdashigLinks()
    {
        return getLinks("cdashig");
    }


    /** Returns links to CT packages. */
    default List<Link> packageLinks()
    {
        return getLinks("packages");
    }


    /** Returns links to QRS instruments. */
    default List<Link> qrsLinks()
    {
        return getLinks("instrument");
    }
}
