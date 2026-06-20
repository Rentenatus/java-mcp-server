package com.softtek.mcp;

import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.softtek.mcp.tools.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class JavaMcpServer {

    private final ProjectManager projectManager = new ProjectManager();

    public static void main(String[] args) {
        loadLoggingConfig();
        JavaMcpServer server = new JavaMcpServer();
        server.start();
    }

    private static void loadLoggingConfig() {
        try (InputStream in = JavaMcpServer.class.getResourceAsStream("/application.yaml")) {
            if (in != null) {
                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(in);
                flatten("", config);
            }
        } catch (Exception e) {
            System.err.println("Warning: could not load application.yaml: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map) {
        map.forEach((key, value) -> {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map) {
                flatten(fullKey, (Map<String, Object>) value);
            } else {
                System.setProperty(fullKey, String.valueOf(value));
            }
        });
    }

    private void start() {
        String serverVersion = VersionLoader.getVersion();
        Logger log = LoggerFactory.getLogger(JavaMcpServer.class);

        List<McpServerFeatures.SyncToolSpecification> toolSpecs = List.of(
                new LoadJavaProjectTool(projectManager).build(),
                new ListLoadedProjectsTool(projectManager).build(),
                new UnloadJavaProjectTool(projectManager).build(),
                new ProjectMetadataTool(projectManager).build(),
                new InspectBuildConfigTool(projectManager).build(),
                new ListPackagesTool(projectManager).build(),
                new ListClassesTool(projectManager).build(),
                new ListMethodsTool(projectManager).build(),
                new InspectClassTool(projectManager).build(),
                new InspectMethodTool(projectManager).build(),
                new ListAnnotationsTool(projectManager).build(),
                new FindAnnotatedElementsTool(projectManager).build(),
                new GetTypeHierarchyTool(projectManager).build(),
                new ListDependenciesTool(projectManager).build(),
                new SearchSourceTool(projectManager).build(),
                new MultiFileSearchTool(projectManager).build(),
                new InspectFieldTool(projectManager).build(),
                new ListConstructorsTool(projectManager).build(),
                new ListEnumConstantsTool(projectManager).build(),
                new ResolveTypeTool(projectManager).build(),
                new ValidateCodeReferenceTool(projectManager).build(),
                new FindImplementationsTool(projectManager).build(),
                new GetFileContentTool(projectManager).build(),
                new GetAnnotationDetailsTool(projectManager).build(),
                new ListMethodsByReturnTypeTool(projectManager).build(),
                new ListMethodInvocationsTool(projectManager).build(),
                new FindReferencesTool(projectManager).build()
        );

        McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

        var server = McpServer.sync(transportProvider)
                .serverInfo("java-mcp-server", serverVersion)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(toolSpecs)
                .build();

        log.info("MCP server started (java-mcp-server v{}).", serverVersion);

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down java-mcp-server...");
            server.closeGracefully();
            latch.countDown();
            log.info("java-mcp-server shut down complete");
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            log.warn("Main thread interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}
