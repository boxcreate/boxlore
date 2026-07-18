#!/usr/bin/env bash
# Keep master open for data bots + ensure the merge-ci label exists.
#
# IMPORTANT (user-owned repos):
# GitHub will NOT let the GitHub Actions Integration bypass repository rulesets.
# A "required status checks" ruleset on master therefore breaks scheduled bots
# (Check New Episodes, sync-pi-data, etc.) that push chore commits with [skip ci].
# Do NOT recreate master-required-checks here.
#
# Merge gating is workflow-side only: add the `merge-ci` label to run Unit +
# Instrumented jobs. Enforced required-checks need an org (Actions bypass) or
# merge queue — neither works cleanly on this user-owned repo today.
#
#   ./scripts/ci/configure-master-merge-queue.sh
set -euo pipefail

OWNER="${OWNER:-ashwkun}"
REPO="${REPO:-boxlore}"

echo "Checking auth can administer ${OWNER}/${REPO}..."
if ! gh api "repos/${OWNER}/${REPO}" --jq '.permissions.admin' | grep -qx true; then
  echo "error: current gh token lacks admin on ${OWNER}/${REPO}" >&2
  echo "       Sign in as the owner: gh auth login" >&2
  exit 1
fi

echo "Ensuring label merge-ci..."
gh label create merge-ci \
  --repo "${OWNER}/${REPO}" \
  --description "Add when ready to merge — starts Unit + Instrumented CI (not on every push)." \
  --color "0E8A16" \
  --force

# Classic branch protection also blocks non-admin direct pushes.
if gh api "repos/${OWNER}/${REPO}/branches/master/protection" >/dev/null 2>&1; then
  echo "Removing classic branch protection on master (bots need direct push)..."
  gh api --method DELETE "repos/${OWNER}/${REPO}/branches/master/protection"
fi

# Remove any rulesets that require status checks / merge queue on master.
# These reject github-actions[bot] pushes on user-owned repositories.
while IFS= read -r id; do
  [[ -z "${id}" || "${id}" == "null" ]] && continue
  name="$(gh api "repos/${OWNER}/${REPO}/rulesets/${id}" --jq '.name')"
  echo "Deleting ruleset ${name} (id=${id}) — blocks bot pushes on user-owned repos..."
  gh api --method DELETE "repos/${OWNER}/${REPO}/rulesets/${id}"
done < <(
  gh api "repos/${OWNER}/${REPO}/rulesets" \
    | jq -r '.[] | select(.name == "master-required-checks" or .name == "master-merge-queue") | .id'
)

echo
echo "Master is clear of required-check / merge-queue rulesets."
echo "  Rules: https://github.com/${OWNER}/${REPO}/settings/rules"
echo
echo "Workflow (label gate only — not GitHub-enforced):"
echo "  1. Open / update PR as usual (no Unit/Instrumented CI yet)"
echo "  2. Add label \`merge-ci\` when ready → Unit + Instrumented run"
echo "  3. Merge after checks are green (honor the process; ruleset cannot enforce it here)"
echo "Scheduled bots can push [skip ci] chore commits to master again."
