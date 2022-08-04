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
import ru.citeck.ecos.model.type.service.utils.EcosModelTypeUtils
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

    @PostConstruct
    fun init() {
        val dbDomainFactory = dbDomainFactory ?: return
        val typesRegistry = typesRegistry ?: return

        typesRegistry.getAllValues().values.forEach { typeDef ->
            if (typeDef.entity.sourceType == EcosModelTypeUtils.SOURCE_TYPE_EMODEL &&
                typeDef.entity.sourceId.isNotBlank()
            ) {

                recordsService.register(createRecordsDao(dbDomainFactory, typeDef.entity))
            }
        }

        typesRegistry.listenEvents { _, before, after ->

            val sourceIdBefore = before?.sourceId ?: ""

            val isSourceTypeBeforeEmodel = before?.sourceType == EcosModelTypeUtils.SOURCE_TYPE_EMODEL
            val isSourceTypeAfterEmodel = after?.sourceType == EcosModelTypeUtils.SOURCE_TYPE_EMODEL

            if (isSourceTypeBeforeEmodel &&
                sourceIdBefore.isNotBlank() &&
                (after == null || sourceIdBefore != after.sourceId)
            ) {
                log.info { "Unregister records DAO with sourceId '$sourceIdBefore'" }
                recordsService.unregister(sourceIdBefore)
            }
            if (isSourceTypeAfterEmodel && after != null &&
                (before?.sourceId != after.sourceId || before.sourceType != after.sourceType)
            ) {
                val sourceId = after.sourceId
                val parentId = typesRegistry.getValue(after.parentRef.id)?.sourceId
                if (sourceId != parentId) {
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

        log.info { "Create new Records DAO for type '${typeDef.id}' with sourceId: '${typeDef.sourceId}'" }

        val tableId = EcosModelTypeUtils.getEmodelSourceTableId(typeDef.id)
        val localSourceId = typeDef.sourceId.substringAfter('/')

        val typeRef = TypeUtils.getTypeRef(typeDef.id)
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(localSourceId)
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
}
