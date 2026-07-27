#!/usr/bin/env bash
# Local equivalent of the `publish` job in .github/workflows/maven.yml.
# CI tags with the workflow run number; pass a tag explicitly when building by hand.
set -euo pipefail

IMAGE_NAME="hendisantika/spring-boot-k8s-argocd"
TAG="${1:-${GITHUB_RUN_NUMBER:?pass a tag as \$1 or set GITHUB_RUN_NUMBER}}"

docker build -t "$IMAGE_NAME:$TAG" .

docker push "$IMAGE_NAME:$TAG"
