package net.cumba.cdisc.library.api.model.meta;

import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * Maintenance status (response from {@code /mdr/maintenance}).
 *
 * <p>
 * Indicates whether the CDISC Library API is currently in maintenance mode and provides an optional
 * message describing the maintenance.
 * </p>
 */
public interface Maintenance extends ApiResource
{

    /** Returns whether the API is in maintenance mode. */
    default Optional<Boolean> maintenanceMode()
    {
        return getBoolean("maintenanceMode");
    }


    /** Returns the maintenance message, if any. */
    default Optional<String> maintenanceMessage()
    {
        return getString("maintenanceMessage");
    }
}
