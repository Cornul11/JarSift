#!/usr/bin/bash
docker run -it --rm -v "$(pwd)/container-central-index:/app/target/central-index" -p 8080:8080 cornul11/maven-index:latest