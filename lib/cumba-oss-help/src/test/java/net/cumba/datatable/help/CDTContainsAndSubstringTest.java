package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Gap-closing tests for {@link CDT}, covering three families the existing suite reaches only
 * partially.
 *
 * <ol>
 * <li><b>The primitive {@code containsInt} / {@code containsLong} / {@code containsDouble}
 * overloads.</b> {@code CDTTest.testContainsInt()} and its two siblings look like they cover them
 * but do not: they call {@code CDT.contains(2, 1, 2, 3)}, whose arguments box and bind to
 * {@code contains(Object, Object...)}. Only the null/empty guards of the primitive overloads were
 * ever executed; their search loops were dead to the suite.</li>
 * <li><b>The "not found" answer of every {@code containsAny} / {@code containsIgnoreCase}
 * overload.</b> The existing tests assert the positive answer only. An implementation that returned
 * {@code true} for a <em>non-matching</em> element passed them, because the first element of every
 * fixture happens to be one the assertion is happy to accept either way.</li>
 * <li><b>The at-index-0 boundary of the {@code getBefore*} / {@code getAfter*} family</b>, and the
 * not-found answer of their {@code String}-excerpt overloads.</li>
 * </ol>
 */
class CDTContainsAndSubstringTest
{

    // ==================== the primitive contains* overloads ====================

    @Test
    void containsIntSearchesThePrimitiveArray()
    {
        assertTrue(CDT.containsInt(1, 1, 2, 3), "first element");
        assertTrue(CDT.containsInt(3, 1, 2, 3), "last element");
        assertFalse(CDT.containsInt(4, 1, 2, 3), "absent element");
        assertFalse(CDT.containsInt(0, new int[0]));
        assertFalse(CDT.containsInt(0, (int[]) null));
    }


    @Test
    void containsLongSearchesThePrimitiveArray()
    {
        assertTrue(CDT.containsLong(1L, 1L, 2L, 3L), "first element");
        assertTrue(CDT.containsLong(3L, 1L, 2L, 3L), "last element");
        assertFalse(CDT.containsLong(4L, 1L, 2L, 3L), "absent element");
        assertFalse(CDT.containsLong(0L, new long[0]));
        assertFalse(CDT.containsLong(0L, (long[]) null));
    }


    @Test
    void containsDoubleSearchesThePrimitiveArray()
    {
        assertTrue(CDT.containsDouble(1.5, 1.5, 2.5, 3.5), "first element");
        assertTrue(CDT.containsDouble(3.5, 1.5, 2.5, 3.5), "last element");
        assertFalse(CDT.containsDouble(4.5, 1.5, 2.5, 3.5), "absent element");
        assertFalse(CDT.containsDouble(0.0, new double[0]));
        assertFalse(CDT.containsDouble(0.0, (double[]) null));
    }


    @Test
    void containsDoubleComparesByDoubleCompareNotByEqualsOperator()
    {
        // Double.compare, unlike ==, makes NaN equal to itself and separates +0.0 from -0.0.
        assertTrue(CDT.containsDouble(Double.NaN, 1.0, Double.NaN),
                "NaN must be findable — the codecs use it for missing numerics");
        assertFalse(CDT.containsDouble(-0.0, 0.0));
        assertTrue(CDT.containsDouble(-0.0, 1.0, -0.0));
    }

    // ==================== containsAny — the negative answer ====================


    @Test
    void containsAnyCollectionListReportsAbsence()
    {
        List<String> list = List.of("a", "b");
        assertFalse(CDT.containsAny(list, List.of("x", "y")),
                "none of the candidates is in the collection");
        assertTrue(CDT.containsAny(list, List.of("x", "a")),
                "a match after a non-match is still a match");
    }


    @Test
    void containsAnyArrayReportsAbsence()
    {
        String[] array =
        {
                "a", "b"
        };
        assertFalse(CDT.containsAny(array, "x", "y"), "none of the candidates is in the array");
        assertTrue(CDT.containsAny(array, "x", "a"), "a match after a non-match is still a match");
    }


    @Test
    void containsAnyVarargsReportsAMatchOnlyAfterANonMatch()
    {
        assertTrue(CDT.containsAny(List.of("a", "b"), "x", "b"));
    }


    @Test
    void containsAnyRejectsAnEmptyOrNullSideWithoutSearching()
    {
        List<String> list = List.of("a");
        String[] array =
        {
                "a"
        };

        // Collection + varargs
        assertFalse(CDT.containsAny(List.<String> of(), "a"));
        assertFalse(CDT.containsAny((List<String>) null, "a"));
        assertFalse(CDT.containsAny(list, new String[0]));
        assertFalse(CDT.containsAny(list, (String[]) null));

        // Collection + List
        assertFalse(CDT.containsAny(List.<String> of(), List.of("a")));
        assertFalse(CDT.containsAny(list, List.<String> of()));
        assertFalse(CDT.containsAny(list, (List<String>) null));

        // array + varargs
        assertFalse(CDT.containsAny(new String[0], "a"));
        assertFalse(CDT.containsAny((String[]) null, "a"));
        assertFalse(CDT.containsAny(array, (Object[]) new String[0]));
    }

    // ==================== containsIgnoreCase — the negative answer ====================


    @Test
    void containsIgnoreCaseCollectionReportsAbsence()
    {
        assertFalse(CDT.containsIgnoreCase("xyz", List.of("abc", "def")));
        assertTrue(CDT.containsIgnoreCase("DEF", List.of("abc", "def")),
                "a match after a non-match is still a match");
    }


    @Test
    void containsIgnoreCaseCollectionFindsANullElement()
    {
        assertTrue(CDT.containsIgnoreCase(null, Arrays.asList("a", null)));
        assertFalse(CDT.containsIgnoreCase(null, List.of("a", "b")));
        assertFalse(CDT.containsIgnoreCase("a", (List<String>) null));
        assertFalse(CDT.containsIgnoreCase("a", List.<String> of()));
    }


    @Test
    void containsIgnoreCaseVarargsFindsAMatchOnlyAfterANonMatch()
    {
        assertTrue(CDT.containsIgnoreCase("DEF", "abc", "def"));
        assertTrue(CDT.containsIgnoreCase(null, "a", null));
    }


    @Test
    void containsIgnoreCaseVarargsRejectsAnEmptyOrNullArrayWithoutSearching()
    {
        assertFalse(CDT.containsIgnoreCase("a", new String[0]));
        assertFalse(CDT.containsIgnoreCase("a", (String[]) null));
        assertFalse(CDT.containsIgnoreCase(null, new String[0]));
    }

    // ==================== indexOfIgnoreCase ====================


    @Test
    void indexOfIgnoreCaseMatchesWhenTheTextCarriesTheUpperCaseForm()
    {
        // The scan skips any position whose character is neither the lower- nor the upper-case
        // form of the excerpt's first character. Every existing case has the text in the same
        // case as the excerpt, so the upper-case arm of that test was never the deciding one.
        assertEquals(1, CDT.indexOfIgnoreCase("aBc", "bc"));
        assertEquals(1, CDT.indexOfIgnoreCase("aBc", "Bc"));
        assertEquals(2, CDT.indexOfIgnoreCase("xyZebra", "zeb"));
        assertEquals(-1, CDT.indexOfIgnoreCase("aBc", "bd"));
    }


    @Test
    void indexOfIgnoreCaseDoesNotMatchPastTheEndOfTheText()
    {
        assertEquals(-1, CDT.indexOfIgnoreCase("ab", "abc"));
        assertEquals(0, CDT.indexOfIgnoreCase("abc", "abc"));
    }

    // ==================== trimRight ====================


    @Test
    void trimRightHandlesAnAllWhitespaceString()
    {
        assertEquals("", CDT.trimRight(" "), "a lone space trims to the empty string");
        assertEquals("", CDT.trimRight("   "));
        assertEquals("", CDT.trimRight(""));
        assertEquals("", CDT.trimRight("\u00A0"), "a lone non-breaking space also trims away");
        assertEquals("a", CDT.trimRight("a\u00A0 \t"));
    }


    @Test
    void trimRightKeepsLeadingWhitespace()
    {
        assertEquals("  a", CDT.trimRight("  a  "));
        assertEquals("a", CDT.trimRight("a"));
    }

    // ==================== getBefore* / getAfter*, separator at index 0 ====================


    @Test
    void getBeforeLastAtIndexZeroYieldsTheEmptyString()
    {
        assertEquals("", CDT.getBeforeLast("/abc", '/'));
        assertEquals("/", CDT.getBeforeLast("//", '/'));
        assertEquals("abc", CDT.getBeforeLast("abc", '/'), "no match returns the whole string");
    }


    @Test
    void getBeforeLastStringAtIndexZeroYieldsTheEmptyString()
    {
        assertEquals("", CDT.getBeforeLast("::abc", "::"));
        assertEquals("abc", CDT.getBeforeLast("abc", "::"), "no match returns the whole string");
    }


    @Test
    void getBeforeFirstAtIndexZeroYieldsTheEmptyString()
    {
        assertEquals("", CDT.getBeforeFirst("/abc/def", '/'));
    }


    @Test
    void getBeforeFirstStringAtIndexZeroYieldsTheEmptyString()
    {
        assertEquals("", CDT.getBeforeFirst("::abc::def", "::"));
        assertEquals("abc", CDT.getBeforeFirst("abc", "::"), "no match returns the whole string");
    }


    @Test
    void getAfterFirstAtIndexZeroYieldsTheRest()
    {
        assertEquals("abc/def", CDT.getAfterFirst("/abc/def", '/'));
    }


    @Test
    void getAfterFirstStringAtIndexZeroYieldsTheRest()
    {
        assertEquals("abc::def", CDT.getAfterFirst("::abc::def", "::"));
        assertEquals("abc", CDT.getAfterFirst("abc", "::"), "no match returns the whole string");
    }


    @Test
    void getAfterLastAtIndexZeroYieldsTheRest()
    {
        assertEquals("abc", CDT.getAfterLast("/abc", '/'));
        assertEquals("", CDT.getAfterLast("abc/", '/'));
    }


    @Test
    void getAfterLastStringAtIndexZeroYieldsTheRest()
    {
        assertEquals("abc", CDT.getAfterLast("::abc", "::"));
        assertEquals("abc", CDT.getAfterLast("abc", "::"), "no match returns the whole string");
    }
}
