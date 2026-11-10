# Nested transaction journal verification

`NestedTransactionJournalTest` uses isolated PostgreSQL containers and the
production JDBC datasource factory with one ordinary connection. It exercises
the real `EmodelTwoPhaseCommitCoordinatorRepo` and `DbRecordRefService`.

The tests verify that journal preparation and commit-decision writes can complete
while a parent holds the only ordinary connection. The durable decision survives
the parent's rollback. Record ID mappings also survive that rollback and remain
readable after invalidating the mapping cache.

Recovery cases close the original XA connections after preparing one or both
branches, retain the real database journal, and invoke the recovery loop through
a fresh transaction manager without an in-memory participant. They cover a
PREPARING decision, a COMMITTING decision, and a lost reply after the first branch
has committed or rolled back. Assertions check final rows, absence of prepared
branches, and journal removal. These are connection-loss/fresh-manager simulations,
not JVM termination or a network fault injector.

These are bounded integration tests: one base connection, at most two prepared
branches, and no sustained request loop or concurrent client load. They remain in
the default suite. The larger pool concurrency scenario in webapp requires the
explicit `ecos.tests.nestedPool.load=true` system property.

Two coordinator-path regressions run a nested rollback or two-phase commit while
an outer transaction keeps the sole base connection. They require synchronous
journal cleanup to finish without pool-acquisition errors and check the durable
rows and journal state before the outer transaction completes. Completion status
alone cannot reset ancestry: suspended ancestors and earlier completion callbacks
may still retain connections.

The fixture registers cleanup as each resource is acquired, attempts every cleanup
in reverse order, and restores the previous transaction manager even after setup
or cleanup fails. A separate bounded test checks preservation of the original
exception, suppressed cleanup errors, datasource closure and manager restoration.

The parent `ecos-webapp-spring-hibernate-parent:3.39.0` supplies the released
`ecos-txn-lib:1.8.0` and `commons-pool2:2.12.1_citeck`. No local snapshot
installation is required. The journal implementation and its persistent format
are unchanged by this dependency update.

Run the class with `mvn -Dtest=NestedTransactionJournalTest test` on JDK 25
with Docker available. Run the complete build with `mvn clean verify`.

Separate acceptance in COREDEV-660 covers real HTTP loss, JVM termination,
old/new version combinations, and full Spring model startup on the local release
platform with ZooKeeper discovery and automatic background recovery. Those
operational results are recorded in the task; they are not assertions made by
this test class or a substitute for application-specific business regression.
