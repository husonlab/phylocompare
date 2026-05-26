#!/usr/bin/env bash
set -euo pipefail

# Build PhyloCompare without producing an installer.
# Purpose:
#   1. Build local Maven dependencies that are not available from Maven Central.
#   2. Install them into the local Maven cache.
#   3. Build the phylocompare project itself.
#
# Run from the phylocompare repository root:
#
#   bash scripts/build-phylocompare-no-installer.sh
#
# Optional:
#   SKIP_TESTS=false bash scripts/build-phylocompare-no-installer.sh

SKIP_TESTS="${SKIP_TESTS:-true}"
BUILD_DIR="${BUILD_DIR:-.build}"
DEPS_DIR="${BUILD_DIR}/deps"

JLoda3_REPO="${JLODA3_REPO:-https://github.com/husonlab/jloda3.git}"
SPLITSTREE6_REPO="${SPLITSTREE6_REPO:-https://github.com/husonlab/splitstree6.git}"

echo "== PhyloCompare build without installer =="
echo "Working directory: $(pwd)"
echo "Build directory:   ${BUILD_DIR}"
echo "Skip tests:        ${SKIP_TESTS}"
echo

if [[ ! -f "pom.xml" ]]; then
    echo "ERROR: This script must be run from the phylocompare repository root."
    echo "       No pom.xml found in current directory."
    exit 1
fi

if ! command -v git >/dev/null 2>&1; then
    echo "ERROR: git is not available."
    exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: mvn is not available."
    exit 1
fi

mkdir -p "${DEPS_DIR}"

build_dependency() {
    local name="$1"
    local repo="$2"

    echo
    echo "== Building dependency: ${name} =="

    if [[ ! -d "${DEPS_DIR}/${name}/.git" ]]; then
        echo "Cloning ${repo}"
        git clone "${repo}" "${DEPS_DIR}/${name}"
    else
        echo "Updating ${name}"
        git -C "${DEPS_DIR}/${name}" pull --ff-only
    fi

    local mvn_args=(-B clean install)

    if [[ "${SKIP_TESTS}" == "true" ]]; then
        mvn_args+=(-DskipTests)
    fi

    mvn -f "${DEPS_DIR}/${name}/pom.xml" "${mvn_args[@]}"
}

build_dependency "jloda3" "${JLoda3_REPO}"
build_dependency "splitstree6" "${SPLITSTREE6_REPO}"

echo
echo "== Building phylocompare =="

mvn_args=(-B clean package)

if [[ "${SKIP_TESTS}" == "true" ]]; then
    mvn_args+=(-DskipTests)
fi

mvn "${mvn_args[@]}"

echo
echo "== Build completed successfully =="
echo "Artifacts should be in:"
echo "  target/"