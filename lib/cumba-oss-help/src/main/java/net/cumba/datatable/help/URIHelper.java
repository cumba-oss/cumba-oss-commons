package net.cumba.datatable.help;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

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
        String path = aUri.getPath();
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
