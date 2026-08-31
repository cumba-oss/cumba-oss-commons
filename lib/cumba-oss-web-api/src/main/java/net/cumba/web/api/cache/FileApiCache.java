package net.cumba.web.api.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        Optional<String> content = readCacheFile(cacheFile);
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

    // --- Path-based API ---


    @Override
    public Optional<String> read(String aPath) throws IOException
    {
        Path cacheFile = cacheDir.resolve(toCacheFileName(aPath));
        if (Files.exists(cacheFile))
        {
            if (validator != null)
            {
                long timestamp = Files.getLastModifiedTime(cacheFile).toMillis();
                // Path-based read: construct a minimal entry for validation
                Optional<String> content = readCacheFile(cacheFile);
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

        Optional<String> content = readCacheFile(cacheFile);
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


    @Override
    public void writeEntry(String aPath, CacheEntry aEntry)
    {
        write(aPath, aEntry.content());
        writeMetaFile(aPath, aEntry);
    }


    /**
     * Reads the content of a cache file. Subclasses may override this to apply decompression or
     * other transformations.
     *
     * @param aCacheFile
     *            the cache file path.
     * @return the file content.
     * @throws IOException
     *             in case of an I/O error.
     */
    protected Optional<String> readCacheFile(Path aCacheFile) throws IOException
    {
        return Optional.of(Files.readString(aCacheFile, StandardCharsets.UTF_8));
    }


    @SuppressWarnings("PMD.EmptyCatchBlock")
    @Override
    public void write(String aPath, String aContent)
    {
        Path tmp = null;
        try
        {
            Path cacheFile = cacheDir.resolve(toCacheFileName(aPath));
            Files.createDirectories(cacheFile.getParent());

            tmp = Files.createTempFile(cacheDir, "cache", ".tmp");
            Files.writeString(tmp, aContent, StandardCharsets.UTF_8);
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
        // Also remove the metadata sidecar
        Path metaFile = metaFilePath(cacheFile);
        Files.deleteIfExists(metaFile);
        return Files.deleteIfExists(cacheFile);
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
     * @param aEndpoint
     *            the endpoint path.
     * @return the cache file name.
     */
    public String toCacheFileName(String aEndpoint)
    {
        String name = aEndpoint;
        if (name.startsWith("/"))
        {
            name = name.substring(1);
        }
        name = name.replace('/', '_');
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + extension;
    }

    // --- Internal ---


    /**
     * Builds a {@link CacheEntry} from a cache body file and its companion metadata file.
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private CacheEntry buildCacheEntry(Path aCacheFile, String aContent)
    {
        Path metaFile = metaFilePath(aCacheFile);
        int statusCode = 200;
        Map<String, List<String>> headers = Collections.emptyMap();
        if (Files.exists(metaFile))
        {
            try
            {
                CacheMeta meta = META_MAPPER.readValue(metaFile.toFile(), CACHE_META_TYPE);
                statusCode = meta.statusCode();
                headers = meta.headers() != null ? meta.headers() : Collections.emptyMap();
            }
            catch (IOException _)
            {
                // Corrupted meta file — use defaults
            }
        }
        return new CacheEntry(statusCode, headers, aContent);
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
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private void writeMetaFile(String aPath, CacheEntry aEntry)
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
        }
        catch (IOException _)
        {
            // Meta write failures are non-fatal
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
