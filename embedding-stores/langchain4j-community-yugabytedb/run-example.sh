#!/bin/bash

# Simple script to run YugabyteDB example with Docker
# Usage: ./run-example.sh

set -e

echo "🚀 Running YugabyteDB Example with Docker..."

# Check if Docker YugabyteDB container is running
if ! docker ps | grep -q yugabyte-local; then
    echo "⚠️  YugabyteDB Docker container not found. Starting it..."
    docker run -d --name yugabyte-local --hostname yugabyte-local \
      -p 7001:7000 -p 9001:9000 -p 15434:15433 -p 5434:5433 -p 9043:9042 \
      -v ~/yb_data:/home/yugabyte/yb_data \
      yugabytedb/yugabyte:2.25.2.0-b359 bin/yugabyted start \
      --base_dir=/home/yugabyte/yb_data \
      --background=false
    
    echo "⏳ Waiting for YugabyteDB to start..."
    sleep 10
    
    # Enable pgvector extension
    docker exec yugabyte-local bash -c '/home/yugabyte/bin/ysqlsh --host yugabyte-local -U yugabyte -d yugabyte -c "CREATE EXTENSION IF NOT EXISTS vector;"'
    echo "✅ YugabyteDB Docker container is ready!"
fi

# Set up Java 17
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# Navigate to project root
cd /home/krishna/code/langchain4j-community

# Build if needed
echo "📦 Building project..."
./mvnw compile test-compile -pl embedding-stores/langchain4j-community-yugabytedb -am -q

# Navigate to module directory
cd embedding-stores/langchain4j-community-yugabytedb

# Get classpath
CLASSPATH="target/classes:target/test-classes:$(./../../mvnw dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)"

# Run the example
echo "▶️  Starting YugabyteDB example..."
java -cp "$CLASSPATH" dev.langchain4j.community.store.embedding.yugabytedb.YugabyteDBExample

echo "✅ Done!"
