#!/bin/bash

echo "🧪 YugabyteDB Test Runner with Detailed Logging"
echo "==============================================="
echo ""

# Auto-detect Java installation
detect_java() {
    if [ -n "$JAVA_HOME" ]; then
        echo "Using JAVA_HOME: $JAVA_HOME"
        return
    fi
    
    # Try common Java 17+ installations
    for java_path in \
        "/usr/lib/jvm/java-17-openjdk"* \
        "/usr/lib/jvm/java-17"* \
        "/usr/lib/jvm/java-11-openjdk"* \
        "/usr/lib/jvm/java-11"* \
        "/usr/lib/jvm/java-8-openjdk"* \
        "/usr/lib/jvm/java-8"* \
        "/opt/java"* \
        "/usr/local/java"*; do
        if [ -d "$java_path" ] && [ -x "$java_path/bin/java" ]; then
            export JAVA_HOME="$java_path"
            echo "Auto-detected Java: $JAVA_HOME"
            return
        fi
    done
    
    echo "❌ No Java installation found!"
    echo "💡 Please set JAVA_HOME or install Java:"
    echo "   export JAVA_HOME=/path/to/java"
    exit 1
}

# Detect and set Java
detect_java
export PATH=$JAVA_HOME/bin:$PATH

echo "Using Java version:"
java -version | head -1

echo ""
echo "💡 Configuration:"
echo "   Java: $JAVA_HOME"
echo "   Tests: Using TestContainers (no external database needed)"
echo ""
echo "   To customize Java, set environment variable:"
echo "   export JAVA_HOME=your_java_path"
echo ""

echo ""
echo "1️⃣ Available Test Classes:"
echo "=========================="
find src/test -name "*IT.java" -exec basename {} \; | sort

echo ""
echo "2️⃣ Choose Test to Run:"
echo "======================"
echo "EMBEDDING STORE TESTS:"
echo "1. YugabyteDBConnectionIT (PostgreSQL JDBC driver tests)"
echo "2. YugabyteDBEmbeddingStoreIT (Basic embedding operations)"
echo "3. YugabyteDBEmbeddingStorePerformanceIT (Performance tests)"
echo "4. YugabyteDBEmbeddingStoreConfigIT (Configuration tests)"
echo "5. YugabyteDBEmbeddingStoreRemovalIT (Removal operations)"
echo "6. YugabyteDBVectorIndexIT (Vector index types: ybhnsw, NoIndex)"
echo "7. YugabyteDBRAGWorkflowIT (RAG pipeline tests)"
echo "8. YugabyteDBWithActualLLMIT (LLM integration tests)"
echo ""
echo "CHAT MEMORY STORE TESTS:"
echo "9. YugabyteDBChatMemoryStoreIT (Basic memory operations)"
echo "10. YugabyteDBChatMemoryStoreConcurrencyIT (Memory concurrency)"
echo "11. YugabyteDBChatMemoryStoreTTLIT (Memory TTL & cleanup)"
echo ""
echo "COMPREHENSIVE TESTS:"
echo "12. All Integration Tests"
echo "13. Unit Tests Only (no database connection)"

echo ""
read -p "Choose test (1-13): " choice

# Find and change to project root directory (where mvnw is located)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "📁 Script directory: $SCRIPT_DIR"
echo "📁 Project root: $PROJECT_ROOT"

# Verify mvnw exists in project root
if [ ! -f "$PROJECT_ROOT/mvnw" ]; then
    echo "❌ mvnw not found in project root: $PROJECT_ROOT"
    echo "💡 Make sure you're running this script from the correct location"
    exit 1
fi

cd "$PROJECT_ROOT"
echo "✅ Changed to project root: $(pwd)"

case $choice in
    1)
        echo ""
        echo "🧪 Running YugabyteDBConnectionIT..."
        echo "===================================="
        echo "This test will:"
        echo "• Test PostgreSQL JDBC driver connection (recommended)"
        echo "• Test YugabyteDB driver connection (alternative)"
        echo "• Test connection pooling with HikariCP"
        echo "• Verify pgvector extension"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBConnectionIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG \
            -Dorg.slf4j.simpleLogger.showDateTime=true \
            -Dorg.slf4j.simpleLogger.dateTimeFormat="HH:mm:ss"
        ;;
    2)
        echo ""
        echo "🧪 Running YugabyteDBEmbeddingStoreIT..."
        echo "======================================"
        echo "This test will:"
        echo "• Test basic embedding operations (add, search, remove)"
        echo "• Test metadata filtering and search"
        echo "• Test different distance metrics"
        echo "• Test table creation and management"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBEmbeddingStoreIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    3)
        echo ""
        echo "🧪 Running YugabyteDBEmbeddingStorePerformanceIT..."
        echo "=================================================="
        echo "This test will:"
        echo "• Test bulk insertion (100+ documents)"
        echo "• Test concurrent operations (5 threads)"
        echo "• Test large dataset search (200+ documents)"
        echo "• Test high-dimensional embeddings (768-dim)"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBEmbeddingStorePerformanceIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    4)
        echo ""
        echo "🧪 Running YugabyteDBEmbeddingStoreConfigIT..."
        echo "============================================="
        echo "This test will:"
        echo "• Test different configuration options"
        echo "• Test table creation parameters"
        echo "• Test dimension and metric type settings"
        echo "• Test connection pool configurations"
        echo "• Test COLUMN_PER_KEY, COMBINED_JSON, and COMBINED_JSONB metadata storage modes"
        echo "• Test SQL injection prevention"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest="YugabyteDBEmbeddingStoreConfigIT\$*" \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    5)
        echo ""
        echo "🧪 Running YugabyteDBEmbeddingStoreRemovalIT..."
        echo "=============================================="
        echo "This test will:"
        echo "• Test removal by ID and filter"
        echo "• Test bulk removal operations"
        echo "• Test removal with metadata filtering"
        echo "• Test cleanup and data integrity"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBEmbeddingStoreRemovalIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    6)
        echo ""
        echo "🧪 Running YugabyteDBVectorIndexIT..."
        echo "====================================="
        echo "This test will:"
        echo "• Test HNSW index creation and search"
        echo "• Test ybhnsw (YugabyteDB's HNSW) index with different metrics"
        echo "• Test NoIndex (sequential scan) functionality"
        echo "• Test custom index parameters (m, efConstruction, lists)"
        echo "• Test different distance metrics with indexes"
        echo "• Test metadata filtering with vector indexes"
        echo "• Test custom and default index naming"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBVectorIndexIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    7)
        echo ""
        echo "🧪 Running YugabyteDBRAGWorkflowIT..."
        echo "==================================="
        echo "This test will:"
        echo "• Test complete RAG pipeline"
        echo "• Test document chunking"
        echo "• Test embedding generation and storage"
        echo "• Test metadata-based RAG filtering"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBRAGWorkflowIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    8)
        echo ""
        echo "🧪 Running YugabyteDBWithActualLLMIT..."
        echo "======================================"
        echo "This test will:"
        echo "• Test OpenAI integration with RAG"
        echo "• Test Ollama integration with RAG"
        echo "• Test Claude-style model integration"
        echo "• Test LLM response comparison"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBWithActualLLMIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    9)
        echo ""
        echo "🧪 Running YugabyteDBChatMemoryStoreIT..."
        echo "========================================"
        echo "This test will:"
        echo "• Test basic memory operations (store, retrieve, update, delete)"
        echo "• Test conversation handling and message types"
        echo "• Test input validation and error handling"
        echo "• Test special characters and Unicode support"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBChatMemoryStoreIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    10)
        echo ""
        echo "🧪 Running YugabyteDBChatMemoryStoreConcurrencyIT..."
        echo "=================================================="
        echo "This test will:"
        echo "• Test concurrent access (10 threads, 50 ops each)"
        echo "• Test concurrent updates to same memory ID"
        echo "• Test mixed concurrent operations (read/write/delete)"
        echo "• Test high concurrency stress (20 threads)"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBChatMemoryStoreConcurrencyIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    11)
        echo ""
        echo "🧪 Running YugabyteDBChatMemoryStoreTTLIT..."
        echo "==========================================="
        echo "This test will:"
        echo "• Test TTL expiration functionality"
        echo "• Test multiple TTL messages with different expiration times"
        echo "• Test cleanup operations for expired messages"
        echo "• Test TTL behavior with message updates"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest=YugabyteDBChatMemoryStoreTTLIT \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    12)
        echo ""
        echo "🧪 Running All Integration Tests..."
        echo "=================================="
        echo "This will run all tests that connect to YugabyteDB:"
        echo "• Embedding store tests (connection, basic, performance, config, removal, vector indexes, RAG, LLM)"
        echo "• Chat memory store tests (basic, concurrency, TTL)"
        echo "• All integration tests with TestContainers"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest="*IT" \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG \
            -Dorg.slf4j.simpleLogger.showDateTime=true
        ;;
    13)
        echo ""
        echo "🧪 Running Unit Tests Only..."
        echo "============================="
        echo "This will run tests that don't need database connection:"
        echo "• MetadataStorageConfigTest"
        echo "• YugabyteDBMetadataFilterMapperTest"
        echo ""
        
        ./mvnw test -pl embedding-stores/langchain4j-community-yugabytedb \
            -Dtest="*Test" \
            -Dorg.slf4j.simpleLogger.log.dev.langchain4j=DEBUG
        ;;
    *)
        echo "❌ Invalid choice. Please select 1-13."
        exit 1
        ;;
esac

echo ""
echo "🎉 Test execution completed!"
echo ""
echo "💡 Understanding the output:"
echo "============================"
echo "• [INFO] lines show Maven build progress"
echo "• Test method names show what's being tested"
echo "• ✅ lines show successful operations"
echo "• [DEBUG] lines show detailed internal operations"
echo "• Final summary shows: Tests run, Failures, Errors, Skipped"
echo ""
echo "🔍 If tests fail:"
echo "• Check for any ERROR messages in the output"
echo "• Ensure Docker is running (for TestContainers)"
echo "• Verify Java version compatibility"
echo "• Check Maven dependencies are resolved"
echo ""
echo "📊 Test Categories:"
echo "• Embedding Store: Vector similarity search, metadata filtering, RAG"
echo "• Chat Memory Store: Conversation persistence, TTL, session management"




