package net.cumba.datatable.help;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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
     * Convenient method to generate and initialize an array as a one liner.
     *
     * @param <T>
     *            the element type.
     * @param aElements
     *            the elements to be added to the array.
     * @return aElement as provided. This might be null.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> T[] of(T... aElements)
    {
        return aElements;
    }


    /**
     * Convenient method to generate and initialize a primitive array as a one liner. This
     * additionally has a null check. If aElements is null, then an empty array is returned.
     *
     * @param aElements
     *            the elements to be added to the array.
     * @return if aElements is not null it is returned, otherwise an empty array.
     */
    public static byte[] ofBytes(byte... aElements)
    {
        if (aElements == null)
        {
            return new byte[0];
        }
        return aElements;
    }


    /**
     * Convenient method to generate and initialize a primitive array as a one liner. This
     * additionally has a null check. If aElements is null, then an empty array is returned.
     *
     * @param aElements
     *            the elements to be added to the array.
     * @return if aElements is not null it is returned, otherwise an empty array.
     */
    public static short[] ofShorts(short... aElements)
    {
        if (aElements == null)
        {
            return new short[0];
        }
        return aElements;
    }


    /**
     * Convenient method to generate and initialize a primitive array as a one liner. This
     * additionally has a null check. If aElements is null, then an empty array is returned.
     *
     * @param aElements
     *            the elements to be added to the array.
     * @return if aElements is not null it is returned, otherwise an empty array.
     */
    public static char[] ofChars(char... aElements)
    {
        if (aElements == null)
        {
            return new char[0];
        }
        return aElements;
    }


    /**
     * Convenient method to generate and initialize a primitive array as a one liner. This
     * additionally has a null check. If aElements is null, then an empty array is returned.
     *
     * @param aElements
     *            the elements to be added to the array.
     * @return if aElements is not null it is returned, otherwise an empty array.
     */
    public static int[] ofInts(int... aElements)
    {
        if (aElements == null)
        {
            return new int[0];
        }
        return aElements;
    }


    /**
     * Convenient method to generate and initialize a primitive array as a one liner. This
     * additionally has a null check. If aElements is null, then an empty array is returned.
     *
     * @param aElements
     *            the elements to be added to the array.
     * @return if aElements is not null it is returned, otherwise an empty array.
     */
    public static long[] ofLongs(long... aElements)
    {
        if (aElements == null)
        {
            return new long[0];
        }
        return aElements;
    }


    /**
     * Convenient method to generate and initialize a primitive array as a one liner. This
     * additionally has a null check. If aElements is null, then an empty array is returned.
     *
     * @param aElements
     *            the elements to be added to the array.
     * @return if aElements is not null it is returned, otherwise an empty array.
     */
    public static double[] ofDoubles(double... aElements)
    {
        if (aElements == null)
        {
            return new double[0];
        }
        return aElements;
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
    public static boolean isEmptyOrNull(int @Nullable [] anArray)
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
    public static boolean isEmptyOrNull(double @Nullable [] anArray)
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
    public static boolean isEmptyOrNull(long @Nullable [] anArray)
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
    public static <T> boolean isEmptyOrNull(T @Nullable [] anArray)
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
    public static <K, V> boolean isEmptyOrNull(@Nullable Map<K, V> aMap)
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
        return contains(aElement, aElements);
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
    public static boolean contains(Object aElement, Object... aElements)
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
     * Null save check if the given element is in the given array of elements.
     *
     * @param aElement
     *            the element to search for.
     * @param aElements
     *            the array of elements to search in.
     * @return true if aElement is contained in aElements.
     */
    public static boolean containsInt(int aElement, int... aElements)
    {
        if (isEmptyOrNull(aElements))
        {
            return false;
        }
        for (int i = 0; i < aElements.length; i++)
        {
            if (aElement == aElements[i])
            {
                return true;
            }
        }
        return false;
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
    public static boolean containsLong(long aElement, long... aElements)
    {
        if (isEmptyOrNull(aElements))
        {
            return false;
        }
        for (int i = 0; i < aElements.length; i++)
        {
            if (aElement == aElements[i])
            {
                return true;
            }
        }
        return false;
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
    public static boolean containsDouble(double aElement, double... aElements)
    {
        if (isEmptyOrNull(aElements))
        {
            return false;
        }
        for (int i = 0; i < aElements.length; i++)
        {
            if (Double.compare(aElement, aElements[i]) == 0)
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Check if aList contains at least one of the elements in aElements.
     *
     * @param <T>
     *            the type of the list and elements.
     * @param aList
     *            the list to check for.
     * @param aElements
     *            the elements to check.
     * @return true if at least one element in aElements is contained in aList.
     */
    @SuppressWarnings("unchecked")
    public static <T, E> boolean containsAny(Collection<T> aList, E... aElements)
    {
        if (isEmptyOrNull(aList) || isEmptyOrNull(aElements))
        {
            return false;
        }
        for (Object e : aElements)
        {
            if (aList.contains(e))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Check if aList contains at least one of the elements in aElements.
     *
     * @param <T>
     *            the type of the list and elements.
     * @param aList
     *            the list to check for.
     * @param aElements
     *            the elements to check.
     * @return true if at least one element in aElements is contained in aList.
     */
    public static <T> boolean containsAny(Collection<T> aList, List<T> aElements)
    {
        if (isEmptyOrNull(aList) || isEmptyOrNull(aElements))
        {
            return false;
        }
        for (T e : aElements)
        {
            if (aList.contains(e))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Check if aList contains at least one of the elements in aElements.
     *
     * @param <T>
     *            the type of the list and elements.
     * @param aList
     *            the list to check for.
     * @param aElements
     *            the elements to check.
     * @return true if at least one element in aElements is contained in aList.
     */
    public static <T> boolean containsAny(T[] aList, Object... aElements)
    {
        if (isEmptyOrNull(aList) || isEmptyOrNull(aElements))
        {
            return false;
        }
        List<T> list = Arrays.asList(aList);
        for (Object e : aElements)
        {
            if (list.contains(e))
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
    public static boolean containsIgnoreCase(@Nullable String aElement,
            String @Nullable... aElements)
    {
        if (aElements == null || aElements.length == 0)
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
    public static boolean containsIgnoreCase(@Nullable String aElement,
            @Nullable Collection<String> aElements)
    {
        if (aElements == null || aElements.isEmpty())
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
     * Find the index of the first occurrence of the given excerpt in the given text. This is a
     * implementation of {@link String#indexOf(String)} for {@link CharSequence}es that also ignores
     * the case while comparing.
     *
     * @param aText
     *            the text to search in.
     * @param aExcerpt
     *            the excerpt to search for.
     * @return the index of the first match or -1 if no match is found.
     */
    public static int indexOfIgnoreCase(@NonNull CharSequence aText, @NonNull CharSequence aExcerpt)
    {
        if (aExcerpt.isEmpty())
        {
            return 0;
        }

        int textLen = aText.length();
        int searchLen = aExcerpt.length();
        char firstLower = Character.toLowerCase(aExcerpt.charAt(0));
        char firstUpper = Character.toUpperCase(aExcerpt.charAt(0));

        for (int i = 0; i <= textLen - searchLen; i++)
        {
            char c = aText.charAt(i);
            if (c != firstLower && c != firstUpper)
            {
                continue;
            }

            if (regionMatchesIgnoreCase(aText, i, aExcerpt, searchLen))
            {
                return i;
            }
        }
        return -1;
    }


    /**
     * Check if a region of the given text matches the given excerpt when ignoring the case. This is
     * a implementation of {@link String#regionMatches(boolean, int, String, int, int)} for
     * {@link CharSequence}es.
     *
     * @param aText
     *            the text to check in.
     * @param aTargetOffset
     *            the start offset in aText to start comparing
     * @param aExcerpt
     *            the text to check against.
     * @param aLength
     *            the number of chars to check.
     * @return true if the given text contains the given excerpt at the given offset.
     */
    private static boolean regionMatchesIgnoreCase(@NonNull CharSequence aText, int aTargetOffset,
            @NonNull CharSequence aExcerpt, int aLength)
    {
        for (int i = 0; i < aLength; i++)
        {
            char c1 = aText.charAt(aTargetOffset + i);
            char c2 = aExcerpt.charAt(i);
            if (c1 != c2 && Character.toLowerCase(c1) != Character.toLowerCase(c2))
            {
                return false;
            }
        }
        return true;
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
        if (len >= 0 && isWhitespaceChar(aString.charAt(len)))
        {
            len--;
            while ((len >= 0) && isWhitespaceChar(aString.charAt(len)))
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
     * A special check for whitespace chars, that also includes the non breaking space u00A0
     *
     * @param aChar
     *            the char to check.
     * @return true if this is a whitespae char.
     */
    public static boolean isWhitespaceChar(char aChar)
    {
        return aChar <= ' ' || aChar == '\u00A0';
    }


    /**
     * Create a stream from the given elements. This is a wrapper for
     * {@link Arrays#stream(Object[])} that allows ... notation.
     *
     * @param <T>
     *            the type of the stream to be created.
     * @param aElements
     *            the elements in the stream.
     * @return the stream of given elements.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Stream<T> stream(T... aElements)
    {
        if (isEmptyOrNull(aElements))
        {
            return Stream.empty();
        }
        return Arrays.stream(aElements);
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
     * Retrieve the part of the given String that is behind the first occurrence of the given char.
     *
     * @param aString
     *            the string to take a subset of.
     * @param aChar
     *            the char to retrieve the subset after the first occurrence.
     * @return the part of aString that is behind the first occurrence of aChar, or the complete
     *         aString if aChar does not occur in aString.
     */
    public static String getAfterFirst(@NonNull String aString, char aChar)
    {
        int idx = aString.indexOf(aChar);
        if (idx >= 0)
        {
            return aString.substring(idx + 1);
        }
        return aString;
    }


    /**
     * Retrieve the part of the given String that is behind the first occurrence of the given
     * excerpt.
     *
     * @param aString
     *            the string to take a subset of.
     * @param aExcerpt
     *            the excerpt to retrieve the subset after the first occurrence.
     * @return the part of aString that is behind the first occurrence of aExcerpt, or the complete
     *         aString if aExcerpt does not occur in aString.
     */
    public static String getAfterFirst(@NonNull String aString, @NonNull String aExcerpt)
    {
        int idx = aString.indexOf(aExcerpt);
        if (idx >= 0)
        {
            return aString.substring(idx + aExcerpt.length());
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
