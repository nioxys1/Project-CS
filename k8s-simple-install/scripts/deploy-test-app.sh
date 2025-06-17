#!/bin/bash
# Déploie une application nginx

kubectl apply -f $(dirname "$0")/../manifests/test-app.yaml
