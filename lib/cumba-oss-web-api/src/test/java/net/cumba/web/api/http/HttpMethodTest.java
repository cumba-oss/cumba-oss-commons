package net.cumba.web.api.http;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HttpMethodTest
{

    @Test
    void allMethodsExist()
    {
        HttpMethod[] methods = HttpMethod.values();
        assertEquals(7, methods.length);
    }


    @Test
    void valueOfGet()
    {
        assertEquals(HttpMethod.GET, HttpMethod.valueOf("GET"));
    }


    @Test
    void valueOfPost()
    {
        assertEquals(HttpMethod.POST, HttpMethod.valueOf("POST"));
    }


    @Test
    void valueOfPut()
    {
        assertEquals(HttpMethod.PUT, HttpMethod.valueOf("PUT"));
    }


    @Test
    void valueOfPatch()
    {
        assertEquals(HttpMethod.PATCH, HttpMethod.valueOf("PATCH"));
    }


    @Test
    void valueOfDelete()
    {
        assertEquals(HttpMethod.DELETE, HttpMethod.valueOf("DELETE"));
    }


    @Test
    void valueOfHead()
    {
        assertEquals(HttpMethod.HEAD, HttpMethod.valueOf("HEAD"));
    }


    @Test
    void valueOfOptions()
    {
        assertEquals(HttpMethod.OPTIONS, HttpMethod.valueOf("OPTIONS"));
    }


    @Test
    void nameReturnsUpperCase()
    {
        assertEquals("GET", HttpMethod.GET.name());
        assertEquals("POST", HttpMethod.POST.name());
    }
}
