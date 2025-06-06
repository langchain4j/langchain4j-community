package dev.langchain4j.community.store.memory.chat.neo4j;

import dev.langchain4j.Internal;
import org.neo4j.cypherdsl.core.FunctionInvocation;

@Internal
class Neo4jUtils {

    private Neo4jUtils() throws InstantiationException {
        throw new InstantiationException("Cannot instantiate utility class.");
    }

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
