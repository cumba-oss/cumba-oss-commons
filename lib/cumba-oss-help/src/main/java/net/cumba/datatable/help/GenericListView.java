package net.cumba.datatable.help;

import java.util.AbstractList;
import java.util.function.IntFunction;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * An unmodifiable {@link java.util.List} view backed by an {@link IntFunction} provider. Elements
 * are computed on access rather than stored, making this a zero-copy view into an underlying data
 * source.
 *
 * @param <T>
 *            the element type of this list. ⚠ The bound is {@code @Nullable Object}, not the
 *            implicit {@code Object}: a view over a data column must be able to carry null
 *            elements, so {@code GenericListView<@Nullable Object>} has to be a legal
 *            instantiation. Without the bound JSpecify rejects it, and the published twin's
 *            NullAway caught exactly that (2026-09-20) while this tree did not.
 */
public class GenericListView<T extends @Nullable Object> extends AbstractList<T>
{

    private final IntFunction<T> provider;

    @Getter
    private final int size;

    public GenericListView(IntFunction<T> aProvider, int aSize)
    {
        if (aSize < 0)
        {
            throw new IllegalArgumentException("Size must not be negative: " + aSize);
        }
        provider = aProvider;
        size = aSize;
    }


    @Override
    public T get(int aIndex)
    {
        if (aIndex < 0 || aIndex >= size)
        {
            throw new IndexOutOfBoundsException("Index: " + aIndex + ", Size: " + size);
        }
        return provider.apply(aIndex);
    }


    @Override
    public int size()
    {
        return size;
    }
}
