package ru.citeck.ecos.model.type.config

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsDao
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.model.type.service.utils.EModelTypeUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import javax.annotation.PostConstruct

@Configuration
class TypesConfig {

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
            if (EModelTypeUtils.getEmodelSourceId(typeDef.entity).isNotBlank()) {
                recordsService.register(createRecordsDao(dbDomainFactory, typeDef.entity))
            }
        }

        typesService.addListener(-100f) { before, after ->
            val emodelSrcIdBefore = EModelTypeUtils.getEmodelSourceId(before)
            val emodelSrcIdAfter = EModelTypeUtils.getEmodelSourceId(after)
            if (emodelSrcIdAfter.isNotEmpty() &&
                emodelSrcIdAfter != emodelSrcIdBefore &&
                recordsService.getRecordsDao(emodelSrcIdAfter) != null
            ) {

                error(
                    "Records DAO with source id '$emodelSrcIdAfter' already registered. " +
                        "Please change type id or enter custom source id in sourceId attribute"
                )
            }
        }

        typesRegistry.listenEvents { _, before, after ->

            val emodelSrcIdBefore = EModelTypeUtils.getEmodelSourceId(before)
            val emodelSrcIdAfter = EModelTypeUtils.getEmodelSourceId(after)

            if (emodelSrcIdBefore.isNotBlank() && (after == null || emodelSrcIdBefore != emodelSrcIdAfter)) {
                log.info { "Unregister records DAO with sourceId '$emodelSrcIdBefore'" }
                recordsService.unregister(emodelSrcIdBefore)
            }
            if (after != null && emodelSrcIdAfter.isNotBlank() && (emodelSrcIdBefore != emodelSrcIdAfter)) {
                if (recordsService.getRecordsDao(emodelSrcIdAfter) != null) {
                    error(
                        "Type with SourceId '$emodelSrcIdAfter' already registered. " +
                            "Please, change type ID or enter your custom sourceId"
                    )
                } else {
                    recordsService.register(createRecordsDao(dbDomainFactory, after))
                }
            }
        }
    }

    @Bean("typesMutMetaMixin")
    fun typesMutMetaMixin(
        typesRepoRecordsDao: TypesRepoRecordsDao,
        typeConverter: TypeConverter
    ): MutMetaMixin {
        val mixin = MutMetaMixin("emodel/type")
        typesRepoRecordsDao.addAttributesMixin(mixin)
        typeConverter.mutMetaMixin = mixin
        return mixin
    }

    private fun createRecordsDao(dbDomainFactory: DbDomainFactory, typeDef: TypeDef): RecordsDao {

        val sourceId = EModelTypeUtils.getEmodelSourceId(typeDef)

        log.info { "Create new Records DAO for type '${typeDef.id}' with sourceId: '$sourceId'" }

        val tableId = EModelTypeUtils.getEmodelSourceTableId(typeDef.id)
        if (tableId.isEmpty() || sourceId.isEmpty()) {
            error(
                "Table ID or Source ID is empty. " +
                    "TableId: '$tableId' Source ID: '$sourceId'. " +
                    "RecordsDAO can't be created."
            )
        }

        val typeRef = TypeUtils.getTypeRef(typeDef.id)
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(sourceId)
                        withTypeRef(typeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withAuthEnabled(true)
                        withTableRef(DbTableRef("ecos_data", tableId))
                        withTransactional(true)
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).build()

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
}
