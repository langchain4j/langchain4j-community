package dev.langchain4j.community.mcp.server.spring;

import static dev.langchain4j.community.mcp.server.spring.McpServerProperties.CONFIG_PREFIX;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@AutoConfiguration
@ConditionalOnClass(McpServer.class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpServer mcpServer(McpServerProperties properties, ConfigurableApplicationContext context) {
        List<String> names = properties.getToolBeanNames();
        Assert.notEmpty(names, CONFIG_PREFIX + ".tool-bean-names must select at least one tool bean");
        List<Object> tools = new ArrayList<>();
        Set<Object> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String name : names) {
            Assert.isTrue(StringUtils.hasText(name), CONFIG_PREFIX + ".tool-bean-names must not contain blank names");
            Assert.isTrue(context.containsBean(name), "MCP tool bean does not exist: " + name);
            Assert.isTrue(context.isSingleton(name), "MCP tool bean must be a singleton: " + name);
            Object tool = context.getBean(name);
            Assert.isTrue(selected.add(tool), "MCP tool bean selected more than once (possibly via an alias): " + name);
            Assert.isTrue(
                    !AopUtils.isAopProxy(tool) && !Proxy.isProxyClass(tool.getClass()),
                    "MCP tool bean must not be a proxy: " + name
                            + ". Use an unproxied @Tool facade that delegates to the proxied service; "
                            + "unwrapping proxies would bypass security or transaction advice.");
            Assert.isTrue(
                    Arrays.stream(tool.getClass().getDeclaredMethods())
                            .anyMatch(m -> m.isAnnotationPresent(Tool.class)),
                    "MCP tool bean must declare at least one @Tool method: " + name);
            tools.add(tool);
        }
        return new McpServer(tools);
    }

    @Bean(destroyMethod = "")
    @Lazy
    @ConditionalOnMissingBean
    public StdioMcpServerTransport stdioMcpServerTransport(McpServer server) {
        return new StdioMcpServerTransport(server);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpServerLifecycle mcpServerLifecycle(
            ObjectProvider<StdioMcpServerTransport> transport, ConfigurableApplicationContext context) {
        return new McpServerLifecycle(transport::getObject, context);
    }
}
