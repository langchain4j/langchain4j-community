package dev.langchain4j.community.store;

import org.neo4j.cypherdsl.core.FunctionInvocation;

public class Neo4jUtils {

    public static FunctionInvocation.FunctionDefinition functionDef(String functionName) {
        return new FunctionInvocation.FunctionDefinition() {

            @Override
            public String getImplementationName() {
                return functionName;
            }

            @Override
            public boolean isAggregate() {
                return false;
            }
        };
    }
}
