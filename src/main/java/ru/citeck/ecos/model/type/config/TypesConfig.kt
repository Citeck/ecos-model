package ru.citeck.ecos.model.type.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.doclib.api.records.DocLibRecordId
import ru.citeck.ecos.model.domain.doclib.api.records.DocLibRecords
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver.TypeStorageDef
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.model.type.service.utils.EModelTypeUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry

@Configuration
class TypesConfig(
    val emodelTypeUtils: EModelTypeUtils,
    val typeDefResolver: TypeDefResolver,
    val rawTypesProvider: TypesProvider
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private var dbDomainFactory: DbDomainFactory? = null
    private lateinit var recordsService: RecordsService
    private var typesRegistry: EcosTypesRegistry? = null
    private lateinit var typesService: TypesService

    @PostConstruct
    fun init() {

        val dbDomainFactory = dbDomainFactory ?: return
        val typesRegistry = typesRegistry ?: return

        typesRegistry.getAllValues().values.forEach { typeDef ->
            if (emodelTypeUtils.getEmodelSourceId(typeDef.entity).isNotBlank() &&
                !typeDef.entity.isInDeletedWorkspace()
            ) {
                recordsService.register(createRecordsDao(dbDomainFactory, typeDef.entity, typesRegistry))
            }
        }

        typesService.addListener(-100f) { before, after ->
            if (after == null) {
                return@addListener
            }
            // A type that resolves to ECOS_MODEL storage gets an auto-generated DbRecordsDao under
            // its emodel source id (whether ECOS_MODEL was declared explicitly or derived through
            // the parents hierarchy). The raw def doesn't carry the sourceId derived from
            // id/workspace, so it's computed by resolving the def the same way the registry does.
            val storageAfter = resolveStorage(after) ?: return@addListener
            val emodelSrcIdAfter = storageAfter.emodelSourceId()
            if (emodelSrcIdAfter.isEmpty() ||
                emodelSrcIdAfter == resolveStorage(before).emodelSourceId() ||
                recordsService.getRecordsDao(emodelSrcIdAfter) == null
            ) {
                return@addListener
            }
            // The conflict is not new if the type already known to the registry implies the same
            // source id: on a fresh database the DAO is registered from the registry before
            // DeployCoreTypesPatch saves the type into the repo.
            val registryDef = typesRegistry.getValue(storageAfter.id)
            if (resolveStorage(registryDef).emodelSourceId() != emodelSrcIdAfter) {
                error(
                    "Records DAO with source id '$emodelSrcIdAfter' already registered. " +
                        "Please change type id or enter custom source id in sourceId attribute"
                )
            }
        }

        typesRegistry.listenEvents { _, before, after ->

            val emodelSrcIdBefore = emodelTypeUtils.getEmodelSourceId(before)
            val emodelSrcIdAfter = emodelTypeUtils.getEmodelSourceId(after)

            if (emodelSrcIdBefore.isNotBlank() && (after == null || emodelSrcIdBefore != emodelSrcIdAfter)) {
                log.info { "Unregister records DAO with sourceId '$emodelSrcIdBefore'" }
                recordsService.unregister(emodelSrcIdBefore)
            }
            if (after != null && emodelSrcIdAfter.isNotBlank() && (emodelSrcIdBefore != emodelSrcIdAfter)) {
                if (after.isInDeletedWorkspace()) {
                    log.info { "Skip records DAO registration for type '${after.id}': workspace is deleted" }
                } else if (recordsService.getRecordsDao(emodelSrcIdAfter) != null) {
                    error(
                        "Type with SourceId '$emodelSrcIdAfter' already registered. " +
                            "Please, change type ID or enter your custom sourceId"
                    )
                } else {
                    recordsService.register(createRecordsDao(dbDomainFactory, after, typesRegistry))
                }
            }
        }
    }

    private fun resolveStorage(typeDef: TypeDef?): TypeStorageDef? {
        typeDef ?: return null
        return typeDefResolver.resolveStorageTypeAndSourceId(typeDef, rawTypesProvider)
    }

    private fun TypeStorageDef?.emodelSourceId(): String {
        this ?: return ""
        return emodelTypeUtils.getEmodelSourceId(id, workspace, storageType, sourceId)
    }

    private fun createRecordsDao(
        dbDomainFactory: DbDomainFactory,
        typeDef: TypeDef,
        typesRegistry: EcosTypesRegistry
    ): RecordsDao {

        val sourceId = emodelTypeUtils.getEmodelSourceId(typeDef)

        log.info { "Create new Records DAO for type '${typeDef.id}' with sourceId: '$sourceId'" }

        val tableId = emodelTypeUtils.getEmodelSourceTableId(typeDef.id, typeDef.workspace)
        if (tableId.isEmpty() || sourceId.isEmpty()) {
            error(
                "Table ID or Source ID is empty. " +
                    "TableId: '$tableId' Source ID: '$sourceId'. " +
                    "RecordsDAO can't be created."
            )
        }

        val typeRef = ModelUtils.getTypeRef(typeDef.id)
        val daoBuilder = DbRecordsDaoConfig.create()
        daoBuilder.withAllowRecordIdUpdate(true)
        daoBuilder.withId(sourceId)
        daoBuilder.withTypeRef(typeRef)
        if (typesRegistry.isSubType(typeRef.getLocalId(), DocLibRecords.DEFAULT_DIR_TYPE_ID)) {
            daoBuilder.withAllowedRecordIdPattern(DocLibRecordId.VALID_ID_PATTERN)
        }

        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(daoBuilder.build())
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable(tableId)
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema("ecos_data").build()

        return recordsDao
    }

    @Autowired(required = false)
    fun setDbDomainFactory(dbDomainFactory: DbDomainFactory) {
        this.dbDomainFactory = dbDomainFactory
    }

    @Autowired(required = false)
    fun setTypesRegistry(typesRegistry: EcosTypesRegistry) {
        this.typesRegistry = typesRegistry
    }

    @Autowired(required = false)
    fun setRecordsService(recordsService: RecordsService) {
        this.recordsService = recordsService
    }

    @Autowired
    fun setTypesService(typesService: TypesService) {
        this.typesService = typesService
    }

    private fun TypeDef.isInDeletedWorkspace(): Boolean {
        return id.startsWith(EmodelWorkspaceService.DELETED_WS_SYS_ID_PREFIX)
    }
}
