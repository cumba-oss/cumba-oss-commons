package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Coverage-focused tests for the {@link CDT} helper methods that were not exercised by
 * {@link CDTTest}: the primitive {@code ofXxx(...)} array factories, the generic {@code of(...)}
 * factory, {@link CDT#indexOfIgnoreCase(CharSequence, CharSequence)} and {@link CDT#tri(String)}.
 */
class CDTPrimitiveAndStringTest
{

    // ==================== of(T...) ====================

    @Test
    void testOfReturnsProvidedArray()
    {
        String[] result = CDT.of("a", "b", "c");
        assertArrayEquals(new String[]
        {
                "a", "b", "c"
        }, result);
    }

    // ==================== ofBytes ====================


    @Test
    void testOfBytesNull()
    {
        byte[] result = CDT.ofBytes((byte[]) null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }


    @Test
    void testOfBytesValues()
    {
        byte[] result = CDT.ofBytes((byte) 1, (byte) 2, (byte) 3);
        assertArrayEquals(new byte[]
        {
                1, 2, 3
        }, result);
    }

    // ==================== ofShorts ====================


    @Test
    void testOfShortsNull()
    {
        short[] result = CDT.ofShorts((short[]) null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }


    @Test
    void testOfShortsValues()
    {
        short[] result = CDT.ofShorts((short) 4, (short) 5);
        assertArrayEquals(new short[]
        {
                4, 5
        }, result);
    }

    // ==================== ofChars ====================


    @Test
    void testOfCharsNull()
    {
        char[] result = CDT.ofChars((char[]) null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }


    @Test
    void testOfCharsValues()
    {
        char[] result = CDT.ofChars('a', 'b');
        assertArrayEquals(new char[]
        {
                'a', 'b'
        }, result);
    }

    // ==================== ofInts ====================


    @Test
    void testOfIntsNull()
    {
        int[] result = CDT.ofInts((int[]) null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }


    @Test
    void testOfIntsValues()
    {
        int[] result = CDT.ofInts(7, 8, 9);
        assertArrayEquals(new int[]
        {
                7, 8, 9
        }, result);
    }

    // ==================== ofLongs ====================


    @Test
    void testOfLongsNull()
    {
        long[] result = CDT.ofLongs((long[]) null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }


    @Test
    void testOfLongsValues()
    {
        long[] result = CDT.ofLongs(10L, 11L);
        assertArrayEquals(new long[]
        {
                10L, 11L
        }, result);
    }

    // ==================== ofDoubles ====================


    @Test
    void testOfDoublesNull()
    {
        double[] result = CDT.ofDoubles((double[]) null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }


    @Test
    void testOfDoublesValues()
    {
        double[] result = CDT.ofDoubles(1.5, 2.5);
        assertArrayEquals(new double[]
        {
                1.5, 2.5
        }, result);
    }

    // ==================== indexOfIgnoreCase ====================


    @Test
    void testIndexOfIgnoreCaseEmptyExcerpt()
    {
        assertEquals(0, CDT.indexOfIgnoreCase("anything", ""));
    }


    @Test
    void testIndexOfIgnoreCaseFound()
    {
        assertEquals(6, CDT.indexOfIgnoreCase("Hello World", "WORLD"));
    }


    @Test
    void testIndexOfIgnoreCaseFoundLowerCase()
    {
        assertEquals(0, CDT.indexOfIgnoreCase("ABCDEF", "abc"));
    }


    @Test
    void testIndexOfIgnoreCaseNotFound()
    {
        assertEquals(-1, CDT.indexOfIgnoreCase("Hello World", "xyz"));
    }


    @Test
    void testIndexOfIgnoreCaseSkipNonMatchingFirstChar()
    {
        // Exercises the "continue" branch where the first char doesn't match
        // before finding the actual match.
        assertEquals(6, CDT.indexOfIgnoreCase("xxxxxxFOObar", "foo"));
    }


    @Test
    void testIndexOfIgnoreCasePartialMatchReject()
    {
        // First char matches at index 0 but rest of region differs, so the
        // region-matches check returns false and the loop continues.
        assertEquals(5, CDT.indexOfIgnoreCase("FxxxxFOO", "FOO"));
    }

    // ==================== tri ====================


    @Test
    void testTriNull()
    {
        assertNull(CDT.tri(null));
    }


    @Test
    void testTriTrimsAndInterns()
    {
        String s = CDT.tri("hello   ");
        assertEquals("hello", s);
        // tri() canonicalises through the application-global StringInterner (no longer the JVM
        // string table), so its result is reference-equal to another tri()/intern() of the same
        // content — not necessarily to a compile-time literal.
        assertSame(s, CDT.tri("hello"));
        assertSame(s, CDT.intern(new String("hello".toCharArray())));
    }


    @Test
    void testTriEmpty()
    {
        assertTrue(CDT.tri("").isEmpty());
    }
}
