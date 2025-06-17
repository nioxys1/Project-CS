#!/bin/bash
# Installe un worker et le joint au cluster

set -e

bash $(dirname "$0")/common-setup.sh

if [ -f /tmp/join.sh ]; then
  bash /tmp/join.sh
else
  echo "Erreur : /tmp/join.sh manquant. Copiez ce fichier depuis le master."
  exit 1
fi
