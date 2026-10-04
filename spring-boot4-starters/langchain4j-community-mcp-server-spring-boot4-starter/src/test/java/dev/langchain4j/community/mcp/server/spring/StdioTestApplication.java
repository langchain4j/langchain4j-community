package dev.langchain4j.community.mcp.server.spring;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
public class StdioTestApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(StdioTestApplication.class, args);
        LoggerFactory.getLogger(StdioTestApplication.class).info("MAIN_RETURNED");
        if (context.getEnvironment().getProperty("test.close-context", Boolean.class, false)) {
            context.close();
        }
    }

    @Bean
    Calculator calculator() {
        return new Calculator();
    }

    @Bean
    Unselected unselected() {
        return new Unselected();
    }

    static class Calculator {
        @Tool("Add two integers")
        public int add(@P(name = "a") int a, @P(name = "b") int b) {
            return a + b;
        }
    }

    static class Unselected {
        @Tool
        public String hidden() {
            return "not exposed";
        }
    }
}
