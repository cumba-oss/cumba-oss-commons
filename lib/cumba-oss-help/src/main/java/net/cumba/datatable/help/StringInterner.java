package net.cumba.datatable.help;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Application-global, sharded, weak-reference {@link String} canonicaliser ("interner").
 *
 * <p>
 * This is a drop-in replacement for {@link String#intern()} with the <em>same observable
 * semantics</em> — every distinct string content maps to a single canonical {@link String}
 * instance, shared <b>process-wide across all open data tables</b>, and that instance is reclaimed
 * by the garbage collector once no strong reference to it remains (e.g. when the last data table
 * holding it is closed). The difference is the <em>mechanism</em>: instead of the JVM's single,
 * fixed-size, globally synchronised native string table — which becomes a contention and
 * bucket-chain wall when canonicalising hundreds of millions of high-cardinality cells, especially
 * under the parallel loaders — this uses a set of independent {@link ConcurrentHashMap} shards
 * keyed by content, with a per-shard {@link ReferenceQueue} draining dead entries
 * opportunistically.
 * </p>
 *
 * <p>
 * <b>Why weak references are mandatory:</b> a strong-reference map would pin every distinct string
 * the application has ever loaded for the lifetime of the JVM. Over a long session that opens and
 * closes dozens of tables that is unbounded growth — the opposite of the desired "shared while
 * referenced, freed on close" behaviour that {@link String#intern()} already provides.
 * </p>
 *
 * <p>
 * Canonicalisation is a <b>provider-layer concern</b>: low-level parser libraries (e.g.
 * {@code net.cumba.sas-utils}, {@code net.cumba.parso}, {@code net.cumba.readstat}) return plain,
 * un-interned strings and must not depend on this class; the providers that wrap them are
 * responsible for routing string cells through here (via {@link CDT#tri(String)} /
 * {@link CDT#intern(String)}).
 * </p>
 *
 * <p>
 * All methods are thread-safe.
 * </p>
 */
public final class StringInterner
{

    /** The shared, process-global interner used by {@link CDT}. */
    private static final StringInterner GLOBAL = new StringInterner();

    private final Shard[] shards;

    private final int mask;

    /**
     * Create an interner with a shard count derived from the available processors (rounded up to a
     * power of two, minimum 4).
     */
    public StringInterner()
    {
        this(defaultShardCount());
    }


    /**
     * Create an interner with the given number of shards.
     *
     * @param aShardCount
     *            the desired shard count; rounded up to the next power of two, with a floor of 1.
     */
    public StringInterner(int aShardCount)
    {
        int count = Integer.highestOneBit(Math.max(1, aShardCount - 1)) << 1;
        this.shards = new Shard[count];
        for (int i = 0; i < count; i++)
        {
            shards[i] = new Shard();
        }
        this.mask = count - 1;
    }


    private static int defaultShardCount()
    {
        return Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    }


    /**
     * Canonicalise the given string against the process-global interner.
     *
     * @param aString
     *            the string to canonicalise; may be {@code null}.
     * @return {@code null} if {@code aString} is {@code null}; otherwise the canonical instance
     *         that is {@link String#equals(Object) equal} to {@code aString}. Repeated calls with
     *         equal-content arguments return the same ({@code ==}) instance while it stays
     *         referenced.
     */
    public static @Nullable String global(@Nullable String aString)
    {
        return GLOBAL.intern(aString);
    }


    /**
     * Canonicalise the given string against this interner instance.
     *
     * @param aString
     *            the string to canonicalise; may be {@code null}.
     * @return {@code null} if {@code aString} is {@code null}; otherwise the canonical instance.
     */
    public @Nullable String intern(@Nullable String aString)
    {
        if (aString == null)
        {
            return null;
        }
        int h = spread(aString.hashCode());
        return shards[h & mask].intern(aString, h);
    }


    /**
     * The current number of live canonical entries across all shards. Intended for tests and
     * diagnostics; the value is approximate under concurrent mutation and may include not-yet-
     * expunged dead entries.
     *
     * @return the approximate entry count.
     */
    public int size()
    {
        int total = 0;
        for (Shard shard : shards)
        {
            total += shard.map.size();
        }
        return total;
    }


    /** Mix the hash so the low bits used for shard selection are well distributed. */
    private static int spread(int aHash)
    {
        return aHash ^ (aHash >>> 16);
    }

    /**
     * One independent partition of the interner: a content-keyed map of weak elements plus a
     * reference queue used to expunge entries whose canonical string has been collected.
     */
    private static final class Shard
    {

        private final ConcurrentHashMap<WeakElement, WeakElement> map = new ConcurrentHashMap<>();

        private final ReferenceQueue<String> queue = new ReferenceQueue<>();

        String intern(String aString, int aHash)
        {
            expunge();
            WeakElement candidate = new WeakElement(aString, aHash, queue);
            while (true)
            {
                WeakElement existing = map.putIfAbsent(candidate, candidate);
                if (existing == null)
                {
                    // We won the race; aString is now the canonical instance.
                    return aString;
                }
                String canonical = existing.get();
                if (canonical != null)
                {
                    // A live canonical instance already exists; the candidate is discarded.
                    return canonical;
                }
                // The existing entry was cleared but not yet expunged. Drop it and retry so we do
                // not return a dead reference.
                map.remove(existing, existing);
            }
        }


        /** Remove map entries whose referent has been garbage-collected. */
        private void expunge()
        {
            Reference<? extends String> ref;
            while ((ref = queue.poll()) != null)
            {
                map.remove(ref);
            }
        }
    }


    /**
     * A {@link WeakReference} to a canonical string that also serves as its own content-based map
     * key. The hash is cached at construction so the element remains usable as a key (for removal)
     * even after its referent has been cleared.
     */
    private static final class WeakElement extends WeakReference<String>
    {

        private final int hash;

        WeakElement(String aReferent, int aHash, ReferenceQueue<String> aQueue)
        {
            super(aReferent, aQueue);
            this.hash = aHash;
        }


        @Override
        public int hashCode()
        {
            return hash;
        }


        @Override
        public boolean equals(Object aOther)
        {
            if (this == aOther)
            {
                return true;
            }
            if (!(aOther instanceof WeakElement other))
            {
                return false;
            }
            if (hash != other.hash)
            {
                return false;
            }
            String a = get();
            String b = other.get();
            // A cleared referent only matches its own instance (handled by the == check above);
            // distinct elements with a collected referent are treated as unequal.
            return a != null && a.equals(b);
        }
    }
}
