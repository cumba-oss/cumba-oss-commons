package net.cumba.web.api.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SplitOpenApiSpecTest
{

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void splitsPaths(@TempDir Path tempDir) throws IOException
    {
        String spec = """
                {
                  "paths": {
                    "/api/studies": { "get": { "summary": "List studies" } },
                    "/api/studies/{id}": { "get": { "summary": "Get study" } }
                  }
                }
                """;

        Path inputFile = tempDir.resolve("api.json");
        Files.writeString(inputFile, spec);
        Path outputDir = tempDir.resolve("output");

        SplitOpenApiSpec.main(new String[]
        {
                inputFile.toString(), outputDir.toString()
        });

        assertTrue(Files.isDirectory(outputDir));
        // /api/studies → path_api_studies.json
        assertTrue(Files.exists(outputDir.resolve("path_api_studies.json")));
        // /api/studies/{id} → path_api_studies__id_.json (braces replaced by underscores)
        assertTrue(Files.exists(outputDir.resolve("path_api_studies__id_.json")));
    }


    @Test
    void splitsComponentSchemas(@TempDir Path tempDir) throws IOException
    {
        String spec = """
                {
                  "components": {
                    "schemas": {
                      "Study": { "type": "object", "properties": {} },
                      "Visit": { "type": "object", "properties": {} }
                    }
                  }
                }
                """;

        Path inputFile = tempDir.resolve("api.json");
        Files.writeString(inputFile, spec);
        Path outputDir = tempDir.resolve("output");

        SplitOpenApiSpec.main(new String[]
        {
                inputFile.toString(), outputDir.toString()
        });

        assertTrue(Files.exists(outputDir.resolve("components_schemas_Study.json")));
        assertTrue(Files.exists(outputDir.resolve("components_schemas_Visit.json")));
    }


    @Test
    void outputFilesContainValidJson(@TempDir Path tempDir) throws IOException
    {
        String spec = """
                {
                  "paths": {
                    "/api/items": { "get": { "summary": "List items" } }
                  }
                }
                """;

        Path inputFile = tempDir.resolve("api.json");
        Files.writeString(inputFile, spec);
        Path outputDir = tempDir.resolve("output");

        SplitOpenApiSpec.main(new String[]
        {
                inputFile.toString(), outputDir.toString()
        });

        Path outputFile = outputDir.resolve("path_api_items.json");
        String content = Files.readString(outputFile);
        var node = mapper.readTree(content);
        assertEquals("List items", node.get("get").get("summary").asText());
    }


    @Test
    void handlesSpecWithNeitherPathsNorSchemas(@TempDir Path tempDir) throws IOException
    {
        String spec = """
                {
                  "info": { "title": "My API" }
                }
                """;

        Path inputFile = tempDir.resolve("api.json");
        Files.writeString(inputFile, spec);
        Path outputDir = tempDir.resolve("output");

        // Should not throw
        assertDoesNotThrow(() -> SplitOpenApiSpec.main(new String[]
        {
                inputFile.toString(), outputDir.toString()
        }));
    }


    @Test
    void handlesBothPathsAndSchemas(@TempDir Path tempDir) throws IOException
    {
        String spec = """
                {
                  "paths": {
                    "/api/items": { "get": {} }
                  },
                  "components": {
                    "schemas": {
                      "Item": { "type": "object" }
                    }
                  }
                }
                """;

        Path inputFile = tempDir.resolve("api.json");
        Files.writeString(inputFile, spec);
        Path outputDir = tempDir.resolve("output");

        SplitOpenApiSpec.main(new String[]
        {
                inputFile.toString(), outputDir.toString()
        });

        assertTrue(Files.exists(outputDir.resolve("path_api_items.json")));
        assertTrue(Files.exists(outputDir.resolve("components_schemas_Item.json")));
    }
}
