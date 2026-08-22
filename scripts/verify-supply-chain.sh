#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
security_directory="${repository_root}/target/security"
dependency_directory="${security_directory}/runtime-dependencies"
report_directory="${security_directory}/reports"
trivy_cache_directory="${REPORTING_TRIVY_CACHE_DIR:-/tmp/job-seeker-copilot-trivy-cache}"
trivy_image="${REPORTING_TRIVY_IMAGE:-aquasec/trivy:0.72.0}"

mkdir -p "${trivy_cache_directory}"

cd "${repository_root}"
mvn --batch-mode --no-transfer-progress clean verify \
    dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="${dependency_directory}"

mkdir -p "${report_directory}"
sbom_path="${repository_root}/target/classes/META-INF/sbom/application.cdx.json"
test -s "${sbom_path}"
(
    cd "$(dirname "${sbom_path}")"
    sha256sum "$(basename "${sbom_path}")"
) > "${security_directory}/sbom.sha256"

docker_network_arguments=()
trivy_database_arguments=()
if [[ "${REPORTING_TRIVY_OFFLINE:-false}" == "true" ]]; then
    docker_network_arguments=(--network none)
    trivy_database_arguments=(--skip-db-update --offline-scan)
fi

docker run --rm \
    --network none \
    --volume "${trivy_cache_directory}:/root/.cache/trivy" \
    "${trivy_image}" \
    --cache-dir /root/.cache/trivy \
    --version > "${report_directory}/trivy-version.txt"

docker run --rm \
    "${docker_network_arguments[@]}" \
    --volume "${dependency_directory}:/scan:ro" \
    --volume "${report_directory}:/reports" \
    --volume "${trivy_cache_directory}:/root/.cache/trivy" \
    "${trivy_image}" \
    rootfs \
    "${trivy_database_arguments[@]}" \
    --cache-dir /root/.cache/trivy \
    --scanners vuln \
    --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL \
    --format json \
    --output /reports/trivy-dependencies.json \
    /scan

docker run --rm \
    "${docker_network_arguments[@]}" \
    --volume "${dependency_directory}:/scan:ro" \
    --volume "${trivy_cache_directory}:/root/.cache/trivy" \
    "${trivy_image}" \
    rootfs \
    "${trivy_database_arguments[@]}" \
    --cache-dir /root/.cache/trivy \
    --scanners vuln \
    --severity CRITICAL,HIGH \
    --exit-code 1 \
    /scan

printf 'SBOM: %s\nDependency report: %s\n' \
    "${sbom_path}" \
    "${report_directory}/trivy-dependencies.json"
