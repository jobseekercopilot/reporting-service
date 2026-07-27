#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 ]]; then
    printf 'Usage: %s <local-image-reference>\n' "$0" >&2
    exit 2
fi

image_reference="$1"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
report_directory="${repository_root}/target/security/reports"
trivy_cache_directory="${REPORTING_TRIVY_CACHE_DIR:-/tmp/job-seeker-copilot-trivy-cache}"
trivy_image="${REPORTING_TRIVY_IMAGE:-aquasec/trivy:0.72.0}"
temporary_directory="$(mktemp -d /tmp/reporting-image-scan.XXXXXX)"
trap 'rm -rf -- "${temporary_directory}"' EXIT

mkdir -p "${report_directory}" "${trivy_cache_directory}"
docker save --output "${temporary_directory}/image.tar" "${image_reference}"

docker_network_arguments=()
trivy_database_arguments=()
if [[ "${REPORTING_TRIVY_OFFLINE:-false}" == "true" ]]; then
    docker_network_arguments=(--network none)
    trivy_database_arguments=(--skip-db-update --offline-scan)
fi

docker run --rm \
    "${docker_network_arguments[@]}" \
    --volume "${temporary_directory}/image.tar:/scan/image.tar:ro" \
    --volume "${report_directory}:/reports" \
    --volume "${trivy_cache_directory}:/root/.cache/trivy" \
    "${trivy_image}" \
    image \
    --input /scan/image.tar \
    "${trivy_database_arguments[@]}" \
    --cache-dir /root/.cache/trivy \
    --scanners vuln \
    --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL \
    --format json \
    --output /reports/trivy-image.json

docker run --rm \
    "${docker_network_arguments[@]}" \
    --volume "${temporary_directory}/image.tar:/scan/image.tar:ro" \
    --volume "${trivy_cache_directory}:/root/.cache/trivy" \
    "${trivy_image}" \
    image \
    --input /scan/image.tar \
    "${trivy_database_arguments[@]}" \
    --cache-dir /root/.cache/trivy \
    --scanners vuln \
    --severity CRITICAL,HIGH \
    --exit-code 1

printf 'Image report: %s\n' "${report_directory}/trivy-image.json"
