package ru.citeck.ecos.model.domain.txn

import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.data.sql.datasource.DbDataSource
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbColumnConstraint
import ru.citeck.ecos.data.sql.dto.DbColumnType
import ru.citeck.ecos.data.sql.repo.entity.annotation.ColumnType
import ru.citeck.ecos.data.sql.repo.entity.annotation.Constraints
import ru.citeck.ecos.data.sql.repo.find.DbFindPage
import ru.citeck.ecos.data.sql.repo.find.DbFindSort
import ru.citeck.ecos.data.sql.service.DbDataService
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceImpl
import ru.citeck.ecos.model.lib.type.dto.QueryPermsPolicy
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.txn.lib.commit.RecoveryData
import ru.citeck.ecos.txn.lib.commit.TxnCommitData
import ru.citeck.ecos.txn.lib.commit.repo.TwoPhaseCommitRepo
import ru.citeck.ecos.txn.lib.commit.repo.TwoPhaseCommitStatus
import ru.citeck.ecos.txn.lib.transaction.TxnId
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService
import ru.citeck.ecos.webapp.lib.web.client.router.EcosWebRoute
import java.time.Duration
import java.time.Instant
import kotlin.math.min

@Component
class EmodelTwoPhaseCommitCoordinatorRepo : TwoPhaseCommitRepo {

    companion object {
        private val log = KotlinLogging.logger {}

        private val RECOVERY_TIME_BY_ITERATION: List<Duration> = listOf(
            *(0..5).map { Duration.ofSeconds(30) }.toTypedArray(),
            *(0..5).map { Duration.ofMinutes(1) }.toTypedArray(),
            *(0..9).map { Duration.ofMinutes(10) }.toTypedArray(),
            *(0..9).map { Duration.ofHours(1) }.toTypedArray(),
        )
    }

    private lateinit var dataService: DbDataService<TwoPcEntity>
    private lateinit var dataSource: DbDataSource
    private lateinit var webAppApi: EcosWebAppApi
    private lateinit var discoveryService: WebAppDiscoveryService

    fun init(domainFactory: DbDomainFactory, discoveryService: WebAppDiscoveryService) {

        this.discoveryService = discoveryService

        dataSource = domainFactory.dataSource
        val schemaCtx = domainFactory.getSchemaContext("public")
        dataService = DbDataServiceImpl(
            TwoPcEntity::class.java,
            DbDataServiceConfig(
                "ecos_txn_2pc_data",
                10000,
                emptyList(),
                true,
                QueryPermsPolicy.PUBLIC
            ),
            schemaCtx,
            true
        )
    }

    override fun beforePrepare(txnId: TxnId, data: TxnCommitData) {
        log.debug { "BEFORE PREPARE $txnId data: $data" }
        val appRoutes = Json.mapper.toStringNotNull(EcosWebRoute.getTxnRoutesMap())
        doInNewTxn {
            val entity = TwoPcEntity()
            entity.txnId = txnId.toString()
            entity.data = Json.mapper.toStringNotNull(data)
            entity.status = TwoPhaseCommitStatus.PREPARING.ordinal
            entity.ownerApp = discoveryService.getCurrentInstance().getRef().toString()
            entity.created = Instant.now()
            entity.modified = entity.created
            entity.recoveryTime = Instant.now().plus(RECOVERY_TIME_BY_ITERATION[0])
            entity.appRoutes = appRoutes
            entity.appsToProcess = Json.mapper.toStringNotNull(data.apps.keys)
            dataService.save(entity)
        }
    }

    override fun beforeCommit(txnId: TxnId, appsToCommit: Set<String>) {
        log.debug { "BEFORE COMMIT $txnId apps: $appsToCommit" }
        doInNewTxn {
            val entity = findRequiredEntity(txnId)
            entity.appsToProcess = Json.mapper.toStringNotNull(appsToCommit.toList())
            entity.status = TwoPhaseCommitStatus.COMMITTING.ordinal
            entity.modified = Instant.now()
            entity.recoveryTime = Instant.now().plus(RECOVERY_TIME_BY_ITERATION[0])
            dataService.save(entity)
        }
    }

    override fun beforeRollback(txnId: TxnId, appsToRollback: Set<String>) {
        log.debug { "BEFORE ROLLBACK $txnId apps: $appsToRollback" }
        doInNewTxn {
            val entity = findEntity(txnId)
            if (entity != null) {
                entity.appsToProcess = Json.mapper.toStringNotNull(appsToRollback)
                entity.status = TwoPhaseCommitStatus.ROLLING_BACK.ordinal
                entity.modified = Instant.now()
                entity.recoveryTime = Instant.now().plus(RECOVERY_TIME_BY_ITERATION[0])
                dataService.save(entity)
            }
        }
    }

    override fun afterRollback(txnId: TxnId, rolledBackApps: Set<String>, errors: Map<String, Throwable>) {
        log.debug {
            "AFTER ROLLBACK $txnId " +
                "rolledBackApps: $rolledBackApps " +
                "errors: ${errors.entries.map { it.key + "-" + it.value.message }}"
        }
        afterCommitOrRollback(txnId, rolledBackApps, errors)
    }

    override fun afterCommit(txnId: TxnId, committedApps: Set<String>, errors: Map<String, Throwable>) {
        log.debug {
            "AFTER COMMIT $txnId " +
                "committedApps: $committedApps " +
                "errors: ${errors.entries.map { it.key + "-" + it.value.message }}"
        }
        afterCommitOrRollback(txnId, committedApps, errors)
    }

    private fun afterCommitOrRollback(txnId: TxnId, processedApps: Set<String>, errors: Map<String, Throwable>) {
        doInNewTxn {
            val entity = findEntity(txnId)
            if (entity != null) {
                val remainingAppsToProc = HashSet(
                    Json.mapper.readList(entity.appsToProcess, String::class.java)
                )
                remainingAppsToProc.removeAll(processedApps)
                if (remainingAppsToProc.isEmpty()) {
                    dataService.forceDelete(Predicates.eq(TwoPcEntity.TXN_ID, txnId.toString()))
                } else {
                    entity.appsToProcess = Json.mapper.toStringNotNull(remainingAppsToProc)
                    entity.modified = Instant.now()
                    entity.errors = Json.mapper.toStringNotNull(
                        errors.entries.associate {
                            it.key to ErrorInfo.valueOf(it.value)
                        }
                    )
                    if (entity.procIteration == 0) {
                        entity.initialErrors = entity.errors
                        entity.initialErrorsTime = Instant.now()
                    }
                    entity.procIteration = entity.procIteration + 1
                    val recoveryTimeIdx = min(entity.procIteration, RECOVERY_TIME_BY_ITERATION.lastIndex)
                    entity.recoveryTime = Instant.now().plus(RECOVERY_TIME_BY_ITERATION[recoveryTimeIdx])

                    dataService.save(entity)
                }
            }
        }
    }

    override fun findDataToRecover(): RecoveryData? {
        return doInNewTxn(true) {
            val activeApps = discoveryService.getInstances(AppName.EMODEL).map { it.getRef().toString() }
            dataService.find(
                Predicates.and(
                    Predicates.lt(TwoPcEntity.RECOVERY_TIME, Instant.now()),
                    Predicates.not(Predicates.inVals(TwoPcEntity.OWNER_APP, activeApps)),
                ),
                listOf(DbFindSort(TwoPcEntity.RECOVERY_TIME, true)),
                DbFindPage.FIRST
            ).entities.firstOrNull()
        }?.asRecoveryData()
    }

    override fun getRecoveryData(txnId: TxnId): RecoveryData? {
        return findEntity(txnId)?.asRecoveryData()
    }

    private inline fun <T> doInNewTxn(readOnly: Boolean = false, crossinline action: () -> T): T {
        return TxnContext.doInNewTxn {
            dataSource.withTransaction(readOnly, requiresNew = true) {
                action.invoke()
            }
        }
    }

    private fun findRequiredEntity(txnId: TxnId): TwoPcEntity {
        return findEntity(txnId) ?: error("Transaction is not found by id $txnId")
    }

    private fun findEntity(txnId: TxnId): TwoPcEntity? {
        return dataService.find(
            Predicates.eq(TwoPcEntity.TXN_ID, txnId.toString()),
            emptyList(),
            DbFindPage.FIRST
        ).entities.firstOrNull()
    }

    @Lazy
    @Autowired
    fun setEcosWebAppApi(webAppApi: EcosWebAppApi) {
        this.webAppApi = webAppApi
    }

    class TwoPcEntity {

        companion object {
            const val TXN_ID = "__txn_id"
            const val OWNER_APP = "__owner_app"
            const val RECOVERY_TIME = "__recovery_time"
        }

        @Constraints(DbColumnConstraint.PRIMARY_KEY)
        var id: Long = -1

        @Constraints(DbColumnConstraint.UNIQUE)
        var txnId: String = ""

        @ColumnType(DbColumnType.JSON)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var data: String = "{}"

        @ColumnType(DbColumnType.JSON)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var appsToProcess: String = "[]"

        @Constraints(DbColumnConstraint.NOT_NULL)
        var procIteration: Int = 0

        @ColumnType(DbColumnType.INT)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var status: Int = TwoPhaseCommitStatus.PREPARING.ordinal

        @ColumnType(DbColumnType.DATETIME)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var created: Instant = Instant.EPOCH

        @ColumnType(DbColumnType.DATETIME)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var modified: Instant = Instant.EPOCH

        @ColumnType(DbColumnType.DATETIME)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var recoveryTime: Instant = Instant.EPOCH

        @ColumnType(DbColumnType.JSON)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var errors: String = "{}"

        @ColumnType(DbColumnType.JSON)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var initialErrors: String = "{}"

        @ColumnType(DbColumnType.DATETIME)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var initialErrorsTime: Instant = Instant.EPOCH

        @ColumnType(DbColumnType.JSON)
        @Constraints(DbColumnConstraint.NOT_NULL)
        var appRoutes: String = "{}"

        @Constraints(DbColumnConstraint.NOT_NULL)
        var ownerApp: String = ""
    }

    private fun TwoPcEntity.asRecoveryData(): RecoveryData {
        return RecoveryData(
            txnId = TxnId.valueOf(this.txnId),
            data = Json.mapper.readNotNull(this.data, TxnCommitData::class.java),
            status = TwoPhaseCommitStatus.values()[this.status],
            appsToProcess = Json.mapper.readList(this.appsToProcess, String::class.java).toSet(),
            ownerApp = this.ownerApp,
            appRoutes = Json.mapper.readMap(this.appRoutes, String::class.java, String::class.java)
        )
    }

    class ErrorInfo(
        val type: String,
        val message: String,
        val rootType: String,
        val rootMessage: String,
        val rootTrace: List<String>
    ) {
        companion object {
            fun valueOf(error: Throwable): ErrorInfo {
                val root = ExceptionUtils.getRootCause(error) ?: error
                return ErrorInfo(
                    type = error::class.java.name,
                    message = error.message ?: "",
                    rootType = root::class.java.name,
                    rootMessage = root.message ?: "",
                    rootTrace = root.stackTrace.take(10).map { it.toString() }
                )
            }
        }
    }
}
