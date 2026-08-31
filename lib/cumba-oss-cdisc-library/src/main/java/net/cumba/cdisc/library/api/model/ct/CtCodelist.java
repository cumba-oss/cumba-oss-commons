package net.cumba.cdisc.library.api.model.ct;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;
import net.cumba.web.api.Link;

/**
 * A codelist within a CT package (response from
 * {@code /mdr/ct/packages/{package}/codelists/{codelist}}).
 *
 * <p>
 * Represents a controlled set of permissible values for a variable, identified by an NCI Thesaurus
 * concept identifier. Each codelist contains one or more terms.
 * </p>
 */
public interface CtCodelist extends ApiResource
{

    /**
     * Returns the NCI Thesaurus concept identifier (pattern: {@code C\d+}).
     *
     * @return the NCI Thesaurus concept identifier (pattern: {@code C\d+}).
     */
    default Optional<String> conceptId()
    {
        return getString("conceptId");
    }


    /**
     * Returns whether this codelist is extensible (sponsors may add terms).
     *
     * <p>
     * Accepts both wire forms. The CDISC Library serialises this field as a JSON <em>string</em>
     * ({@code "extensible": "false"}), which a strict {@code getBoolean} would reject — leaving
     * every live-API codelist with an empty extensibility and {@code ICodeList.isExtensible()}
     * {@code null}. The Python reference engine parses the same string at cache-build time
     * ({@code codelist.get("extensible", "").lower() == "true"} in
     * {@code cdisc_library_service.get_codelist_terms_map}), so its pickles carry a real boolean.
     * Accepting either keeps the live-API and pickle-backed paths in agreement.
     * </p>
     *
     * <p>
     * The leniency is deliberately scoped to this field rather than to
     * {@code ApiResource.getBoolean}, whose strict contract every other model accessor relies on.
     * Values other than {@code true}/{@code false} (case-insensitive) yield an empty result rather
     * than a silent {@code false}.
     * </p>
     *
     * @return whether this codelist is extensible, or empty when the field is absent or not a
     *         recognisable boolean.
     */
    default Optional<Boolean> extensible()
    {
        Optional<Boolean> direct = getBoolean("extensible");
        if (direct.isPresent())
        {
            return direct;
        }
        return getString("extensible")//
                .map(String::trim)//
                .filter(v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v))//
                .map(Boolean::parseBoolean);
    }


    /**
     * Returns the codelist name.
     *
     * @return the codelist name.
     */
    default Optional<String> name()
    {
        return getString("name");
    }


    /**
     * Returns the codelist submission value.
     *
     * @return the codelist submission value.
     */
    default Optional<String> submissionValue()
    {
        return getString("submissionValue");
    }


    /**
     * Returns the codelist definition.
     *
     * @return the codelist definition.
     */
    default Optional<String> definition()
    {
        return getString("definition");
    }


    /**
     * Returns the codelist preferred term.
     *
     * @return the codelist preferred term.
     */
    default Optional<String> preferredTerm()
    {
        return getString("preferredTerm");
    }


    /**
     * Returns the codelist synonyms.
     *
     * @return the codelist synonyms.
     */
    default List<String> synonyms()
    {
        return getStringList("synonyms");
    }


    /**
     * Returns embedded terms within this codelist.
     *
     * @return embedded terms within this codelist.
     */
    default List<CtTerm> terms()
    {
        return getList("terms", CtTerm.class);
    }


    /**
     * Returns a link to the parent package.
     *
     * @return link to the parent package.
     */
    default Optional<Link> parentPackageLink()
    {
        return getLink("parentPackage");
    }


    /**
     * Returns a link to the root item in the terminology hierarchy.
     *
     * @return link to the root item in the terminology hierarchy.
     */
    default Optional<Link> rootItemLink()
    {
        return getLink("rootItem");
    }


    /**
     * Returns a link to the prior version of this codelist.
     *
     * @return link to the prior version of this codelist.
     */
    default Optional<Link> priorVersionLink()
    {
        return getLink("priorVersion");
    }
}
