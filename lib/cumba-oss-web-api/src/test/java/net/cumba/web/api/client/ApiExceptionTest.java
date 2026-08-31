package net.cumba.web.api.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import net.cumba.web.api.ApiException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApiExceptionTest
{

    @Test
    void extendsIOException()
    {
        ApiException ex = new ApiException(404, "Not Found");
        assertInstanceOf(IOException.class, ex);
    }

    // --- Status code constructor ---


    @Test
    void statusCodeConstructorStoresCode()
    {
        ApiException ex = new ApiException(500, "Internal Server Error");
        assertEquals(500, ex.statusCode());
    }


    @Test
    void statusCodeConstructorStoresBody()
    {
        ApiException ex = new ApiException(400, "Bad Request");
        assertEquals("Bad Request", ex.responseBody());
    }


    @Test
    void statusCodeConstructorFormatsMessage()
    {
        ApiException ex = new ApiException(404, "Not Found");
        assertEquals("HTTP 404: Not Found", ex.getMessage());
    }


    @Test
    void statusCodeConstructorWithNullBody()
    {
        ApiException ex = new ApiException(500, null);
        assertEquals("HTTP 500", ex.getMessage());
        assertNull(ex.responseBody());
    }


    @Test
    void statusCodeConstructorTruncatesLongBody()
    {
        String longBody = "x".repeat(300);
        ApiException ex = new ApiException(400, longBody);
        assertTrue(ex.getMessage().length() < 300);
        assertTrue(ex.getMessage().endsWith("..."));
        // responseBody is not truncated
        assertEquals(300, ex.responseBody().length());
    }

    // --- Cause constructor ---


    @Test
    void causeConstructorStoresMessageAndCause()
    {
        IOException cause = new IOException("connection refused");
        ApiException ex = new ApiException("request failed", cause);
        assertEquals("request failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }


    @Test
    void causeConstructorDefaultsStatusCodeToMinusOne()
    {
        ApiException ex = new ApiException("error", new IOException());
        assertEquals(-1, ex.statusCode());
    }


    @Test
    void causeConstructorDefaultsResponseBodyToNull()
    {
        ApiException ex = new ApiException("error", new IOException());
        assertNull(ex.responseBody());
    }

    // --- isClientError / isServerError ---

    @Nested
    class ErrorClassification
    {

        @Test
        void isClientErrorTrueFor400()
        {
            assertTrue(new ApiException(400, null).isClientError());
        }


        @Test
        void isClientErrorTrueFor499()
        {
            assertTrue(new ApiException(499, null).isClientError());
        }


        @Test
        void isClientErrorFalseFor399()
        {
            assertFalse(new ApiException(399, null).isClientError());
        }


        @Test
        void isClientErrorFalseFor500()
        {
            assertFalse(new ApiException(500, null).isClientError());
        }


        @Test
        void isServerErrorTrueFor500()
        {
            assertTrue(new ApiException(500, null).isServerError());
        }


        @Test
        void isServerErrorTrueFor599()
        {
            assertTrue(new ApiException(599, null).isServerError());
        }


        @Test
        void isServerErrorFalseFor499()
        {
            assertFalse(new ApiException(499, null).isServerError());
        }


        @Test
        void isServerErrorFalseFor600()
        {
            assertFalse(new ApiException(600, null).isServerError());
        }


        @Test
        void isNeitherClientNorServerErrorFor200()
        {
            ApiException ex = new ApiException(200, null);
            assertFalse(ex.isClientError());
            assertFalse(ex.isServerError());
        }


        @Test
        void causeConstructorIsNeitherClientNorServerError()
        {
            ApiException ex = new ApiException("err", new IOException());
            assertFalse(ex.isClientError());
            assertFalse(ex.isServerError());
        }
    }
}
