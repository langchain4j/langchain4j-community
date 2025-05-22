package dev.langchain4j.community.store.embedding.neo4j;

import dev.langchain4j.Internal;
import org.neo4j.cypherdsl.core.FunctionInvocation;

@Internal
class Neo4jUtils {

    static FunctionInvocation.FunctionDefinition functionDef(String functionName) {
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
