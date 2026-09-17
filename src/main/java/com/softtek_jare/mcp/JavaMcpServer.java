/*
 * MIT License
 *
 * Copyright (c) 2026 Alejandro Ferreira
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.softtek_jare.mcp;

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

import com.softtek_jare.mcp.tools.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * The {@code JavaMcpServer} class.
 *
 * @author Alejandro Ferreira
 * @author Janusch Rentenatus
 */
public class JavaMcpServer {

    private final ProjectManager projectManager = new ProjectManager();
    private final com.softtek_jare.mcp.edit.EditManager editManager = new com.softtek_jare.mcp.edit.EditManager();

/**
 * Entry point that loads logging configuration and starts the MCP server.
 */
    public static void main(String[] args) {
        loadLoggingConfig();
        JavaMcpServer server = new JavaMcpServer();
        server.start();
    }

/**
 * Loads the YAML logging configuration from the classpath and sets system properties.
 */
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

/**
 * Flattens a nested map into dot-separated system property keys.
 */
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

/**
 * Builds and starts the MCP server with all registered tools over stdio transport.
 */
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
                new FindReferencesTool(projectManager).build(),
                new CheckProjectDirtyTool(projectManager).build(),
                new ReloadJavaProjectTool(projectManager).build(),
                new EditLineTool(projectManager, editManager).build(),
                new RenameSymbolTool(projectManager, editManager).build(),
                new ReplaceMethodBodyTool(projectManager, editManager).build(),
                new AddMethodTool(projectManager, editManager).build(),
                new AddFieldTool(projectManager, editManager).build(),
                new RemoveMemberTool(projectManager, editManager).build(),
                new AddAnnotationTool(projectManager, editManager).build(),
                new RemoveAnnotationTool(projectManager, editManager).build(),
                new EditAnnotationTool(projectManager, editManager).build(),
                new RewriteSignatureTool(projectManager, editManager).build(),
                new AddPackageTool(projectManager).build(),
                new AddClassTool(projectManager).build(),
                new MoveClassTool(projectManager, editManager).build()
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
