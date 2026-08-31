package net.cumba.cdisc.library.api.model.diff;

import java.util.List;

import net.cumba.web.api.ApiResource;

/**
 * Diff result between product versions (response from {@code /mdr/diff/{product}/{version}} or
 * {@code /mdr/diff/{product}/{version}/{previous}}).
 */
public interface DiffResult extends ApiResource
{

    /** The diff entries. Each entry has a title, head columns, and body rows. */
    default List<DiffEntry> diff()
    {
        return getList("diff", DiffEntry.class);
    }
}
