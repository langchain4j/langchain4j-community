package dev.langchain4j.community.mcp.server.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

class McpServerAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class));

    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues("langchain4j.community.mcp.server.enabled=true")
                .withBean(McpServerLifecycle.class, () -> mock(McpServerLifecycle.class));
    }

    @Test
    void disabledByDefaultAndExplicitly() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(McpServer.class)
                .doesNotHaveBean(StdioMcpServerTransport.class)
                .doesNotHaveBean(McpServerLifecycle.class));
        runner.withPropertyValues("langchain4j.community.mcp.server.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(McpServer.class));
    }

    @Test
    void backsOffWithoutServerClass() {
        runner.withClassLoader(new FilteredClassLoader(McpServer.class))
                .withPropertyValues("langchain4j.community.mcp.server.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(McpServerLifecycle.class));
    }

    @Test
    void selectsOnlyNamedBeansAndDefersTransportConstruction() {
        enabled()
                .withUserConfiguration(Tools.class)
                .withPropertyValues("langchain4j.community.mcp.server.tool-bean-names=calculator")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(McpServer.class);
                    assertThat(context.getBeanFactory().containsSingleton("stdioMcpServerTransport"))
                            .isFalse();
                    var response = new ObjectMapper()
                            .valueToTree(context.getBean(McpServer.class).handle(new ObjectMapper().readTree("""
                                    {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                                    """)));
                    assertThat(response.at("/result/tools")).hasSize(1);
                    assertThat(response.at("/result/tools/0/name").asText()).isEqualTo("add");
                    assertThat(context.getBean(McpServerProperties.class).getToolBeanNames())
                            .containsExactly("calculator");
                    assertThat(context.getEnvironment().getProperty("logging.config"))
                            .isNull();
                    assertThat(context.getEnvironment().getProperty("spring.main.banner-mode"))
                            .isNull();
                });
    }

    @Test
    void generatesTypedConfigurationMetadata() throws Exception {
        try (var metadata = Files.newInputStream(generatedMetadata("spring-configuration-metadata.json"))) {
            var properties = new ObjectMapper().readTree(metadata).path("properties");
            assertThat(properties).hasSize(2);
            assertThat(properties.get(0).path("name").asText()).isEqualTo("langchain4j.community.mcp.server.enabled");
            assertThat(properties.get(0).path("defaultValue").asBoolean()).isFalse();
            assertThat(properties.get(1).path("name").asText())
                    .isEqualTo("langchain4j.community.mcp.server.tool-bean-names");
            assertThat(properties.get(1).path("type").asText()).isEqualTo("java.util.List<java.lang.String>");
        }
    }

    @Test
    void generatesAutoConfigurationMetadata() throws Exception {
        try (var metadata = Files.newInputStream(generatedMetadata("spring-autoconfigure-metadata.properties"))) {
            var properties = new Properties();
            properties.load(metadata);
            assertThat(properties.getProperty(McpServerAutoConfiguration.class.getName() + ".ConditionalOnClass"))
                    .isEqualTo(McpServer.class.getName());
        }
    }

    private Path generatedMetadata(String name) throws Exception {
        // Inspect this module's output, not an identically named resource in a Boot dependency.
        Path metadata = Path.of(McpServerProperties.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .resolve("META-INF")
                .resolve(name);
        assertThat(metadata).isRegularFile();
        return metadata;
    }

    @Test
    void userServerOwnsToolRegistration() {
        McpServer server = new McpServer(List.of());
        enabled().withBean(McpServer.class, () -> server).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(McpServer.class);
            assertThat(context.getBean(McpServer.class)).isSameAs(server);
        });
    }

    @Test
    void userTransportBacksOff() {
        StdioMcpServerTransport transport = mock(StdioMcpServerTransport.class);
        enabled()
                .withBean(McpServer.class, () -> new McpServer(List.of()))
                .withBean("customTransport", StdioMcpServerTransport.class, () -> transport)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(StdioMcpServerTransport.class);
                    assertThat(context.getBean(StdioMcpServerTransport.class)).isSameAs(transport);
                    assertThat(context).doesNotHaveBean("stdioMcpServerTransport");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "missing", "calculator,calculator", "calculator,alias", "notATool", "prototype"})
    void rejectsInvalidSelection(String selection) {
        enabled()
                .withUserConfiguration(Tools.class)
                .withPropertyValues("langchain4j.community.mcp.server.tool-bean-names=" + selection)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingSelectionExplainsRequiredProperty() {
        enabled()
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "langchain4j.community.mcp.server.tool-bean-names must select at least one tool bean"));
    }

    @Test
    void duplicateProtocolNamesFail() {
        enabled()
                .withUserConfiguration(Tools.class)
                .withPropertyValues("langchain4j.community.mcp.server.tool-bean-names=calculator,other")
                .run(context ->
                        assertThat(context.getStartupFailure()).hasRootCauseMessage("Duplicated tool name: add"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsProxiesWithoutUnwrapping(boolean classProxy) {
        ProxyFactory factory = new ProxyFactory(new Calculator());
        factory.setProxyTargetClass(classProxy);
        factory.addAdvice((MethodInterceptor) invocation -> {
            throw new SecurityException("Advice must not be bypassed");
        });
        enabled()
                .withBean("secured", Object.class, factory::getProxy)
                .withPropertyValues("langchain4j.community.mcp.server.tool-bean-names=secured")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .rootCause()
                        .hasMessageContaining("must not be a proxy")
                        .hasMessageContaining("security or transaction advice"));
    }

    @Test
    void facadePreservesServiceAdvice() {
        ProxyFactory factory = new ProxyFactory(new Calculator());
        factory.addAdvice((MethodInterceptor) invocation -> {
            throw new SecurityException("Access denied by service advice");
        });
        CalculatorApi service = (CalculatorApi) factory.getProxy();
        enabled()
                .withBean("facade", Facade.class, () -> new Facade(service))
                .withPropertyValues("langchain4j.community.mcp.server.tool-bean-names=facade")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var response = new ObjectMapper()
                            .valueToTree(context.getBean(McpServer.class).handle(new ObjectMapper().readTree("""
                                    {"id":1,"method":"tools/call","params":{"name":"securedAdd","arguments":{}}}
                                    """)));
                    assertThat(response.at("/result/isError").asBoolean()).isTrue();
                    assertThat(response.at("/result/content/0/text").asText())
                            .contains("Access denied by service advice");
                });
    }

    interface CalculatorApi {
        int add(int a, int b);
    }

    static class Calculator implements CalculatorApi {
        @Override
        @Tool
        public int add(int a, int b) {
            return a + b;
        }
    }

    static class Facade {
        private final CalculatorApi service;

        Facade(CalculatorApi service) {
            this.service = service;
        }

        @Tool
        public int securedAdd() {
            return service.add(1, 2);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Tools {
        @Bean({"calculator", "alias"})
        Calculator calculator() {
            return new Calculator();
        }

        @Bean
        Calculator other() {
            return new Calculator();
        }

        @Bean
        Object notATool() {
            return new Object();
        }

        @Bean
        @Scope("prototype")
        Calculator prototype() {
            return new Calculator();
        }
    }
}
