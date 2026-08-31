package net.cumba.web.api.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * File-system based {@link ApiCache} implementation that stores cache files gzip-compressed. This
 * reduces disk usage for large API responses at the cost of a small CPU overhead for
 * compression/decompression.
 *
 * <p>
 * Uses the same file naming convention as {@link FileApiCache} but appends {@code .gz} to the file
 * extension (e.g., {@code .json.gz}).
 * </p>
 *
 * <p>
 * Metadata sidecar files ({@code .meta}) are stored uncompressed since they are small.
 * </p>
 */
public class GzipFileApiCache extends FileApiCache
{

    /**
     * Creates a gzip-compressed file cache with the given directory and file extension.
     *
     * @param aCacheDir
     *            the directory to store cache files in.
     * @param aExtension
     *            the base file extension including the dot (e.g., ".json", ".xml"). The actual
     *            files will have {@code .gz} appended (e.g., ".json.gz").
     */
    public GzipFileApiCache(@NonNull Path aCacheDir, @NonNull String aExtension)
    {
        super(aCacheDir, aExtension + ".gz");
    }


    /**
     * Creates a gzip-compressed file cache with the given directory, file extension, and optional
     * validator.
     *
     * @param aCacheDir
     *            the directory to store cache files in.
     * @param aExtension
     *            the base file extension including the dot (e.g., ".json", ".xml"). The actual
     *            files will have {@code .gz} appended (e.g., ".json.gz").
     * @param aValidator
     *            an optional {@link CacheValidator} consulted before serving cached entries.
     */
    public GzipFileApiCache(@NonNull Path aCacheDir, @NonNull String aExtension,
            @Nullable CacheValidator aValidator)
    {
        super(aCacheDir, aExtension + ".gz", aValidator);
    }


    @Override
    protected Optional<String> readCacheFile(Path aCacheFile) throws IOException
    {
        try (InputStream fis = Files.newInputStream(aCacheFile);
                GZIPInputStream gis = new GZIPInputStream(fis))
        {
            return Optional.of(new String(gis.readAllBytes(), StandardCharsets.UTF_8));
        }
    }


    @SuppressWarnings("PMD.EmptyCatchBlock")
    @Override
    public void write(String aPath, String aContent)
    {
        Path tmp = null;
        try
        {
            Path cacheFile = cacheDir().resolve(toCacheFileName(aPath));
            Files.createDirectories(cacheFile.getParent());

            tmp = Files.createTempFile(cacheDir(), "cache", ".tmp.gz");
            try (OutputStream fos = Files.newOutputStream(tmp);
                    GZIPOutputStream gos = new GZIPOutputStream(fos))
            {
                gos.write(aContent.getBytes(StandardCharsets.UTF_8));
            }
            Files.move(tmp, cacheFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            tmp = null;
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
}
