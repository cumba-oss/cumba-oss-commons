package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class PlatformAndGrantTest
{

    @Test
    void osNamesMapToThePlatforms()
    {
        assertEquals(Platform.WINDOWS, Platform.fromOsName("Windows 11"));
        assertEquals(Platform.MACOS, Platform.fromOsName("Mac OS X"));
        assertEquals(Platform.MACOS, Platform.fromOsName("Darwin"));
        assertEquals(Platform.LINUX, Platform.fromOsName("Linux"));
        assertEquals(Platform.OTHER, Platform.fromOsName("FreeBSD"));
        assertEquals(Platform.OTHER, Platform.fromOsName(""));
        assertEquals(Platform.fromOsName(System.getProperty("os.name", "")), Platform.current());
    }


    @Test
    void onlyWindowsGetsTheNativeGrant() throws Exception
    {
        assertSame(ForegroundGrant.NONE, ForegroundGrant.forPlatform(Platform.LINUX));
        assertSame(ForegroundGrant.NONE, ForegroundGrant.forPlatform(Platform.MACOS));
        assertSame(ForegroundGrant.NONE, ForegroundGrant.forPlatform(Platform.OTHER));
        assertEquals(new ForegroundGrant.Result(1, 0), ForegroundGrant.NONE.allow(4711),
                "the no-op grant reads as granted, no error");
        ForegroundGrant windows = ForegroundGrant.forPlatform(Platform.WINDOWS);
        assertNotSame(ForegroundGrant.NONE, windows);
        // the binding is looked up on the first call, not on selection: this build host has no
        // user32.dll, so the call throws - which the client logs and survives
        assumeTrue(Platform.current() != Platform.WINDOWS);
        assertThrows(ForegroundGrant.NativeCallException.class, () -> windows.allow(1));
    }
}
