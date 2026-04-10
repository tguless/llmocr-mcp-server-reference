package com.llmocr.mcp.invoice;

import com.networknt.schema.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;

public class ValidateTest {
    public static void main(String[] args) throws Exception {
        String dataPath = args.length > 0 ? args[0] : "../test.json";
        String schemaPath = args.length > 1 ? args[1] : "../test-schema.json";
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaNode = mapper.readTree(Files.readString(Path.of(schemaPath)));
        JsonNode dataNode = mapper.readTree(Files.readString(Path.of(dataPath)));
        
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema schema = factory.getSchema(schemaNode);
        
        var errors = schema.validate(dataNode);
        if (errors.isEmpty()) {
            System.out.println("✅ JSON is VALID against schema!");
        } else {
            System.out.println("❌ Validation FAILED with " + errors.size() + " errors:");
            int i = 0;
            for (var e : errors) {
                if (i++ >= 25) {
                    System.out.println("  ... and " + (errors.size() - 25) + " more");
                    break;
                }
                System.out.println("  " + (i) + ". " + e.getInstanceLocation() + ": " + e.getMessage());
            }
        }
    }
}
