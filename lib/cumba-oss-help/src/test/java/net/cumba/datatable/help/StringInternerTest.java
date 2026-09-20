package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    // ==================== merged from the internal twin, 2026-09-20 ====================
    // ⭐ The two suites were INDEPENDENT DRIFT over a byte-identical class, with ZERO
    // overlapping method names and different naming conventions. Neither was a subset, so
    // overwriting either direction would have deleted real coverage. Owner ruled: merge.
    // Above: the published suite (shard-count rounding, cross-shard stability at 2000 entries).
    // Below: the internal suite (hash-collision distinction, weak-reference expunge with a GC
    // budget). Names are left in their original conventions rather than harmonised, so each
    // half stays greppable against the history it came from.


    /** Build a fresh, non-pooled String with the given content. */
    private static String fresh(String aContent)
    {
        return new String(aContent.toCharArray());
    }


    @Test
    void nullInternsToNull()
    {
        assertNull(new StringInterner().intern(null));
        assertNull(StringInterner.global(null));
    }


    @Test
    void returnsEqualContent()
    {
        StringInterner interner = new StringInterner();
        String canonical = interner.intern(fresh("STUDYID"));
        assertEquals("STUDYID", canonical);
    }


    @Test
    void equalContentReturnsSameInstance()
    {
        StringInterner interner = new StringInterner();
        String a = interner.intern(fresh("PLACEBO"));
        String b = interner.intern(fresh("PLACEBO"));
        assertSame(a, b, "equal-content interning must return the identical canonical instance");
    }


    @Test
    void firstArgumentBecomesCanonical()
    {
        StringInterner interner = new StringInterner();
        String first = fresh("ADSL");
        String returned = interner.intern(first);
        assertSame(first, returned, "the first interned instance is kept as the canonical one");
        assertSame(first, interner.intern(fresh("ADSL")));
    }


    @Test
    void distinctValuesAreIndependentAndCounted()
    {
        StringInterner interner = new StringInterner(1);
        for (int i = 0; i < 1000; i++)
        {
            String v = "VAL-" + i;
            assertSame(interner.intern(fresh(v)), interner.intern(fresh(v)));
        }
        assertEquals(1000, interner.size());
    }


    @Test
    void globalPoolIsShared()
    {
        String a = StringInterner.global(fresh("GLOBAL-SHARED-TOKEN"));
        String b = StringInterner.global(fresh("GLOBAL-SHARED-TOKEN"));
        assertSame(a, b);
    }


    @ParameterizedTest
    @ValueSource(ints =
    {
            1, 2, 3, 8, 16
    })
    void worksWithVariousShardCounts(int aShardCount)
    {
        StringInterner interner = new StringInterner(aShardCount);
        String a = interner.intern(fresh("SHARDED"));
        String b = interner.intern(fresh("SHARDED"));
        assertSame(a, b);
    }


    @Test
    void hashCollisionsAreDistinguishedByContent()
    {
        // "Aa" and "BB" have the same String#hashCode() (2112) yet differ in content. They must end
        // up as two distinct canonical entries, exercising the content comparison on a hash clash.
        StringInterner interner = new StringInterner(1);
        String aa = interner.intern(fresh("Aa"));
        String bb = interner.intern(fresh("BB"));
        assertEquals("Aa", aa);
        assertEquals("BB", bb);
        assertNotSame(aa, bb);
        assertSame(aa, interner.intern(fresh("Aa")));
        assertSame(bb, interner.intern(fresh("BB")));
        assertEquals(2, interner.size());
    }


    @Test
    void collectedEntriesAreExpunged() throws InterruptedException
    {
        StringInterner interner = new StringInterner(1);
        String unique = new String(new char[]
        {
                'g', 'c', '-', 't', 'o', 'k', 'e', 'n'
        });
        WeakReference<String> ref = new WeakReference<>(interner.intern(unique));
        unique = null;

        boolean cleared = false;
        for (int i = 0; i < 100 && !cleared; i++)
        {
            System.gc();
            Thread.sleep(10);
            cleared = ref.get() == null;
        }

        if (cleared)
        {
            // Touch the shard so the reference queue is drained, then confirm the dead entry is
            // gone
            // and re-interning the same content yields a brand-new canonical instance.
            interner.intern(fresh("trigger-expunge"));
            String reInterned = interner.intern(fresh("gc-token"));
            assertEquals("gc-token", reInterned);
            assertTrue(interner.size() <= 2,
                    "collected entry must not linger after expunge; size=" + interner.size());
        }
        // If the GC declined to collect within the budget we simply skip the strict assertion to
        // avoid a flaky test; the functional interning behaviour is covered by the other cases.
    }
}
