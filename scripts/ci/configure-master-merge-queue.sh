#!/usr/bin/env bash
# Enable master merge queue + required checks, with bot-safe bypass.
#
# Required checks (must be green before merge / in the queue):
#   1. testDebugUnitTest            — unit job (PR + merge_group; cancel-in-progress)
#   2. coderabbit-threads-resolved — unresolved CodeRabbit threads must be Resolved
#
# Not required (still run on PRs, no bridge WF):
#   - SonarCloud App (quality gate fails the Sonar check on the PR)
#   - CodeRabbit App status (means "review finished" only — not a merge gate)
#   - Gitleaks (secret scan on PR/push)
#
# Bypass (always): boxlore-master-pusher Integration (app 4340323) + Organization admins.
#
#   ./scripts/ci/configure-master-merge-queue.sh
set -euo pipefail

OWNER="${OWNER:-boxcreate}"
REPO="${REPO:-boxlore}"
RULESET_NAME="master-merge-queue"

# Custom GitHub App installed on the org for ruleset-bypassing master pushes.
# Do NOT use the built-in GitHub Actions app (15368) — API returns 422 on org repos.
PUSHER_APP_ID="${PUSHER_APP_ID:-4340323}"
# OrganizationAdmin role constant for ruleset bypass_actors
ORG_ADMIN_ACTOR_ID=1

echo "Checking auth can administer ${OWNER}/${REPO}..."
if ! gh api "repos/${OWNER}/${REPO}" --jq '.permissions.admin' | grep -qx true; then
  echo "error: current gh token lacks admin on ${OWNER}/${REPO}" >&2
  echo "       Sign in as an org admin: gh auth login" >&2
  exit 1
fi

# Classic branch protection conflicts with ruleset merge queue + blocks bot pushes.
if gh api "repos/${OWNER}/${REPO}/branches/master/protection" >/dev/null 2>&1; then
  echo "Removing classic branch protection on master..."
  gh api --method DELETE "repos/${OWNER}/${REPO}/branches/master/protection"
fi

# Drop legacy ruleset names from the user-owned-repo era.
while IFS= read -r id; do
  [[ -z "${id}" || "${id}" == "null" ]] && continue
  name="$(gh api "repos/${OWNER}/${REPO}/rulesets/${id}" --jq '.name')"
  if [[ "${name}" == "master-required-checks" ]]; then
    echo "Deleting legacy ruleset ${name} (id=${id})..."
    gh api --method DELETE "repos/${OWNER}/${REPO}/rulesets/${id}"
  fi
done < <(gh api "repos/${OWNER}/${REPO}/rulesets" --jq '.[].id')

echo "Enabling auto-merge on the repository..."
gh api --method PATCH "repos/${OWNER}/${REPO}" \
  -f allow_auto_merge=true \
  -f delete_branch_on_merge=true \
  >/dev/null

build_ruleset_body() {
  local include_pusher_integration="$1"
  jq -n \
    --arg name "${RULESET_NAME}" \
    --argjson pusher_app_id "${PUSHER_APP_ID}" \
    --argjson org_admin_id "${ORG_ADMIN_ACTOR_ID}" \
    --argjson include_pusher "${include_pusher_integration}" \
    '{
      name: $name,
      target: "branch",
      enforcement: "active",
      conditions: {
        ref_name: {
          include: ["refs/heads/master"],
          exclude: []
        }
      },
      bypass_actors: (
        [
          {
            actor_id: $org_admin_id,
            actor_type: "OrganizationAdmin",
            bypass_mode: "always"
          }
        ]
        + if $include_pusher then
            [{
              actor_id: $pusher_app_id,
              actor_type: "Integration",
              bypass_mode: "always"
            }]
          else
            []
          end
      ),
      rules: [
        {
          type: "merge_queue",
          parameters: {
            merge_method: "SQUASH",
            max_entries_to_build: 5,
            min_entries_to_merge: 1,
            max_entries_to_merge: 5,
            min_entries_to_merge_wait_minutes: 0,
            check_response_timeout_minutes: 90,
            grouping_strategy: "ALLGREEN"
          }
        },
        {
          type: "pull_request",
          parameters: {
            required_approving_review_count: 0,
            dismiss_stale_reviews_on_push: false,
            require_code_owner_review: false,
            require_last_push_approval: false,
            required_review_thread_resolution: true,
            allowed_merge_methods: ["squash"]
          }
        },
        {
          type: "required_status_checks",
          parameters: {
            strict_required_status_checks_policy: false,
            do_not_enforce_on_create: true,
            required_status_checks: [
              { context: "testDebugUnitTest" },
              { context: "coderabbit-threads-resolved" }
            ]
          }
        },
        { type: "non_fast_forward" }
      ]
    }'
}

LAST_RULESET_ERR=""
apply_ruleset() {
  local body="$1"
  local existing_id="$2"
  local tmp_err
  tmp_err="$(mktemp)"
  local http_out=""
  local status=0
  LAST_RULESET_ERR=""

  if [[ -n "${existing_id}" ]]; then
    echo "Updating ruleset ${RULESET_NAME} (id=${existing_id})..."
    set +e
    http_out="$(echo "${body}" | gh api --method PUT "repos/${OWNER}/${REPO}/rulesets/${existing_id}" --input - 2>"${tmp_err}")"
    status=$?
    set -e
  else
    echo "Creating ruleset ${RULESET_NAME}..."
    set +e
    http_out="$(echo "${body}" | gh api --method POST "repos/${OWNER}/${REPO}/rulesets" --input - 2>"${tmp_err}")"
    status=$?
    set -e
  fi

  if [[ "${status}" -ne 0 ]]; then
    LAST_RULESET_ERR="$(cat "${tmp_err}" 2>/dev/null || true)"
    if [[ -n "${http_out}" ]]; then
      LAST_RULESET_ERR="${LAST_RULESET_ERR}"$'\n'"${http_out}"
    fi
    echo "Ruleset API error:" >&2
    echo "${LAST_RULESET_ERR}" >&2
    rm -f "${tmp_err}"
    return "${status}"
  fi

  rm -f "${tmp_err}"
  echo "${http_out}" | jq -r '"Ruleset id=\(.id) name=\(.name) enforcement=\(.enforcement)"'
  return 0
}

EXISTING_ID="$(
  gh api "repos/${OWNER}/${REPO}/rulesets" \
    --jq ".[] | select(.name == \"${RULESET_NAME}\") | .id" \
    | head -n1
)"

PUSHER_BYPASS_OK=true
RULESET_BODY="$(build_ruleset_body true)"
if ! apply_ruleset "${RULESET_BODY}" "${EXISTING_ID}"; then
  EXISTING_ID="$(
    gh api "repos/${OWNER}/${REPO}/rulesets" \
      --jq ".[] | select(.name == \"${RULESET_NAME}\") | .id" \
      | head -n1
  )"

  echo >&2
  echo "BLOCKER: cannot add Integration ${PUSHER_APP_ID} (boxlore-master-pusher) as bypass_actor." >&2
  echo "Retrying ruleset without Integration bypass (OrganizationAdmin only)..." >&2
  # Do not fall back to GitHub Actions app 15368 — it 422s on org repos.
  PUSHER_BYPASS_OK=false
  RULESET_BODY="$(build_ruleset_body false)"
  if ! apply_ruleset "${RULESET_BODY}" "${EXISTING_ID}"; then
    echo "error: ruleset create/update failed after retries" >&2
    exit 1
  fi
fi

FINAL_ID="$(
  gh api "repos/${OWNER}/${REPO}/rulesets" \
    --jq ".[] | select(.name == \"${RULESET_NAME}\") | .id" \
    | head -n1
)"

echo
echo "Master merge queue is active (ruleset id=${FINAL_ID})."
echo "  Rules: https://github.com/${OWNER}/${REPO}/settings/rules"
echo "  Required checks: testDebugUnitTest | coderabbit-threads-resolved"
if [[ "${PUSHER_BYPASS_OK}" == "true" ]]; then
  echo "  Bypass: boxlore-master-pusher (app ${PUSHER_APP_ID}) + Organization admins"
else
  echo "  Bypass: Organization admins ONLY (Integration ${PUSHER_APP_ID} rejected by GitHub API)"
  echo
  echo "BLOCKER: bot pushes via Integration bypass are NOT configured." >&2
  echo "  Ensure app ${PUSHER_APP_ID} (boxlore-master-pusher) is installed on the org," >&2
  echo "  and BOXLORE_PUSHER_APP_ID / BOXLORE_PUSHER_PRIVATE_KEY are set as repo secrets." >&2
fi
echo
echo "PR flow:"
echo "  1. Push → unit tests (cancel prior) + gitleaks + Sonar/CodeRabbit apps + threads gate"
echo "  2. Resolve CodeRabbit threads until coderabbit-threads-resolved is green"
echo "  3. Merge when ready → merge queue re-runs unit + threads gate"
echo "  4. Optional: Actions → Run workflow (Unit Tests) for a manual full gate"
echo
if [[ "${PUSHER_BYPASS_OK}" == "true" ]]; then
  echo "Bots keep direct-push access via boxlore-master-pusher Integration bypass."
  exit 0
else
  echo "WARNING: bot direct-push via Integration bypass is NOT configured." >&2
  exit 2
fi
