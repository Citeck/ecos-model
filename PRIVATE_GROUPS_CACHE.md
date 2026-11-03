# Private-group cache and JDBC pool ordering

`PrivateGroupsService` supplies the private-group set used by person and group query
visibility checks. Administrators, system users and actors with the unified-private-group
authority bypass this filter. Other actors still use the existing visibility predicates.

The cache miss must not perform a Records query while holding a shared monitor. A loader
waiting for a pool connection can otherwise hold the monitor needed by a transaction that
already owns the final connection. Increasing the pool size does not remove that cycle.

The cache uses an atomic state snapshot. A miss loads in the calling context without a
cache lock; concurrent misses can issue additional queries. The result is published with
compare-and-set against the original state. The minute timer and existing filtered
authority-group change listener replace that state, including while a miss is loading.
An older load may finish for its existing caller but cannot repopulate shared rights after
invalidation. A successful competing load also cannot be overwritten by an earlier miss.
Empty results are cached; failed queries propagate and are retried on a later miss.
The existing system-authenticated group lookup and its transaction behavior are preserved.

## Verification

`PrivateGroupsServiceTest` exercises the monitor/pool cycle using real PostgreSQL and a
DBCP pool with one connection and auto-commit disabled. The current transaction owner
must finish and return its connection while another loader waits; that loader and a fresh
SQL query must then succeed. Timer and event callbacks are also tested for completed and
in-flight invalidation, empty caching and exception retry. On the original 2.44.1 source,
the JDBC case times out and both in-flight invalidation cases return stale cached rights.

`PrivateGroupsVisibilityTest` uses the actual Records/DB pipeline for outside users,
members of two private groups, unified-private-group actors and administrators. It changes
a group's privacy flag and verifies the actual after-commit listener and refreshed visibility.
Its mixed workload uses 40 concurrent clients: 50% workspace queries, 25% person queries
and 25% comment create/delete operations on the caller's own person record. Privacy changes
run concurrently. Post-load user/admin queries and the absence of long idle-in-transaction
sessions are checked. The default duration is 15 seconds; run the incident-duration case with:

```sh
mvn -B -Dkotlin.compiler.daemon=false \
  -Dtest=PrivateGroupsServiceTest,PrivateGroupsVisibilityTest \
  -Decos.private-groups.load.seconds=180 verify
```

This workload calls Records inside real transactions against isolated test databases. It
covers the server path but is not an HTTP replay against the Launcher acceptance namespace.
The submitted 2026-11-03 incident report and deployment acceptance belong to COREDEV-660's
task record. No release or deployment follows from these tests.
