#!/bin/bash
mkdir -p build
javac -d build src/*.java
cp data/*.txt build/
docker build -t rpc-voting-app .
