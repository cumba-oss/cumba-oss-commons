package net.cumba.cdisc.library.api.model.rules;

import java.util.List;
import java.util.Optional;

import net.cumba.web.api.ApiResource;

/**
 * A standard within an authority, containing version, references, and optionally a substandard.
 *
 * <p>
 * Represents a specific CDISC standard (e.g. SDTM, ADaM) and version to which a rule applies. May
 * include a substandard qualifier and a list of {@link RuleReference} entries that point to
 * specific documents and citations.
 * </p>
 */
public interface RuleStandard extends ApiResource
{

    /** Returns the standard name (e.g. "SDTM", "ADaM"). */
    default Optional<String> name()
    {
        return getString("Name");
    }


    /** Returns the standard version (e.g. "3.4"). */
    default Optional<String> version()
    {
        return getString("Version");
    }


    /** Returns the substandard qualifier, if any. */
    default Optional<String> substandard()
    {
        return getString("Substandard");
    }


    /** Returns the list of references within this standard. */
    default List<RuleReference> references()
    {
        return getList("References", RuleReference.class);
    }
}
