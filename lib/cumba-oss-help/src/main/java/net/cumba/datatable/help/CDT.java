package net.cumba.datatable.help;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Global static helper methods for Cumba Data Table Viewer.
 */
public class CDT
{

    private CDT()
    {
        throw new UnsupportedOperationException("utility class");
    }


    /**
     * A null save check for strings if these are empty.
     *
     * @param aValue
     *            the value to check.
     * @return true if the given value is null or {@link String#isEmpty()} returns true for the
     *         value.
     */
    public static boolean isEmptyOrNull(@Nullable String aValue)
    {
        return aValue == null || aValue.isEmpty();
    }


    /**
     * A null save check for an array, if this is empty.
     *
     * @param anArray
     *            the array to check.
     * @return true if the given array is null or has a length of 0.
     */
    public static boolean isEmptyOrNull(int[] anArray)
    {
        return anArray == null || anArray.length == 0;
    }


    /**
     * A null save check for an array, if this is empty.
     *
     * @param anArray
     *            the array to check.
     * @return true if the given array is null or has a length of 0.
     */
    public static boolean isEmptyOrNull(double[] anArray)
    {
        return anArray == null || anArray.length == 0;
    }


    /**
     * A null save check for an array, if this is empty.
     *
     * @param anArray
     *            the array to check.
     * @return true if the given array is null or has a length of 0.
     */
    public static boolean isEmptyOrNull(long[] anArray)
    {
        return anArray == null || anArray.length == 0;
    }


    /**
     * A null save check for an array, if this is empty.
     *
     * @param anArray
     *            the array to check.
     * @return true if the given array is null or has a length of 0.
     */
    public static <T> boolean isEmptyOrNull(T[] anArray)
    {
        return anArray == null || anArray.length == 0;
    }


    /**
     * A null save check for an Collection, if this is empty.
     *
     * @param aCollection
     *            the Collection to check.
     * @return true if the given Collection is null or has a size of 0.
     */
    public static <T> boolean isEmptyOrNull(@Nullable Collection<T> aCollection)
    {
        return aCollection == null || aCollection.isEmpty();
    }


    /**
     * A null save check for an Map, if this is empty.
     *
     * @param aMap
     *            the Map to check.
     * @return true if the given Map is null or has a size of 0.
     */
    public static <K, V> boolean isEmptyOrNull(Map<K, V> aMap)
    {
        return aMap == null || aMap.isEmpty();
    }


    /**
     * A null save equals check for Strings that uses {@link String#equalsIgnoreCase(String)}.
     *
     * @param aString1
     *            the first string to check.
     * @param aString2
     *            the second string to check.
     * @return true if both strings are null or have the same content when case is ignored.
     */
    public static boolean equalsIgnoreCase(@Nullable String aString1, @Nullable String aString2)
    {
        if (aString1 == null)
        {
            return aString2 == null;
        }
        if (aString2 == null)
        {
            return false;
        }
        return aString1.equalsIgnoreCase(aString2);
    }


    /**
     * A null save check for strings if these are blank.
     *
     * @param aValue
     *            the value to check.
     * @return true if the given value is null or {@link String#isBlank()} returns true for the
     *         value.
     */
    public static boolean isBlankOrNull(@Nullable String aValue)
    {
        return aValue == null || aValue.isBlank();
    }


    /**
     * Null save check if the given element is in the given array of elements.
     *
     * @param aElement
     *            the element to search for.
     * @param aElements
     *            the array of elements to search in.
     * @return true if aElement is contained in aElements.
     */
    public static boolean isIn(Object aElement, Object... aElements)
    {
        if (isEmptyOrNull(aElements))
        {
            return false;
        }
        for (int i = 0; i < aElements.length; i++)
        {
            if (Objects.equals(aElement, aElements[i]))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Null save check if the given string is in the given array of Strings where comparing is done
     * with ignored case.
     *
     * @param aElement
     *            the element to search for.
     * @param aElements
     *            the array of elements to search in.
     * @return true if aElement is contained in aElements.
     */
    public static boolean containsIgnoreCase(String aElement, String... aElements)
    {
        if (isEmptyOrNull(aElements))
        {
            return false;
        }
        int size = aElements.length;
        if (aElement == null)
        {
            // we search for a null element
            for (int i = 0; i < size; i++)
            {
                if (aElements[i] == null)
                {
                    return true;
                }
            }
        }
        else
        {
            // aElement is not null, so we can use equalsIgnoreCase
            for (int i = 0; i < size; i++)
            {
                if (aElement.equalsIgnoreCase(aElements[i]))
                {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Null save check if the given string is in the given Collection of Strings where comparing is
     * done with ignored case.
     *
     * @param aElement
     *            the element to search for.
     * @param aElements
     *            the Collection of elements to search in.
     * @return true if aElement is contained in aElements.
     */
    public static boolean containsIgnoreCase(String aElement, Collection<String> aElements)
    {
        if (isEmptyOrNull(aElements))
        {
            return false;
        }
        if (aElement == null)
        {
            // we search for a null element
            for (String e : aElements)
            {
                if (e == null)
                {
                    return true;
                }
            }
        }
        else
        {
            // aElement is not null, so we can use equalsIgnoreCase
            for (String e : aElements)
            {
                if (aElement.equalsIgnoreCase(e))
                {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Removes trailing whitespaces from string.
     *
     * @return the trimmed string.
     */
    public static String trimRight(String aString)
    {
        if (aString == null)
        {
            return aString;
        }
        int len = aString.length() - 1;
        if (len >= 0 && isWs(aString.charAt(len)))
        {
            len--;
            while ((len >= 0) && isWs(aString.charAt(len)))
            {
                len--;
            }
            return aString.substring(0, len + 1);
        }
        return aString;
    }


    public static @Nullable String tri(@Nullable String aString)
    {
        if (aString == null)
        {
            return null;
        }
        return intern(trimRight(aString));
    }


    /**
     * Canonicalise (intern) the given string against the application-global {@link StringInterner}.
     * This is a drop-in replacement for {@link String#intern()} that deduplicates strings
     * process-wide across all open data tables while scaling far better under high-cardinality,
     * highly-concurrent loads. Unlike {@link #tri(String)} it does <b>not</b> right-trim — callers
     * that need trimming should use {@link #tri(String)}.
     *
     * @param aString
     *            the string to canonicalise; may be {@code null}.
     * @return {@code null} if {@code aString} is {@code null}; otherwise the canonical instance
     *         that is {@link String#equals(Object) equal} to {@code aString}.
     */
    public static @Nullable String intern(@Nullable String aString)
    {
        return StringInterner.global(aString);
    }


    /**
     * Whitespace check that also treats the non-breaking space (U+00A0) as whitespace. Private —
     * only used internally by {@link #trimRight(String)}.
     */
    private static boolean isWs(char aChar)
    {
        return aChar <= ' ' || aChar == ' ';
    }


    /**
     * Retrieve the part of the given String that is before the last occurrence of the given char.
     *
     * @param aString
     *            the string to take a subset of.
     * @param aChar
     *            the char to retrieve the subset before the last occurrence.
     * @return the part of aString that is before the last occurrence of aChar, or the complete
     *         aString if aChar does not occur in aString.
     */
    public static String getBeforeLast(@NonNull String aString, char aChar)
    {
        int idx = aString.lastIndexOf(aChar);
        if (idx >= 0)
        {
            return aString.substring(0, idx);
        }
        return aString;
    }


    /**
     * Retrieve the part of the given String that is before the last occurrence of the given
     * excerpt.
     *
     * @param aString
     *            the string to take a subset of.
     * @param aExcerpt
     *            the excerpt to retrieve the subset before the last occurrence.
     * @return the part of aString that is before the last occurrence of aExcerpt, or the complete
     *         aString if aExcerpt does not occur in aString.
     */
    public static String getBeforeLast(@NonNull String aString, @NonNull String aExcerpt)
    {
        int idx = aString.lastIndexOf(aExcerpt);
        if (idx >= 0)
        {
            return aString.substring(0, idx);
        }
        return aString;
    }


    /**
     * Retrieve the part of the given String that is before the first occurrence of the given char.
     *
     * @param aString
     *            the string to take a subset of.
     * @param aChar
     *            the char to retrieve the subset before the first occurrence.
     * @return the part of aString that is before the first occurrence of aChar, or the complete
     *         aString if aChar does not occur in aString.
     */
    public static String getBeforeFirst(@NonNull String aString, char aChar)
    {
        int idx = aString.indexOf(aChar);
        if (idx >= 0)
        {
            return aString.substring(0, idx);
        }
        return aString;
    }


    /**
     * Retrieve the part of the given String that is before the first occurrence of the given
     * excerpt.
     *
     * @param aString
     *            the string to take a subset of.
     * @param aExcerpt
     *            the excerpt to retrieve the subset before the first occurrence.
     * @return the part of aString that is before the first occurrence of aExcerpt, or the complete
     *         aString if aExcerpt does not occur in aString.
     */
    public static String getBeforeFirst(@NonNull String aString, @NonNull String aExcerpt)
    {
        int idx = aString.indexOf(aExcerpt);
        if (idx >= 0)
        {
            return aString.substring(0, idx);
        }
        return aString;
    }


    /**
     * Retrieve the part of the given String that is behind the last occurrence of the given char.
     *
     * @param aString
     *            the string to take a subset of.
     * @param aChar
     *            the char to retrieve the subset after the last occurrence.
     * @return the part of aString that is behind the last occurrence of aChar, or the complete
     *         aString if aChar does not occur in aString.
     */
    public static String getAfterLast(@NonNull String aString, char aChar)
    {
        int idx = aString.lastIndexOf(aChar);
        if (idx >= 0)
        {
            return aString.substring(idx + 1);
        }
        return aString;
    }


    /**
     * Retrieve the part of the given String that is behind the last occurrence of the given
     * excerpt.
     *
     * @param aString
     *            the string to take a subset of.
     * @param aExcerpt
     *            the excerpt to retrieve the subset after the last occurrence.
     * @return the part of aString that is behind the last occurrence of aExcerpt, or the complete
     *         aString if aExcerpt does not occur in aString.
     */
    public static String getAfterLast(@NonNull String aString, @NonNull String aExcerpt)
    {
        int idx = aString.lastIndexOf(aExcerpt);
        if (idx >= 0)
        {
            return aString.substring(idx + aExcerpt.length());
        }
        return aString;
    }

}
