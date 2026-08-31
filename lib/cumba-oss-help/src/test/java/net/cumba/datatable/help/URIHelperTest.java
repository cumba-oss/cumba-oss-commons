package net.cumba.datatable.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
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


    @Test
    void testReplaceFragmentOnHierarchicalHttpUri()
    {
        URI uri = URI.create("http://example.com/d/data.cdt");
        URI updated = URIHelper.replaceFragment(uri, "AE");
        assertEquals("AE", updated.getFragment());
        assertEquals("http://example.com/d/data.cdt#AE", updated.toString());
    }


    @Test
    void testReplaceFragmentOnOpaqueJarUri()
    {
        // Opaque URI: getPath() is null and the content lives in the scheme-specific part.
        URI uri = URI.create("jar:file:/app/lib.jar!/x.cdt");
        URI updated = URIHelper.replaceFragment(uri, "DM");
        assertTrue(updated.isOpaque());
        assertEquals("DM", updated.getFragment());
        assertEquals("file:/app/lib.jar!/x.cdt", updated.getSchemeSpecificPart());
        assertEquals("jar:file:/app/lib.jar!/x.cdt#DM", updated.toString());
    }


    @Test
    void testReplaceFragmentClearsFragmentOnOpaqueJarUri()
    {
        URI uri = URI.create("jar:file:/app/lib.jar!/x.cdt#old");
        URI updated = URIHelper.replaceFragment(uri, null);
        assertNull(updated.getFragment());
        assertEquals("file:/app/lib.jar!/x.cdt", updated.getSchemeSpecificPart());
    }


    @Test
    void testReplaceFragmentOnOpaqueMailtoUri()
    {
        URI uri = URI.create("mailto:user@example.com");
        URI updated = URIHelper.replaceFragment(uri, "frag");
        assertTrue(updated.isOpaque());
        assertEquals("frag", updated.getFragment());
        assertEquals("user@example.com", updated.getSchemeSpecificPart());
    }
}
