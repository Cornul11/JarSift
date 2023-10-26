#!/bin/bash

INDEX_DIR="/app/target/central-index"
INDEX_MARKER="$INDEX_DIR/write.lock"

if [ ! -e "$INDEX_MARKER" ]; then
  echo "Maven index not found or incomplete. Running update..."
  java -cp "/app/maven-local-index-timestamp-lookup-7.0.3.jar:/app/libs/*" org.cornul11.maven.MavenIndexTimestampLookup update
fi

java -cp "/app/maven-local-index-timestamp-lookup-7.0.3.jar:/app/libs/*" org.cornul11.maven.MavenIndexTimestampLookup server