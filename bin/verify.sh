#!/usr/bin/env bash
set -euo pipefail

# 1) Format & static checks
mvn -q spotless:apply

# 2) Build & run all tests (unit + any configured integration tests)
#    'verify' runs checks after tests in the Maven lifecycle.
mvn -q verify

# 3) Repository hygiene
# Fail if formatters changed tracked files and they are not committed yet
if ! git diff --quiet --exit-code; then
  echo "ERROR: Tracked files changed by the build/formatters. Commit them and re-run."
  exit 2
fi

# 4) No TODO/FIXME left in tracked sources (excluding build outputs)
if git grep -n -E 'TODO|FIXME' -- ':!target' ':!**/generated/**' | grep -q .; then
  echo "ERROR: TODO/FIXME markers present in sources."
  git grep -n -E 'TODO|FIXME' -- ':!target' ':!**/generated/**' || true
  exit 3
fi

echo "Gatekeeper passed ✅"
