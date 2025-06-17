#!/bin/bash
docker run --rm -it -p 12345:12345 -v "$(pwd)/data:/app/data" rpc-voting-app

