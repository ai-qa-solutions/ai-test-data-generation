#!/usr/bin/env bash
set -euo pipefail

# 1) Format & static checks
mvn -q spotless:apply
mvn -q -DskipTests=true checkstyle:check

# 2) Build & tests
mvn -q verify

# 3) Repo hygiene — fail if formatters changed tracked files
if ! git diff --quiet --exit-code; then
  echo "ERROR: Tracked files changed by the build/formatters. Commit them and re-run."
  exit 2
fi

# 4) No markers left in sources (ignore build, generated, and docs)
if git grep -n -E 'TO''DO|FIX''ME' -- ':!target' ':!**/generated/**' ':!**/*.md' ':!**/*.MD' | grep -q .; then
  echo "ERROR: todo/fixme markers present in sources."
  git grep -n -E 'TO''DO|FIX''ME' -- ':!target' ':!**/generated/**' ':!**/*.md' ':!**/*.MD' || true
  exit 3
fi

echo "Gatekeeper passed ✅"