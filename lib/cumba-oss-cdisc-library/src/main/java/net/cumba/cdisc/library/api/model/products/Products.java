package net.cumba.cdisc.library.api.model.products;

import java.util.List;
import java.util.stream.Stream;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * CDISC Library product catalog (response from {@code /mdr/products}).
 *
 * <p>
 * The product catalog organizes available standards into groups such as data-collection,
 * data-tabulation, data-analysis, terminology, and qrs. Each group contains links to available
 * product versions.
 * </p>
 */
public interface Products extends ApiResource
{

    /** Links to ADaM product versions. */
    default List<Link> adamLinks()
    {
        return getObject("_links", ApiResource.class)
                .flatMap(links -> links.getObject("data-analysis", ApiResource.class))
                .map(da -> da.getLinks("adam")).orElse(List.of());
    }


    /** Links to SDTM product versions. */
    default List<Link> sdtmLinks()
    {
        return getObject("_links", ApiResource.class)
                .flatMap(links -> links.getObject("data-tabulation", ApiResource.class))
                .map(dt -> dt.getLinks("sdtm")).orElse(List.of());
    }


    /** Links to SDTM-IG product versions. */
    default List<Link> sdtmigLinks()
    {
        return getObject("_links", ApiResource.class)
                .flatMap(links -> links.getObject("data-tabulation", ApiResource.class))
                .map(dt -> dt.getLinks("sdtmig")).orElse(List.of());
    }


    /** Links to SEND-IG product versions. */
    default List<Link> sendigLinks()
    {
        return getObject("_links", ApiResource.class)
                .flatMap(links -> links.getObject("data-tabulation", ApiResource.class))
                .map(dt -> dt.getLinks("sendig")).orElse(List.of());
    }


    /** Links to CDASH product versions. */
    default List<Link> cdashLinks()
    {
        return getObject("_links", ApiResource.class)
                .flatMap(links -> links.getObject("data-collection", ApiResource.class))
                .map(dc -> dc.getLinks("cdash")).orElse(List.of());
    }


    /** Links to CDASH-IG product versions. */
    default List<Link> cdashigLinks()
    {
        return getObject("_links", ApiResource.class)
                .flatMap(links -> links.getObject("data-collection", ApiResource.class))
                .map(dc -> dc.getLinks("cdashig")).orElse(List.of());
    }


    /** Links to CT packages. */
    default List<Link> terminologyLinks()
    {
        return getObject("_links", ApiResource.class)
                .flatMap(links -> links.getObject("terminology", ApiResource.class))
                .map(t -> t.getLinks("packages")).orElse(List.of());
    }


    /** Links to QRS instruments. */
    default List<Link> qrsLinks()
    {
        return getObject("_links", ApiResource.class)
                .flatMap(links -> links.getObject("qrs", ApiResource.class))
                .map(q -> q.getLinks("instrument")).orElse(List.of());
    }


    /** Returns all product links across all groups as a combined list. */
    default List<Link> allLinks()
    {
        return Stream
                .of(adamLinks(), sdtmLinks(), sdtmigLinks(), sendigLinks(), cdashLinks(),
                        cdashigLinks(), terminologyLinks(), qrsLinks())
                .flatMap(List::stream).toList();
    }
}
