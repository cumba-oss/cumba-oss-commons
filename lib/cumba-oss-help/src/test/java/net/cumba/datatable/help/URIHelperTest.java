package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import org.junit.jupiter.api.Test;

class URIHelperTest
{

    @Test
    void testGetFileName()
    {
        URI uri = URI.create("file:///path/to/data.csv");
        assertEquals("data.csv", URIHelper.getFileName(uri));
    }


    @Test
    void testGetFileNameNull()
    {
        assertNull(URIHelper.getFileName(null));
    }


    @Test
    void testGetFileNameHttpUri()
    {
        URI uri = URI.create("http://example.com/api/report.xlsx");
        assertEquals("report.xlsx", URIHelper.getFileName(uri));
    }


    @Test
    void testGetFileNameNoPath()
    {
        URI uri = URI.create("file:///");
        assertEquals("", URIHelper.getFileName(uri));
    }


    @Test
    void testAsFileNull()
    {
        assertNull(URIHelper.asFile(null));
    }


    @Test
    void testAsFileNonFileScheme()
    {
        URI uri = URI.create("http://example.com/file.csv");
        assertNull(URIHelper.asFile(uri));
    }


    @Test
    void testAsFilePlain()
    {
        URI uri = URI.create("file:///tmp/data.csv");
        File file = URIHelper.asFile(uri);
        assertNotNull(file);
        assertEquals("data.csv", file.getName());
    }


    @Test
    void testAsFileWithFragmentDropsFragment()
    {
        URI uri = URI.create("file:///tmp/data.csv#section");
        File file = URIHelper.asFile(uri);
        assertNotNull(file);
        assertEquals("data.csv", file.getName());
    }


    @Test
    void testReplaceFragmentSetsNewFragment()
    {
        URI uri = URI.create("file:///tmp/data.csv#old");
        URI updated = URIHelper.replaceFragment(uri, "new");
        assertEquals("new", updated.getFragment());
        assertEquals("/tmp/data.csv", updated.getPath());
    }


    @Test
    void testReplaceFragmentNullClearsFragment()
    {
        URI uri = URI.create("file:///tmp/data.csv#old");
        URI updated = URIHelper.replaceFragment(uri, null);
        assertNull(updated.getFragment());
    }

    // ===== opaque URIs =====
    //
    // An opaque URI (jar:, mailto:, …) keeps its content in the scheme-specific part and reports a
    // null getPath(). Rebuilding one through the hierarchical (scheme, authority, path, query,
    // fragment) constructor silently loses that content — which is what these tests pin, because
    // jar: URIs are how CdtTableProvider / CdtLibraryProvider address entries inside an archive.


    @Test
    void testReplaceFragmentOnJarUriAddsFragment()
    {
        URI uri = URI.create("jar:file:/tmp/x.jar!/y.cdt");
        URI updated = URIHelper.replaceFragment(uri, "TABLE1");

        assertTrue(updated.isOpaque(), "a jar: URI must stay opaque");
        assertEquals("jar", updated.getScheme());
        assertEquals("file:/tmp/x.jar!/y.cdt", updated.getSchemeSpecificPart(),
                "the scheme-specific part must survive the rebuild");
        assertEquals("TABLE1", updated.getFragment());
        assertEquals("jar:file:/tmp/x.jar!/y.cdt#TABLE1", updated.toString());
    }


    @Test
    void testReplaceFragmentOnJarUriReplacesFragment()
    {
        URI uri = URI.create("jar:file:/tmp/x.jar!/y.cdt#OLD");
        URI updated = URIHelper.replaceFragment(uri, "NEW");

        assertEquals("NEW", updated.getFragment());
        assertEquals("file:/tmp/x.jar!/y.cdt", updated.getSchemeSpecificPart());
    }


    @Test
    void testReplaceFragmentOnJarUriClearsFragment()
    {
        URI uri = URI.create("jar:file:/tmp/x.jar!/y.cdt#OLD");
        URI updated = URIHelper.replaceFragment(uri, null);

        assertNull(updated.getFragment());
        assertEquals("jar:file:/tmp/x.jar!/y.cdt", updated.toString());
    }


    @Test
    void testReplaceFragmentOnMailtoUri()
    {
        URI uri = URI.create("mailto:a@b.c");
        URI updated = URIHelper.replaceFragment(uri, "note");

        assertTrue(updated.isOpaque());
        assertEquals("a@b.c", updated.getSchemeSpecificPart());
        assertEquals("note", updated.getFragment());
    }


    /** Control case: the hierarchical path must be unchanged by the opaque branch. */
    @Test
    void testReplaceFragmentOnHierarchicalUriKeepsAuthorityAndQuery()
    {
        URI uri = URI.create("http://example.com/api/report.xlsx?v=2#old");
        URI updated = URIHelper.replaceFragment(uri, "new");

        assertFalse(updated.isOpaque());
        assertEquals("example.com", updated.getAuthority());
        assertEquals("/api/report.xlsx", updated.getPath());
        assertEquals("v=2", updated.getQuery());
        assertEquals("new", updated.getFragment());
    }

    // ===== getFileName on opaque URIs =====
    //
    // Q11 (owner ruling, 2026-09-11): getFileName(jar:file:/study/x.jar!/dm.cdt) must return
    // "dm.cdt" rather than throw NPE. getPath() is null for an opaque URI, so the fix reads
    // getSchemeSpecificPart() instead - exactly the accessor replaceFragment already uses to
    // rebuild an opaque URI - and reuses the same CDT.getAfterLast(path, '/') split that the
    // hierarchical branch already applied. This is the shape LocalCacheSupport.lookupMemberFindings
    // hits for every library member built via URIHelper.replaceFragment(aUri, name) (Cdt/Xpt/Excel/
    // Rds providers all do this) when the library file itself was opened through an opaque URI.


    @Test
    void testGetFileNameOnJarUriReturnsArchiveEntryName()
    {
        URI uri = URI.create("jar:file:/study/x.jar!/dm.cdt");
        assertEquals("dm.cdt", URIHelper.getFileName(uri));
    }


    @Test
    void testGetFileNameOnJarUriWithFragmentDropsFragment()
    {
        // Fragments occur on this exact path: CdtLibraryProvider / XptLibraryProvider / etc. all
        // build member URIs as replaceFragment(fileUri, memberName). getSchemeSpecificPart()
        // never includes the fragment, so this must behave identically to the no-fragment case.
        URI uri = URI.create("jar:file:/a.jar!/dm.cdt#DM");
        assertEquals("dm.cdt", URIHelper.getFileName(uri));
    }


    @Test
    void testGetFileNameOnNestedJarUri()
    {
        URI uri = URI.create("jar:file:/a.jar!/b.jar!/c.cdt");
        assertEquals("c.cdt", URIHelper.getFileName(uri));
    }


    @Test
    void testGetFileNameOnJarUriEndingInSeparator()
    {
        // No entry name after "!/" - mirrors the existing hierarchical "file:///" -> "" case.
        URI uri = URI.create("jar:file:/a.jar!/");
        assertEquals("", URIHelper.getFileName(uri));
    }


    @Test
    void testGetFileNameOnJarUriWithTrailingSlashEntry()
    {
        URI uri = URI.create("jar:file:/a.jar!/dir/");
        assertEquals("", URIHelper.getFileName(uri));
    }


    @Test
    void testGetFileNameOnOpaqueUriWithoutSeparator()
    {
        // No '/' anywhere in the scheme-specific part: falls back to CDT.getAfterLast's
        // "excerpt not found -> return the whole string" rule, same as it already does for a
        // slash-free hierarchical path.
        URI uri = URI.create("mailto:x@y.z");
        assertEquals("x@y.z", URIHelper.getFileName(uri));
    }
}
