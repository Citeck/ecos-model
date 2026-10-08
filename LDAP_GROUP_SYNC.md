# LDAP group synchronization follow-up (COREDEV-585)

The coordinated LDAP implementation is provided by `ecos-model-enterprise`.
See `ecos-model-enterprise/docs/ldap-groups.md` in the Solution for configuration
and the supported LDAP membership semantics.

`groups.allowDeletions` is independent of the user `allowDeletions` setting.
The default `false` retains groups missing from a complete LDAP read and retires
their LDAP memberships. `true` physically deletes owned groups missing from a
complete full read; differential reads never delete groups. This also removes
manual memberships involving the deleted group. Review permission/process
references before enabling deletion. The model context rejects deletion of local,
foreign-owned and system groups, and retains the existing user-disable behavior.

Missing or unsupported members are skipped with WARN. LDAP-managed groups manually
nested in administrator groups continue synchronizing. Direct system-group import
and cycle creation remain prohibited. Users from every page of the same coordinated
run are committed before groups start; a late user-page failure prevents group
processing, and a retry processes users first. There is no retry queue for members
of unchanged groups: a later group read is required to pick up a skipped member.

These changes require deploying the matching enterprise library and model context.
Both develop (2026.3) and hotfix `2.39.18` (2026.2) use the released enterprise
`1.7.1`. The hotfix is based on the released model `2.39.17`.
The Groups journal inherits actions from the authority-group type. This type now
includes the standard `delete` action alongside `edit-ext-users-info`. The delete
action uses the existing Write-permission evaluator and the DAO still requires
system/admin rights. Removing a group manually does not remove it from LDAP; a
subsequent full group read can recreate it.

No release or deployment is implied by this document.


## Verification

The earlier develop clean `verify` completed 394 tests with zero failures/errors
and seven existing skips. That run included unrelated workspace permission work
in the shared checkout. The isolated develop verification against the published
enterprise `1.7.1` completed 376 tests with zero failures/errors and seven existing
skips. The isolated hotfix `2.39.18-SNAPSHOT` clean `verify` against the same
published library completed 242 tests with zero failures/errors and seven existing
skips. Both checks used the production library without a license bypass. The
authsync tests specifically verify
actual group deletion and association cleanup in PostgreSQL, plus protection of
local, foreign-owned and system groups. The coordinated LDAP tests cover the last
user page and failure/retry ordering before groups.

The user authorized a license bypass exclusively for isolated test builds under
Solution `.tmp/ldap-followup`; production Enterprise sources retain license checks.
The model verification artifacts contain explicitly named `LOCAL-TEST` Enterprise
libraries and must not be released. A browser screenshot of the row delete action
is saved at `.tmp/ldap-followup/groups-delete-action.png`. The initial UI check attached this action. The subsequent live verification is
recorded below.


## Live verification (2026-10-08)

The changed implementation was tested on the existing live OpenLDAP corporate
fixture: 5,000 users, 363 groups, 127 OUs. Full/differential synchronization, a
5,000-member group delta, manual edits, skipped invalid members, administrator
ancestry, cycle rollback/recovery and both deletion modes passed. A new user at
LDAP offset 5,000 (page 51) received the group on the same run.

After removing temporary records, all 5,841 ECOS authorities and 26,547 provenance
edges matched the fresh backup; LDAP attribute sets also matched. The original
runtime image/config and disabled synchronization were restored. The test image
was local and unpublished. Evidence: Solution `.tmp/ldap-followup/live/result.json`.
AD-specific ranges/computers were simulated in tests; the live server was OpenLDAP.


## Published-library verification (2026-10-08)

The enterprise `1.7.1` artifact was downloaded from Nexus; all 55 LDAP class files
match the prepared library build. Clean checks above ran in isolated worktrees,
excluding the uncommitted workspace permission changes. Initial parallel attempts
failed because RootlessKit could not bind test container ports; sequential clean
runs passed. Evidence is in Solution `.tmp/ldap-followup/model-publish/`: the
`develop-verify-retry.log` and `hotfix-verify-retry.log` files. Formatting changed
zero files, and both diffs passed `git diff --check`. A fresh read-only journal
screenshot is `groups-journal.png`; the earlier delete-icon screenshot remains
`.tmp/ldap-followup/groups-delete-action.png`. This phase did not redeploy model.
