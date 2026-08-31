package net.cumba.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.web.api.json.JsonArrayResource;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ApiArrayResource} interface contract — verifying that the interface methods
 * have the expected signatures and that implementations work via the interface type.
 */
class ApiArrayResourceTest
{

    @Test
    void interfaceMethodsAccessibleViaApiArrayResource()
    {
        // Verify the interface can be used as a type reference
        ApiArrayResource resource = JsonArrayResource
                .of(new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode());
        assertEquals(0, resource.getLength());
    }


    @Test
    void allTypeCheckMethodsReturnFalseForEmptyArray()
    {
        ApiArrayResource resource = JsonArrayResource
                .of(new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode());

        assertTrue(resource.getString(0).isEmpty());
        assertTrue(resource.getInt(0).isEmpty());
        assertTrue(resource.getLong(0).isEmpty());
        assertTrue(resource.getDouble(0).isEmpty());
        assertTrue(resource.getBoolean(0).isEmpty());
        assertTrue(resource.getNumber(0).isEmpty());
        assertTrue(resource.getObject(0, ApiResource.class).isEmpty());
        assertTrue(resource.getArray(0, ApiArrayResource.class).isEmpty());
        assertTrue(resource.getStringList(0).isEmpty());
        assertEquals(0, resource.getStringStream(0).count());
    }
}
