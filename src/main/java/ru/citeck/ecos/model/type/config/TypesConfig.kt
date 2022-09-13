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
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsDao
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.service.utils.EModelTypeUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import javax.annotation.PostConstruct

@Configuration
class TypesConfig {

    companion object {
        private val log = KotlinLogging.logger {}

        private val EMODEL_SOURCE_ID_PREFIX = EcosModelApp.NAME + EntityRef.APP_NAME_DELIMITER
    }

    private var dbDomainFactory: DbDomainFactory? = null
    private lateinit var recordsService: RecordsService
    private var typesRegistry: EcosTypesRegistry? = null

    @PostConstruct
    fun init() {

        val dbDomainFactory = dbDomainFactory ?: return
        val typesRegistry = typesRegistry ?: return

        typesRegistry.getAllValues().values.forEach { typeDef ->
            if (getEmodelSrcId(typeDef.entity).isNotBlank()) {
                recordsService.register(createRecordsDao(dbDomainFactory, typeDef.entity))
            }
        }

        typesRegistry.listenEvents { _, before, after ->

            val emodelSrcIdBefore = getEmodelSrcId(before)
            val emodelSrcIdAfter = getEmodelSrcId(after)

            if (emodelSrcIdBefore.isNotBlank() && (after == null || emodelSrcIdBefore != emodelSrcIdAfter)) {
                log.info { "Unregister records DAO with sourceId '$emodelSrcIdBefore'" }
                recordsService.unregister(emodelSrcIdBefore)
            }
            if (after != null && emodelSrcIdAfter.isNotBlank() && (emodelSrcIdBefore != emodelSrcIdAfter)) {
                recordsService.register(createRecordsDao(dbDomainFactory, after))
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

        log.info { "Create new Records DAO for type '${typeDef.id}' with sourceId: '${typeDef.sourceId}'" }

        val tableId = EModelTypeUtils.getEmodelSourceTableId(typeDef.id)
        val sourceId = getEmodelSrcId(typeDef)
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

    private fun getEmodelSrcId(typeDef: TypeDef?): String {
        if (typeDef == null) {
            return ""
        }
        return if (typeDef.storageType == EModelTypeUtils.STORAGE_TYPE_EMODEL) {
            if (typeDef.sourceId.startsWith(EMODEL_SOURCE_ID_PREFIX)) {
                return typeDef.sourceId.substring(EMODEL_SOURCE_ID_PREFIX.length)
            } else {
                return EModelTypeUtils.getEmodelSourceId(typeDef.id)
            }
        } else {
            ""
        }
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
}
