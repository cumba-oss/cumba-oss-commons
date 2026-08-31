package net.cumba.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ErrorModeTest
{

    @Test
    void nullAndBlankDefaultToWarn()
    {
        assertEquals(ErrorMode.WARN, ErrorMode.fromProperty(null));
        assertEquals(ErrorMode.WARN, ErrorMode.fromProperty("   "));
    }


    @Test
    void parsesCaseInsensitively()
    {
        assertEquals(ErrorMode.WARN, ErrorMode.fromProperty("WARN"));
        assertEquals(ErrorMode.WARN, ErrorMode.fromProperty(" warn "));
        assertEquals(ErrorMode.ERROR, ErrorMode.fromProperty("Error"));
    }


    @Test
    void rejectsUnknownValue()
    {
        BootstrapException ex = assertThrows(BootstrapException.class,
                () -> ErrorMode.fromProperty("loud"));
        assertTrue(ex.getMessage().contains("classpath.error.mode"));
        assertTrue(ex.getMessage().contains("loud"));
    }
}
