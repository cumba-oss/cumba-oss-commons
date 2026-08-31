package net.cumba.cdisc.library.api.model.meta;

import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * API about information (response from {@code /mdr/about}).
 *
 * <p>
 * Provides links to release notes and API documentation for the CDISC Library.
 * </p>
 */
public interface About extends ApiResource
{

    /** Returns the URL to the release notes. */
    default Optional<String> releaseNotes()
    {
        return getString("release-notes");
    }


    /** Returns the URL to the API documentation. */
    default Optional<String> apiDocumentation()
    {
        return getString("api-documentation");
    }
}
