package net.cumba.web.api.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.NonNull;
import net.cumba.web.api.http.HttpRequest;
import net.cumba.web.api.http.HttpResponse;
import org.jspecify.annotations.Nullable;

/**
 * File-system based {@link ApiCache} implementation. Responses are stored as plain text files in a
 * configurable directory. Writes use an atomic temp-file-and-move pattern to prevent corruption.
 *
 * <p>
 * Cache file names are derived from the endpoint path: leading slashes are stripped, remaining
 * slashes become underscores, the result is URL-encoded, and a configurable file extension is
 * appended.
 * </p>
 *
 * <p>
 * HTTP response metadata (status code and headers) is stored in a companion {@code .meta} file
 * alongside the body file. When no {@code .meta} file exists (e.g., for pre-existing cache
 * entries), a default status of 200 and empty headers are assumed.
 * </p>
 */
public class FileApiCache implements ApiCache
{

    /**
     * The body handed to a validator on the streaming path, where by contract the validator has
     * declared it does not inspect the content. Zero-length, so it is immutable in practice and
     * safe to share.
     */
    private static final byte[] NO_CONTENT = new byte[0];

    private static final ObjectMapper META_MAPPER = new ObjectMapper();

    private static final TypeReference<CacheMeta> CACHE_META_TYPE = new TypeReference<>()
    {
    };

    private final Path cacheDir;

    private final String extension;

    private final @Nullable CacheValidator validator;

    /**
     * Returns the cache directory.
     *
     * @return the cache directory path.
     */
    protected Path cacheDir()
    {
        return cacheDir;
    }


    /**
     * Creates a file cache with the given directory and file extension, without a validator.
     *
     * @param aCacheDir
     *            the directory to store cache files in.
     * @param aExtension
     *            the file extension including the dot (e.g., ".json", ".xml").
     */
    public FileApiCache(@NonNull Path aCacheDir, @NonNull String aExtension)
    {
        this(aCacheDir, aExtension, null);
    }


    /**
     * Creates a file cache with the given directory, file extension, and optional validator.
     *
     * @param aCacheDir
     *            the directory to store cache files in.
     * @param aExtension
     *            the file extension including the dot (e.g., ".json", ".xml").
     * @param aValidator
     *            an optional {@link CacheValidator} consulted before serving cached entries. If
     *            {@code null}, all existing cache entries are considered valid.
     */
    public FileApiCache(@NonNull Path aCacheDir, @NonNull String aExtension,
            @Nullable CacheValidator aValidator)
    {
        this.cacheDir = aCacheDir;
        this.extension = aExtension;
        this.validator = aValidator;
    }

    // --- Request-aware API (overrides from ApiCache) ---


    @Override
    public Optional<CacheEntry> get(HttpRequest aRequest) throws IOException
    {
        String cacheKey = toCacheKey(aRequest);
        Optional<CacheEntry> entry = get(aRequest, cacheKey);
        if (entry.isEmpty())
        {
            String legacyKey = toLegacyCacheKey(aRequest);
            if (!legacyKey.equals(cacheKey))
            {
                entry = get(aRequest, legacyKey);
            }
        }
        return entry;
    }


    /**
     * Looks up one specific cache key on behalf of {@link #get(HttpRequest)}, validating the entry
     * against the originating request.
     *
     * @param aRequest
     *            the HTTP request being served.
     * @param aCacheKey
     *            the cache key to look up.
     * @return the cached entry, or empty if absent or invalidated.
     * @throws IOException
     *             in case of an I/O error reading the cache.
     */
    private Optional<CacheEntry> get(HttpRequest aRequest, String aCacheKey) throws IOException
    {
        Path cacheFile = cacheDir.resolve(toCacheFileName(aCacheKey));
        if (!Files.exists(cacheFile))
        {
            return Optional.empty();
        }

        // Read body and metadata first so the validator can inspect the entry
        Optional<byte[]> content = readCacheFile(cacheFile);
        if (content.isEmpty())
        {
            return Optional.empty();
        }

        CacheEntry entry = buildCacheEntry(cacheFile, content.get());

        // Validate with full request + entry context
        if (validator != null)
        {
            long timestamp = Files.getLastModifiedTime(cacheFile).toMillis();
            if (!validator.isValid(aRequest, entry, timestamp))
            {
                invalidate(aCacheKey);
                return Optional.empty();
            }
        }

        return Optional.of(entry);
    }


    @Override
    public Optional<HttpResponse> openStream(HttpRequest aRequest) throws IOException
    {
        // A content-inspecting validator cannot be answered from a stream: it wants the whole
        // body in memory, which is exactly the materialisation this path exists to avoid. Let
        // the caller fall back to get(HttpRequest). Deciding it HERE rather than in the caller is
        // deliberate - this class owns the validator, and a caller holding a custom ApiCache has
        // no way to see it.
        CacheValidator configured = validator;
        if (configured != null && configured.needsContent())
        {
            return Optional.empty();
        }
        String cacheKey = toCacheKey(aRequest);
        Optional<HttpResponse> streamed = openStream(aRequest, cacheKey);
        if (streamed.isEmpty())
        {
            String legacyKey = toLegacyCacheKey(aRequest);
            if (!legacyKey.equals(cacheKey))
            {
                streamed = openStream(aRequest, legacyKey);
            }
        }
        return streamed;
    }


    /**
     * Opens one specific cache key as a stream on behalf of {@link #openStream(HttpRequest)}.
     *
     * <p>
     * The entry is still validated - a non-content validator is consulted exactly as
     * {@link #get(HttpRequest)} consults it, and an entry it rejects is invalidated here too, so
     * the streaming path cannot silently outlive a TTL. The {@link CacheEntry} handed to the
     * validator carries the real status code and headers from the {@code .meta} sidecar and an
     * empty content array, which is sound precisely because {@link CacheValidator#needsContent()}
     * said the content is not consulted.
     * </p>
     *
     * @param aRequest
     *            the HTTP request being served.
     * @param aCacheKey
     *            the cache key to look up.
     * @return a streaming response, or empty if the entry is absent or was invalidated.
     * @throws IOException
     *             in case of an I/O error reading the cache.
     */
    private Optional<HttpResponse> openStream(HttpRequest aRequest, String aCacheKey)
        throws IOException
    {
        Path cacheFile = cacheDir.resolve(toCacheFileName(aCacheKey));
        if (!Files.exists(cacheFile))
        {
            return Optional.empty();
        }

        CacheMeta meta = readMeta(cacheFile);

        CacheValidator configured = validator;
        if (configured != null)
        {
            long timestamp = Files.getLastModifiedTime(cacheFile).toMillis();
            CacheEntry probe = new CacheEntry(meta.statusCode(), meta.headers(), NO_CONTENT);
            if (!configured.isValid(aRequest, probe, timestamp))
            {
                invalidate(aCacheKey);
                return Optional.empty();
            }
        }

        return Optional.of(new HttpResponse(meta.statusCode(), meta.headers(),
                openCacheFileStream(cacheFile)));
    }


    /**
     * Opens a cache file as a stream of its <b>decoded</b> bytes - the same bytes
     * {@link #readCacheFile(Path)} would have turned into a String. Subclasses that transform the
     * stored form (compression, encryption) must override this as well as
     * {@link #readCacheFile(Path)}, or the streaming path would serve the raw stored bytes.
     *
     * @param aCacheFile
     *            the cache file path.
     * @return an open stream over the file's decoded content; the caller closes it.
     * @throws IOException
     *             in case of an I/O error.
     */
    protected InputStream openCacheFileStream(Path aCacheFile) throws IOException
    {
        return Files.newInputStream(aCacheFile);
    }

    // --- Path-based API ---


    @Override
    public Optional<byte[]> read(String aPath) throws IOException
    {
        Path cacheFile = cacheDir.resolve(toCacheFileName(aPath));
        if (Files.exists(cacheFile))
        {
            if (validator != null)
            {
                long timestamp = Files.getLastModifiedTime(cacheFile).toMillis();
                // Path-based read: construct a minimal entry for validation
                Optional<byte[]> content = readCacheFile(cacheFile);
                if (content.isEmpty())
                {
                    return Optional.empty();
                }
                CacheEntry entry = buildCacheEntry(cacheFile, content.get());
                if (!validator.isValid(null, entry, timestamp))
                {
                    invalidate(aPath);
                    return Optional.empty();
                }
                return content;
            }
            return readCacheFile(cacheFile);
        }
        return Optional.empty();
    }


    @Override
    public Optional<CacheEntry> readEntry(String aPath) throws IOException
    {
        Path cacheFile = cacheDir.resolve(toCacheFileName(aPath));
        if (!Files.exists(cacheFile))
        {
            return Optional.empty();
        }

        Optional<byte[]> content = readCacheFile(cacheFile);
        if (content.isEmpty())
        {
            return Optional.empty();
        }

        CacheEntry entry = buildCacheEntry(cacheFile, content.get());

        if (validator != null)
        {
            long timestamp = Files.getLastModifiedTime(cacheFile).toMillis();
            if (!validator.isValid(null, entry, timestamp))
            {
                invalidate(aPath);
                return Optional.empty();
            }
        }

        return Optional.of(entry);
    }


    @SuppressWarnings("PMD.EmptyCatchBlock")
    @Override
    public void writeEntry(String aPath, CacheEntry aEntry)
    {
        write(aPath, aEntry.content());
        if (!writeMetaFile(aPath, aEntry))
        {
            // A body whose sidecar never landed is worse than no entry at all. readMeta falls
            // back to status 200 and NO headers, so that body would come back under a status and
            // a header set that were never its own - a cached 404 read as a 200, and a
            // CacheValidator keyed on the response headers judging a stale body fresh because it
            // sees none. Both writes stay non-fatal by design; what must not survive is the
            // half-written entry.
            try
            {
                invalidate(aPath);
            }
            catch (IOException _)
            {
                // Best effort. A cache write never throws, and there is nothing further to try.
            }
        }
    }


    /**
     * Reads the content of a cache file as its <b>decoded bytes</b> — decoded in the sense of the
     * storage form only (a subclass may decompress or decrypt here), never in the sense of a
     * character set. The bytes are handed on to callers untouched.
     *
     * @param aCacheFile
     *            the cache file path.
     * @return the file content as raw bytes.
     * @throws IOException
     *             in case of an I/O error.
     */
    protected Optional<byte[]> readCacheFile(Path aCacheFile) throws IOException
    {
        return Optional.of(Files.readAllBytes(aCacheFile));
    }


    @SuppressWarnings("PMD.EmptyCatchBlock")
    @Override
    public void write(String aPath, byte[] aContent)
    {
        Path tmp = null;
        try
        {
            Path cacheFile = cacheDir.resolve(toCacheFileName(aPath));
            Files.createDirectories(cacheFile.getParent());

            tmp = Files.createTempFile(cacheDir, "cache", ".tmp");
            Files.write(tmp, aContent);
            Files.move(tmp, cacheFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            tmp = null; // move succeeded, no cleanup needed
        }
        catch (IOException _)
        {
            // Cache write failures are non-fatal
        }
        finally
        {
            if (tmp != null)
            {
                try
                {
                    Files.deleteIfExists(tmp);
                }
                catch (IOException _)
                {
                    // Best-effort cleanup
                }
            }
        }
    }


    @Override
    public boolean invalidate(String aPath) throws IOException
    {
        Path cacheFile = cacheDir.resolve(toCacheFileName(aPath));
        Path metaFile = metaFilePath(cacheFile);
        // Body first, sidecar second. The other order leaves the body standing with its metadata
        // already gone whenever the second delete fails, and readMeta then serves that body under
        // its status-200 / no-headers fallback - a cached 404 coming back as a 200. This order can
        // only ever leave an orphaned sidecar, which nothing reads once its body is gone.
        boolean removed = Files.deleteIfExists(cacheFile);
        Files.deleteIfExists(metaFile);
        return removed;
    }


    @Override
    public OptionalLong cacheTimestamp(String aPath) throws IOException
    {
        Path cacheFile = cacheDir.resolve(toCacheFileName(aPath));
        if (Files.exists(cacheFile))
        {
            return OptionalLong.of(Files.getLastModifiedTime(cacheFile).toMillis());
        }
        return OptionalLong.empty();
    }


    /**
     * Returns the configured {@link CacheValidator}, or {@code null} if none is set.
     *
     * @return the cache validator, or {@code null}.
     */
    protected @Nullable CacheValidator validator()
    {
        return validator;
    }


    /**
     * Converts an endpoint path to a safe cache file name.
     *
     * <p>
     * The encoding lives in {@link ApiCache#encodeKeyForFileName(String)} — one function, used both
     * here and by the length bound that decides when a key has to be shortened, so the two cannot
     * disagree about what a key costs on disk. See it for why the mapping is injective and why it
     * survives a case-insensitive file system (Q14, 2026-09-11).
     * </p>
     *
     * @param aEndpoint
     *            the endpoint path.
     * @return the cache file name.
     */
    public String toCacheFileName(String aEndpoint)
    {
        return ApiCache.encodeKeyForFileName(aEndpoint) + extension;
    }

    // --- Internal ---


    /**
     * Builds a {@link CacheEntry} from a cache body file and its companion metadata file.
     */
    private CacheEntry buildCacheEntry(Path aCacheFile, byte[] aContent)
    {
        CacheMeta meta = readMeta(aCacheFile);
        return new CacheEntry(meta.statusCode(), meta.headers(), aContent);
    }


    /**
     * Reads the {@code .meta} sidecar of a cache body file, falling back to status 200 and empty
     * headers when it is absent or corrupt. The returned record's headers are never {@code null}.
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private CacheMeta readMeta(Path aCacheFile)
    {
        Path metaFile = metaFilePath(aCacheFile);
        if (Files.exists(metaFile))
        {
            try
            {
                CacheMeta meta = META_MAPPER.readValue(metaFile.toFile(), CACHE_META_TYPE);
                Map<String, List<String>> headers = meta.headers() != null ? meta.headers()
                        : Collections.emptyMap();
                return new CacheMeta(meta.statusCode(), headers);
            }
            catch (IOException _)
            {
                // Corrupted meta file — use defaults
            }
        }
        return new CacheMeta(200, Collections.emptyMap());
    }


    /**
     * Returns the path of the metadata sidecar file for the given cache body file.
     */
    protected Path metaFilePath(Path aCacheFile)
    {
        return aCacheFile.resolveSibling(aCacheFile.getFileName() + ".meta");
    }


    /**
     * Writes the metadata sidecar file for a cache entry.
     *
     * @return {@code true} when the sidecar is on disk, {@code false} when the write failed. The
     *         failure itself stays non-fatal; the caller uses the answer to decide whether the body
     *         it has just written may be left standing.
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private boolean writeMetaFile(String aPath, CacheEntry aEntry)
    {
        Path tmp = null;
        try
        {
            Path cacheFile = cacheDir.resolve(toCacheFileName(aPath));
            Path metaFile = metaFilePath(cacheFile);
            Files.createDirectories(metaFile.getParent());

            CacheMeta meta = new CacheMeta(aEntry.statusCode(), aEntry.headers());
            byte[] metaBytes = META_MAPPER.writeValueAsBytes(meta);

            tmp = Files.createTempFile(cacheDir, "meta", ".tmp");
            Files.write(tmp, metaBytes);
            Files.move(tmp, metaFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            tmp = null;
            return true;
        }
        catch (IOException _)
        {
            // Meta write failures are non-fatal
            return false;
        }
        finally
        {
            if (tmp != null)
            {
                try
                {
                    Files.deleteIfExists(tmp);
                }
                catch (IOException _)
                {
                    // Best-effort cleanup
                }
            }
        }
    }

    /**
     * Internal record for JSON serialization of cache metadata (status code and headers).
     */
    record CacheMeta(int statusCode, Map<String, List<String>> headers)
    {
    }
}
