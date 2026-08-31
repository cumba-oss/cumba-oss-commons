package net.cumba.cdisc.library.api.model.products;

import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * Last-updated timestamps (response from {@code /mdr/lastupdated}).
 *
 * <p>
 * Provides the last modification timestamps for each product group in the CDISC Library.
 * </p>
 */
public interface LastUpdated extends ApiResource
{

    /** Returns the overall last-updated timestamp. */
    default Optional<String> overall()
    {
        return getString("overall");
    }


    /** Returns the last-updated timestamp for data-analysis products (ADaM). */
    default Optional<String> dataAnalysis()
    {
        return getString("data-analysis");
    }


    /** Returns the last-updated timestamp for data-collection products (CDASH). */
    default Optional<String> dataCollection()
    {
        return getString("data-collection");
    }


    /** Returns the last-updated timestamp for data-tabulation products (SDTM/SEND). */
    default Optional<String> dataTabulation()
    {
        return getString("data-tabulation");
    }


    /** Returns the last-updated timestamp for QRS instruments. */
    default Optional<String> qrs()
    {
        return getString("qrs");
    }


    /** Returns the last-updated timestamp for controlled terminology. */
    default Optional<String> terminology()
    {
        return getString("terminology");
    }
}
