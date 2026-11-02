# Workspace lookup cache and connection pool ordering

`CustomWorkspaceApi` caches user workspaces, manager membership and manager references for
30 seconds, with at most 1,000 entries per cache. These lookups read Records API and can
need a JDBC connection.

A synchronous Caffeine loading cache holds its key lock while loading. A request without
a connection can own that lock while waiting for a pool connection. A transaction holding
the final connection can then wait for the same cache key. Neither request can finish.
Increasing the pool size postpones this interleaving; it does not remove the dependency.

`WorkspaceLookupCache` reads and publishes cached values under a short monitor, but loads
outside both the monitor and Caffeine computations. Concurrent misses deliberately load
independently using each caller's existing context and transaction. No background executor,
extra transaction, stale-value fallback or permission bypass is introduced. Cold concurrent
lookups can consequently perform additional database reads.

Every invalidation advances a generation, including invalidation of a key which is absent
because it is still loading. A load from an earlier generation can return to its existing
caller, but cannot restore shared cached rights after invalidation. Selective invalidation
keeps other completed entries; conservatively, all in-flight publications in that cache are
suppressed on any invalidation. Loader exceptions propagate and are not cached. Negative
manager answers and absent manager sets retain their existing caching semantics.

`CustomWorkspaceApiConcurrencyTest` arranges a one-permit connection resource and two
requests: a cache loader waiting for the permit, and its current owner querying the same
key before returning the permit. It exercises all three API lookups. The owner must finish,
then the loader must finish, and subsequent requests must succeed. This is a deterministic
resource-ordering regression with a mocked workspace service, not a replay of the original
RC6 HTTP/BPM incident. A second set of three cases uses a real PostgreSQL database and
a DBCP `BasicDataSource` with `maxTotal=1`, `autoCommit=false` and a ten-second borrow
timeout. The owner executes SQL in its existing transaction, commits and closes its
connection; the competing loader and a subsequent fresh SQL request must then succeed.
The verified database was PostgreSQL 12.6, supplied by the existing test-container defaults;
this is a minimal resource-ordering test, not a throughput benchmark.

`WorkspaceLookupCacheTest` covers invalidation during an in-flight load (single key,
predicate and all entries), exception retry, negative caching, selective eviction and
30-second expiry with a controlled clock. Existing workspace integration tests exercise
membership, permissions, creation, join/leave and event-driven invalidation.

The original R25-01 JVM/SQL dumps were unavailable in this solution. The code ordering
and regression establish the cache/pool cycle; they do not establish that it was the only
cause of the historical incident. Identifier-mapping and other platform caches are outside
this change. Full mixed HTTP query/mutate and BPM completion load on a deployed candidate
remains a release acceptance check.

Verification on 2026-10-29: `mvn -B clean verify` passed with 430 tests, zero failures/errors
and seven pre-existing disabled controller tests. A subsequent
`mvn -B -Dtest=CustomWorkspaceApiConcurrencyTest,WorkspaceLookupCacheTest verify` passed
all 12 focused tests, including the three added JDBC cases. The original implementation
failed all three deterministic resource-ordering cases with `TimeoutException`.
