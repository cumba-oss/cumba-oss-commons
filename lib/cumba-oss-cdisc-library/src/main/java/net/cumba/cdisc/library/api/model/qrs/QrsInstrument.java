package net.cumba.cdisc.library.api.model.qrs;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A QRS instrument version (response from
 * {@code /mdr/qrs/instruments/{instrument}/versions/{version}}).
 *
 * <p>
 * Represents a Questionnaire, Rating Scale, or Functional Test (QRS) instrument used in clinical
 * trials to collect patient-reported outcomes or clinician assessments.
 * </p>
 */
public interface QrsInstrument extends ApiResource
{

    /**
     * Returns the instrument name.
     *
     * @return the instrument name.
     */
    default Optional<String> name()
    {
        return getString("name");
    }


    /**
     * Returns the instrument label.
     *
     * @return the instrument label.
     */
    default Optional<String> label()
    {
        return getString("label");
    }


    /**
     * Returns the instrument description.
     *
     * @return the instrument description.
     */
    default Optional<String> description()
    {
        return getString("description");
    }


    /**
     * Returns the effective date of this instrument version.
     *
     * @return the effective date of this instrument version.
     */
    default Optional<String> effectiveDate()
    {
        return getString("effectiveDate");
    }


    /**
     * Returns the date until which this version is valid.
     *
     * @return the date until which this version is valid.
     */
    default Optional<String> untilDate()
    {
        return getString("untilDate");
    }


    /**
     * Returns the registration status (e.g., "Final").
     *
     * @return the registration status (e.g., "Final").
     */
    default Optional<String> registrationStatus()
    {
        return getString("registrationStatus");
    }


    /**
     * Returns the instrument version identifier.
     *
     * @return the instrument version identifier.
     */
    default Optional<String> version()
    {
        return getString("version");
    }


    /**
     * Returns the instrument type (e.g., "Questionnaire", "Functional Test").
     *
     * @return the instrument type (e.g., "Questionnaire", "Functional Test").
     */
    default Optional<String> instrumentType()
    {
        return getString("instrumentType");
    }


    /**
     * Returns the copyright status of this instrument.
     *
     * @return the copyright status of this instrument.
     */
    default Optional<String> copyrightStatus()
    {
        return getString("copyrightStatus");
    }


    /**
     * Returns embedded response groups defining the possible responses.
     *
     * @return embedded response groups defining the possible responses.
     */
    default List<QrsResponseGroup> responseGroups()
    {
        return getList("responseGroups", QrsResponseGroup.class);
    }


    /**
     * Returns embedded items (questions) in this instrument.
     *
     * @return embedded items (questions) in this instrument.
     */
    default List<QrsItem> items()
    {
        return getList("items", QrsItem.class);
    }


    /**
     * Returns link to the prior version of this instrument.
     *
     * @return link to the prior version of this instrument.
     */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }


    /**
     * Returns link to the instrument CAT (Computerized Adaptive Testing) version.
     *
     * @return link to the instrument CAT (Computerized Adaptive Testing) version.
     */
    default Optional<Link> instrumentCatLink()
    {
        return getLink("instrumentCAT");
    }


    /**
     * Returns link to the instrument SCAT (Short CAT) version.
     *
     * @return link to the instrument SCAT (Short CAT) version.
     */
    default Optional<Link> instrumentScatLink()
    {
        return getLink("instrumentSCAT");
    }
}
