package net.cumba.datatable.help;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;

import org.jspecify.annotations.Nullable;

public class URIHelper
{

    private URIHelper()
    {
        throw new UnsupportedOperationException("utility class");
    }


    public static @Nullable String getFileName(@Nullable URI aUri)
    {
        if (aUri == null)
        {
            return null;
        }
        // Opaque URIs (e.g. jar:file:/x.jar!/y.cdt, mailto:…) carry their content in the
        // scheme-specific part, not in the path — getPath() is null for them — so, exactly as
        // replaceFragment does, read getSchemeSpecificPart() instead. That accessor already
        // excludes the fragment, so "jar:file:/x.jar!/y.cdt#TABLE1" behaves like the
        // fragment-free URI here too.
        String path = aUri.isOpaque() ? aUri.getSchemeSpecificPart() : aUri.getPath();
        return CDT.getAfterLast(path, '/');
    }


    public static @Nullable File asFile(@Nullable URI aUri)
    {
        if (aUri == null)
        {
            return null;
        }
        if (!"file".equalsIgnoreCase(aUri.getScheme()))
        {
            return null;
        }
        if (!CDT.isEmptyOrNull(aUri.getFragment()))
        {
            return new File(replaceFragment(aUri, null));
        }
        return new File(aUri);
    }


    /**
     * Opens an input stream on {@code aUri} <b>without</b> the JVM-wide jar-file cache — the way
     * every provider opens user data.
     * <p>
     * {@code aUri.toURL().openStream()} on a {@code jar:} URL (a file inside a zip, e.g.
     * {@code jar:file:/study.zip!/dm.json}) goes through {@code JarURLConnection}, which by default
     * keeps the archive open in a cache for the life of the JVM, after the stream is closed.
     * Measured on JDK 25: the archive keeps an open handle, so on Windows it stays locked until the
     * application exits, and on every OS a re-read after the archive is replaced returns the OLD
     * content. With caching off, closing the stream closes the archive. For {@code file:} this is
     * the same stream {@code openStream()} returns; for {@code http(s):} the body is the same, but
     * an uncached request also carries {@code Cache-Control: no-cache} / {@code Pragma: no-cache}
     * and bypasses any installed {@code ResponseCache} (none is installed in this stack). A
     * {@code #fragment} is carried into the URL's ref and ignored by the connection, exactly as
     * before.
     * </p>
     * <p>
     * ⚠ Cost: uncached, each open of a {@code jar:file:} URL re-reads the archive's central
     * directory. The app's own classpath resources do not come through here and keep their cache.
     * </p>
     *
     * @param aUri
     *            the location to read.
     * @return a stream the caller must close.
     * @throws IOException
     *             if the location cannot be opened.
     */
    public static InputStream openStream(URI aUri) throws IOException
    {
        URLConnection connection = aUri.toURL().openConnection();
        connection.setUseCaches(false);
        return connection.getInputStream();
    }


    public static URI replaceFragment(URI uri, @Nullable String newFragment)
    {
        try
        {
            if (uri.isOpaque())
            {
                // Opaque URIs (e.g. jar:file:/x.jar!/y.cdt, mailto:…) carry their content in the
                // scheme-specific part, not in authority/path/query — getPath() is null — so they
                // require the opaque (scheme, ssp, fragment) constructor.
                return new URI(uri.getScheme(), uri.getSchemeSpecificPart(), newFragment);
            }
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(),
                    newFragment);
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException("Invalid fragment: " + newFragment, e);
        }
    }
}
