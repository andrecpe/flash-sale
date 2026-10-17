#!/usr/bin/env bash
set -euo pipefail

jav 21 -Silencioso
cd "$(dirname "$0")/.."
./mvnw -q verify

