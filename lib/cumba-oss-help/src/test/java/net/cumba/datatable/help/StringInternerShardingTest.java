package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.ReferenceQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Two things about {@link StringInterner} that the behavioural tests cannot see.
 *
 * <ol>
 * <li><b>The shard count contract.</b> {@code StringInternerTest} proves that interning works for
 * any requested shard count, which is exactly why it cannot tell a correct rounding from a wrong
 * one — every count produces a working interner. The documented contract is "rounded up to the next
 * power of two, with a floor of 2", and that is what is pinned here.</li>
 * <li><b>{@code WeakElement.equals} outside the happy path.</b>
 * {@link java.util.concurrent.ConcurrentHashMap} short-circuits on reference identity and only
 * consults {@code equals} within a single hash bin, so three of its branches — the identity
 * shortcut, the non-{@code WeakElement} argument and the hash mismatch — are unreachable through
 * the interner's own use. They are still part of the key contract that makes expunging a dead entry
 * safe, so they are exercised directly.</li>
 * </ol>
 */
class StringInternerShardingTest
{

    @ParameterizedTest(name = "requesting {0} shards yields {1}")
    @CsvSource(
    {
            "1, 2", "2, 2", "3, 4", "4, 4", "5, 8", "7, 8", "8, 8", "9, 16", "16, 16", "17, 32"
    })
    void shardCountIsRoundedUpToAPowerOfTwoWithAFloorOfTwo(int aRequested, int aExpected)
    {
        assertEquals(aExpected, new StringInterner(aRequested).getShardCount());
    }


    @ParameterizedTest(name = "a non-positive request ({0}) still yields the floor")
    @CsvSource(
    {
            "0", "-1", "-1000"
    })
    void shardCountNeverFallsBelowTheFloor(int aRequested)
    {
        assertEquals(2, new StringInterner(aRequested).getShardCount());
    }


    @Test
    void defaultShardCountScalesWithTheAvailableProcessors()
    {
        int processors = Runtime.getRuntime().availableProcessors();
        int shards = new StringInterner().getShardCount();

        assertEquals(Integer.highestOneBit(shards), shards, "the shard count must be a power of 2");
        assertTrue(shards >= Math.max(4, processors * 2),
                "the default must give at least two shards per processor (floor 4); processors="
                        + processors + ", shards=" + shards);
    }


    @Test
    void everyShardCountStillCanonicalisesCorrectly()
    {
        // The shard index is derived from the hash, so a count/mask mismatch would send equal
        // content to two different shards and quietly stop deduplicating.
        StringInterner interner = new StringInterner(16);
        assertEquals(16, interner.getShardCount());
        for (int i = 0; i < 500; i++)
        {
            String v = "SUBJ-" + i;
            assertSame(interner.intern(new String(v.toCharArray())),
                    interner.intern(new String(v.toCharArray())));
        }
        assertEquals(500, interner.size());
    }

    // ==================== WeakElement ====================


    private static StringInterner.WeakElement element(String aContent, int aHash)
    {
        return new StringInterner.WeakElement(aContent, aHash, new ReferenceQueue<>());
    }


    @Test
    void weakElementReportsTheHashItWasBuiltWith()
    {
        // Cached rather than recomputed, so the element stays usable as a map key for removal
        // after its referent has been collected and get() returns null.
        assertEquals(4711, element("ADSL", 4711).hashCode());
        assertEquals(-1, element("ADSL", -1).hashCode());
    }


    @Test
    void weakElementEqualsItself()
    {
        StringInterner.WeakElement e = element("ADSL", 1);
        assertTrue(e.equals(e), "an element must equal itself even once its referent is gone");
    }


    @Test
    void weakElementDoesNotEqualAForeignType()
    {
        assertFalse(element("ADSL", 1).equals("ADSL"));
        assertFalse(element("ADSL", 1).equals(null));
    }


    @Test
    void weakElementWithADifferentHashIsNotEqual()
    {
        assertNotEquals(element("ADSL", 1), element("ADSL", 2),
                "equal content under a different cached hash must not collapse into one entry");
    }


    @Test
    void weakElementWithTheSameHashComparesByContent()
    {
        assertEquals(element("ADSL", 1), element("ADSL", 1));
        assertNotEquals(element("ADSL", 1), element("ADAE", 1),
                "a hash clash must be resolved by content");
    }
}
