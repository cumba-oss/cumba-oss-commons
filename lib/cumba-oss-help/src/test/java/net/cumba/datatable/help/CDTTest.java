package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CDTTest
{

    // ==================== isEmptyOrNull ====================

    @Test
    void testIsEmptyOrNullStringNull()
    {
        assertTrue(CDT.isEmptyOrNull((String) null));
    }


    @Test
    void testIsEmptyOrNullStringEmpty()
    {
        assertTrue(CDT.isEmptyOrNull(""));
    }


    @Test
    void testIsEmptyOrNullStringNonEmpty()
    {
        assertFalse(CDT.isEmptyOrNull("abc"));
    }


    @Test
    void testIsEmptyOrNullIntArray()
    {
        assertTrue(CDT.isEmptyOrNull((int[]) null));
        assertTrue(CDT.isEmptyOrNull(new int[0]));
        assertFalse(CDT.isEmptyOrNull(new int[]
        {
                1
        }));
    }


    @Test
    void testIsEmptyOrNullDoubleArray()
    {
        assertTrue(CDT.isEmptyOrNull((double[]) null));
        assertTrue(CDT.isEmptyOrNull(new double[0]));
        assertFalse(CDT.isEmptyOrNull(new double[]
        {
                1.0
        }));
    }


    @Test
    void testIsEmptyOrNullLongArray()
    {
        assertTrue(CDT.isEmptyOrNull((long[]) null));
        assertTrue(CDT.isEmptyOrNull(new long[0]));
        assertFalse(CDT.isEmptyOrNull(new long[]
        {
                1L
        }));
    }


    @Test
    void testIsEmptyOrNullObjectArray()
    {
        assertTrue(CDT.isEmptyOrNull((String[]) null));
        assertTrue(CDT.isEmptyOrNull(new String[0]));
        assertFalse(CDT.isEmptyOrNull(new String[]
        {
                "a"
        }));
    }


    @Test
    void testIsEmptyOrNullCollection()
    {
        assertTrue(CDT.isEmptyOrNull((Collection<String>) null));
        assertTrue(CDT.isEmptyOrNull(new ArrayList<String>()));
        assertFalse(CDT.isEmptyOrNull(List.of("a")));
    }


    @Test
    void testIsEmptyOrNullMap()
    {
        assertTrue(CDT.isEmptyOrNull((Map<String, String>) null));
        assertTrue(CDT.isEmptyOrNull(new HashMap<String, String>()));
        assertFalse(CDT.isEmptyOrNull(Map.of("k", "v")));
    }

    // ==================== isBlankOrNull ====================


    @Test
    void testIsBlankOrNullNull()
    {
        assertTrue(CDT.isBlankOrNull(null));
    }


    @Test
    void testIsBlankOrNullBlank()
    {
        assertTrue(CDT.isBlankOrNull("   "));
    }


    @Test
    void testIsBlankOrNullNonBlank()
    {
        assertFalse(CDT.isBlankOrNull(" a "));
    }

    // ==================== equalsIgnoreCase ====================


    @Test
    void testEqualsIgnoreCaseBothNull()
    {
        assertTrue(CDT.equalsIgnoreCase(null, null));
    }


    @Test
    void testEqualsIgnoreCaseFirstNull()
    {
        assertFalse(CDT.equalsIgnoreCase(null, "abc"));
    }


    @Test
    void testEqualsIgnoreCaseSecondNull()
    {
        assertFalse(CDT.equalsIgnoreCase("abc", null));
    }


    @Test
    void testEqualsIgnoreCaseMatch()
    {
        assertTrue(CDT.equalsIgnoreCase("Abc", "ABC"));
        assertFalse(CDT.equalsIgnoreCase("abc", "xyz"));
    }

    // ==================== isIn ====================


    @Test
    void testIsIn()
    {
        assertTrue(CDT.isIn("b", "a", "b", "c"));
        assertFalse(CDT.isIn("d", "a", "b", "c"));
    }


    @Test
    void testIsInNull()
    {
        assertTrue(CDT.isIn(null, null, "a"));
        assertFalse(CDT.isIn(null, "a", "b"));
    }


    @Test
    void testIsInEmptyArray()
    {
        assertFalse(CDT.isIn("a", new Object[0]));
    }

    // ==================== containsIgnoreCase ====================


    @Test
    void testContainsIgnoreCaseVarargs()
    {
        assertTrue(CDT.containsIgnoreCase("ABC", "abc", "def"));
    }


    @Test
    void testContainsIgnoreCaseVarargsNotFound()
    {
        assertFalse(CDT.containsIgnoreCase("xyz", "abc", "def"));
    }


    @Test
    void testContainsIgnoreCaseVarargsNull()
    {
        assertTrue(CDT.containsIgnoreCase(null, null, "a"));
        assertFalse(CDT.containsIgnoreCase(null, "a", "b"));
    }


    @Test
    void testContainsIgnoreCaseCollection()
    {
        assertTrue(CDT.containsIgnoreCase("ABC", List.of("abc", "def")));
    }

    // ==================== trimRight ====================


    @Test
    void testTrimRightNull()
    {
        assertNull(CDT.trimRight(null));
    }


    @Test
    void testTrimRightNoTrailing()
    {
        assertEquals("abc", CDT.trimRight("abc"));
    }


    @Test
    void testTrimRightSpaces()
    {
        assertEquals("abc", CDT.trimRight("abc   "));
    }


    @Test
    void testTrimRightNBSP()
    {
        assertEquals("abc", CDT.trimRight("abc\u00A0"));
    }

    // ==================== getBeforeLast ====================


    @Test
    void testGetBeforeLastChar()
    {
        assertEquals("a.b", CDT.getBeforeLast("a.b.c", '.'));
    }


    @Test
    void testGetBeforeLastCharNotFound()
    {
        assertEquals("abc", CDT.getBeforeLast("abc", '.'));
    }


    @Test
    void testGetBeforeLastString()
    {
        assertEquals("abcXYZdef", CDT.getBeforeLast("abcXYZdefXYZghi", "XYZ"));
    }

    // ==================== getBeforeFirst ====================


    @Test
    void testGetBeforeFirstChar()
    {
        assertEquals("a", CDT.getBeforeFirst("a.b.c", '.'));
    }


    @Test
    void testGetBeforeFirstCharNotFound()
    {
        assertEquals("abc", CDT.getBeforeFirst("abc", '.'));
    }


    @Test
    void testGetBeforeFirstString()
    {
        assertEquals("abc", CDT.getBeforeFirst("abcXYZdefXYZghi", "XYZ"));
    }

    // ==================== getAfterLast ====================


    @Test
    void testGetAfterLastChar()
    {
        assertEquals("c", CDT.getAfterLast("a.b.c", '.'));
    }


    @Test
    void testGetAfterLastCharNotFound()
    {
        assertEquals("abc", CDT.getAfterLast("abc", '.'));
    }


    @Test
    void testGetAfterLastString()
    {
        assertEquals("c", CDT.getAfterLast("a/b/c", "/"));

        String result = CDT.getAfterLast("foohellofooworld", "foo");
        assertEquals("world", result);
        assertNotEquals("ooworld", result);
    }

    // ==================== constructor ====================


    @Test
    void testPrivateConstructorThrows() throws Exception
    {
        Constructor<CDT> ctor = CDT.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                ctor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }

    // ==================== containsIgnoreCase (additional) ====================


    @Test
    void testContainsIgnoreCaseVarargsEmpty()
    {
        assertFalse(CDT.containsIgnoreCase("x"));
        assertFalse(CDT.containsIgnoreCase("x", (String[]) null));
    }


    @Test
    void testContainsIgnoreCaseCollectionEmptyOrNull()
    {
        assertFalse(CDT.containsIgnoreCase("x", new ArrayList<String>()));
        assertFalse(CDT.containsIgnoreCase("x", (Collection<String>) null));
    }


    @Test
    void testContainsIgnoreCaseCollectionFoundAndNotFound()
    {
        assertTrue(CDT.containsIgnoreCase("ABC", List.of("abc", "def")));
        assertFalse(CDT.containsIgnoreCase("xyz", List.of("abc", "def")));
    }


    @Test
    void testContainsIgnoreCaseCollectionNullElement()
    {
        assertTrue(CDT.containsIgnoreCase(null, Arrays.asList("a", null)));
        assertFalse(CDT.containsIgnoreCase(null, List.of("a", "b")));
    }

    // ==================== trimRight (additional) ====================


    @Test
    void testTrimRightEmpty()
    {
        assertEquals("", CDT.trimRight(""));
    }


    @Test
    void testTrimRightAllWhitespace()
    {
        assertEquals("", CDT.trimRight("   "));
    }


    @Test
    void testTrimRightSingleTrailingSpace()
    {
        assertEquals("abc", CDT.trimRight("abc "));
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
        assertEquals("abc", CDT.tri("abc   "));
    }

    // ==================== intern ====================


    @Test
    void testInternNull()
    {
        assertNull(CDT.intern(null));
    }


    @Test
    void testInternReturnsCanonicalInstance()
    {
        // Distinct-but-equal instances (not from the constant pool) must
        // canonicalise to the very same reference. String.valueOf(char[])
        // yields a fresh String each call.
        String base = "corej-intern-test-value";
        String a = String.valueOf(base.toCharArray());
        String b = String.valueOf(base.toCharArray());
        assertNotSame(a, b);
        assertEquals(base, CDT.intern(a));
        assertSame(CDT.intern(a), CDT.intern(b));
    }

    // ==================== getBeforeLast / getBeforeFirst / getAfterLast (additional)
    // ====================


    @Test
    void testGetBeforeLastStringNotFound()
    {
        assertEquals("abc", CDT.getBeforeLast("abc", "XYZ"));
    }


    @Test
    void testGetBeforeFirstStringNotFound()
    {
        assertEquals("abc", CDT.getBeforeFirst("abc", "XYZ"));
    }


    @Test
    void testGetAfterLastStringNotFound()
    {
        assertEquals("abc", CDT.getAfterLast("abc", "XYZ"));
    }


    @Test
    void testGetBeforeLastNullArgsThrow()
    {
        assertThrows(NullPointerException.class, () -> CDT.getBeforeLast((String) null, '.'));
        assertThrows(NullPointerException.class, () -> CDT.getBeforeLast((String) null, "x"));
        assertThrows(NullPointerException.class, () -> CDT.getBeforeLast("abc", (String) null));
    }


    @Test
    void testGetBeforeFirstNullArgsThrow()
    {
        assertThrows(NullPointerException.class, () -> CDT.getBeforeFirst((String) null, '.'));
        assertThrows(NullPointerException.class, () -> CDT.getBeforeFirst((String) null, "x"));
        assertThrows(NullPointerException.class, () -> CDT.getBeforeFirst("abc", (String) null));
    }


    @Test
    void testGetAfterLastNullArgsThrow()
    {
        assertThrows(NullPointerException.class, () -> CDT.getAfterLast((String) null, '.'));
        assertThrows(NullPointerException.class, () -> CDT.getAfterLast((String) null, "x"));
        assertThrows(NullPointerException.class, () -> CDT.getAfterLast("abc", (String) null));
    }

}
