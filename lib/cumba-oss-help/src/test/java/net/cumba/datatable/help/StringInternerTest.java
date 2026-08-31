package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StringInternerTest
{

    @Test
    void intern_null_returnsNull()
    {
        assertNull(new StringInterner().intern(null));
    }


    @Test
    void intern_returnsEqualString()
    {
        StringInterner si = new StringInterner();
        String canonical = si.intern("hello");
        assertEquals("hello", canonical);
    }


    @Test
    void intern_equalContentDistinctInstances_returnsSameCanonical()
    {
        // Two distinct String objects with equal content; the interner must collapse them.
        String a = new String("subject");
        String b = new String("subject");
        assertNotSame(a, b);

        StringInterner si = new StringInterner();
        String first = si.intern(a);
        String second = si.intern(b);

        assertSame(first, second);
        assertSame(a, first, "first caller's instance becomes the canonical one");
    }


    @Test
    void intern_distinctContent_returnsDistinctInstances()
    {
        StringInterner si = new StringInterner();
        assertNotSame(si.intern("one"), si.intern("two"));
    }


    @Test
    void global_null_returnsNull()
    {
        assertNull(StringInterner.global(null));
    }


    @Test
    void global_dedupsProcessWide()
    {
        String first = StringInterner.global(new String("global-token"));
        String second = StringInterner.global(new String("global-token"));
        assertSame(first, second);
        assertEquals("global-token", first);
    }


    @Test
    void size_reflectsDistinctLiveEntries()
    {
        StringInterner si = new StringInterner();
        assertEquals(0, si.size());

        si.intern("a");
        si.intern("b");
        si.intern("c");
        assertEquals(3, si.size());

        // Re-interning existing content does not grow the table.
        si.intern("a");
        assertEquals(3, si.size());
    }


    @Test
    void constructor_explicitShardCount_isUsableAndDedups()
    {
        // Exercise the power-of-two rounding (3 -> 4) and the floor (0 -> 1) branches.
        for (int requested : new int[]
        {
                0, 1, 3, 5
        })
        {
            StringInterner si = new StringInterner(requested);
            String a = new String("shard-test");
            String b = new String("shard-test");
            assertSame(si.intern(a), si.intern(b),
                    "interner with requested shard count " + requested + " must dedup");
        }
    }


    @Test
    void intern_manyEntriesAcrossShards_areCanonicalAndStable()
    {
        StringInterner si = new StringInterner(8);
        int n = 2000;
        String[] canonical = new String[n];
        for (int i = 0; i < n; i++)
        {
            canonical[i] = si.intern("value-" + i);
        }
        assertEquals(n, si.size());

        // Re-interning equal content (distinct String instances) returns the same canonical refs.
        for (int i = 0; i < n; i++)
        {
            assertSame(canonical[i], si.intern(new String("value-" + i)));
        }
    }


    @Test
    void intern_afterGarbageCollection_doesNotThrowAndStaysConsistent()
    {
        // Best-effort exercise of the weak-reference expunge path: load many entries that become
        // unreachable, then prod the GC and keep interning so the reference queue is drained. GC is
        // not guaranteed, so this asserts only invariants that must hold regardless of whether
        // collection actually ran.
        StringInterner si = new StringInterner(4);
        for (int i = 0; i < 10_000; i++)
        {
            si.intern(new String("ephemeral-" + i));
        }
        for (int i = 0; i < 5; i++)
        {
            System.gc();
            si.intern("survivor-" + i);
        }
        assertTrue(si.size() >= 0);
        // A freshly interned, still-referenced string remains canonical after the churn.
        String pinned = si.intern(new String("pinned"));
        assertSame(pinned, si.intern(new String("pinned")));
    }
}
