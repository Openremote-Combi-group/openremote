package org.openremote.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;

@Configuration
class McpConfig {
    @Bean
    WebFluxStreamableServerTransportProvider webFluxStreamableServerTransportProvider(ObjectMapper mapper) {
        return this.mcpStreamableServerTransportProvider = WebFluxStreamableServerTransportProvider.builder()
                .objectMapper(mapper)
                .messageEndpoint("/mcp")
                .build();
    }

    @Bean
    RouterFunction<?> mcpRouterFunction(WebFluxStreamableServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }
}
