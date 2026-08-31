package net.cumba.web.api.http;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JdkHttpTransportTest
{

    @Test
    void defaultConstructorCreatesInstance()
    {
        JdkHttpTransport transport = new JdkHttpTransport();
        assertNotNull(transport);
    }


    @Test
    void customClientConstructor()
    {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkHttpTransport transport = new JdkHttpTransport(client);
        assertNotNull(transport);
    }


    @Test
    void implementsHttpTransport()
    {
        JdkHttpTransport transport = new JdkHttpTransport();
        assertInstanceOf(HttpTransport.class, transport);
    }


    @Test
    void defaultRequestTimeoutIsSixtySeconds()
    {
        assertEquals(Duration.ofSeconds(60), JdkHttpTransport.DEFAULT_REQUEST_TIMEOUT);
    }


    @Test
    void customRequestTimeoutAccepted()
    {
        HttpClient client = HttpClient.newHttpClient();
        // Should not throw; the timeout is honoured at send() time, not at construction.
        JdkHttpTransport transport = new JdkHttpTransport(client, Duration.ofSeconds(5));
        assertNotNull(transport);
    }


    @Test
    void nullRequestTimeoutDisablesTimeout()
    {
        HttpClient client = HttpClient.newHttpClient();
        // Passing null is the documented way to opt out of the timeout; the constructor must not
        // reject it.
        JdkHttpTransport transport = new JdkHttpTransport(client, null);
        assertNotNull(transport);
    }
}
