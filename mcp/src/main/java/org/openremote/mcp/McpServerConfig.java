package org.openremote.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

public class McpServerConfig {

    public static void startMcpServer() throws Exception {
        WebFluxStreamableServerTransportProvider transportProvider = WebFluxStreamableServerTransportProvider.builder()
                .messageEndpoint("/mcp")
                .build();

        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("my-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .resources(false, true)  // Enable resource support
                        .tools(true)             // Enable tool support
                        .prompts(true)           // Enable prompt support
                        .logging()               // Enable logging support
                        .completions()           // Enable completions support
                        .build())
                .build();


        var schema = """
            {
              "type" : "object",
              "id" : "urn:jsonschema:Operation",
              "properties" : {
                "operation" : {
                  "type" : "string"
                },
                "a" : {
                  "type" : "number"
                },
                "b" : {
                  "type" : "number"
                }
              }
            }
            """;
        var syncToolSpecification = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("calculator", "calculator", "Basic calculator", schema),
                (exchange, arguments) -> {
                    // Tool implementation
                    return new McpSchema.CallToolResult(result, false);
                }
        );

        syncServer.addTool(syncToolSpecification);
    }
}
