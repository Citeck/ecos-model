package ru.citeck.ecos.model.domain.content.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.apps.EcosRemoteWebAppsApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.util.concurrent.atomic.AtomicLong

@Component
class ContentStoragesRecordsDao(
    private val typesRegistry: EcosTypesRegistry
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    companion object {

        const val ID = "content-storage"
        const val ECOS_TYPE = "content-storage"

        private val CONTENT_STORAGE_TYPE = ModelUtils.getTypeRef("content-storage")

        private val LOCAL_STORAGE_RECORD = StorageRecord(
            EntityRef.create(AppName.EMODEL, ID, "LOCAL"),
            MLText(
                I18nContext.RUSSIAN to "Локальное хранишлище в БД",
                I18nContext.ENGLISH to "Local DB storage"
            ),
            ECOS_TYPE
        )
        private val DEFAULT_STORAGE_RECORD = StorageRecord(
            EntityRef.create(AppName.EMODEL, ID, "DEFAULT"),
            MLText(
                I18nContext.RUSSIAN to "Хранишлище по умолчанию",
                I18nContext.ENGLISH to "Default storage"
            ),
            ECOS_TYPE
        )
    }

    private var storagesSourceIds: Set<String> = emptySet()
    private val storagesSourceIdsLastUpdate = AtomicLong(0L)
    private lateinit var remoteWebAppsApi: EcosRemoteWebAppsApi

    override fun getRecordAtts(recordId: String): Any? {
        return when (recordId) {
            LOCAL_STORAGE_RECORD.id.getLocalId() -> LOCAL_STORAGE_RECORD
            DEFAULT_STORAGE_RECORD.id.getLocalId() -> DEFAULT_STORAGE_RECORD
            else -> null
        }
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any {

        val allRecords = ArrayList<StorageRecord>()
        val lang = recsQuery.language
        val predicate = if (lang == PredicateService.LANGUAGE_PREDICATE || lang.isEmpty()) {
            recsQuery.getQuery(Predicate::class.java)
        } else {
            return emptyList<Any>()
        }
        if (predicateService.isMatch(LOCAL_STORAGE_RECORD, predicate)) {
            allRecords.add(LOCAL_STORAGE_RECORD)
        }
        if (predicateService.isMatch(DEFAULT_STORAGE_RECORD, predicate)) {
            allRecords.add(DEFAULT_STORAGE_RECORD)
        }
        getStorageSourceIds().forEach {
            if (remoteWebAppsApi.isAppAvailable(it.substringBefore('/'))) {
                val query = recsQuery.copy().withSourceId(it).build()
                recordsService.query(query, RemoteStorageAtts::class.java).getRecords().forEach { atts ->
                    allRecords.add(StorageRecord(atts.id, atts.name, atts.type))
                }
            }
        }
        return predicateService.filterAndSort(
            allRecords,
            Predicates.alwaysTrue(),
            recsQuery.sortBy,
            0,
            recsQuery.page.maxItems
        )
    }

    private fun getStorageSourceIds(): Set<String> {
        if (System.currentTimeMillis() - storagesSourceIdsLastUpdate.get() < 5_000) {
            return storagesSourceIds
        }
        storagesSourceIds = typesRegistry.getChildren(CONTENT_STORAGE_TYPE).mapNotNullTo(HashSet()) {
            typesRegistry.getValue(it.getLocalId())?.sourceId
        }
        storagesSourceIdsLastUpdate.set(System.currentTimeMillis())
        return storagesSourceIds
    }

    override fun getId(): String {
        return ID
    }

    override fun setRecordsServiceFactory(serviceFactory: RecordsServiceFactory) {
        super.setRecordsServiceFactory(serviceFactory)
        remoteWebAppsApi = serviceFactory.getEcosWebAppApi()!!.getRemoteWebAppsApi()
    }

    class StorageRecord(
        val id: EntityRef,
        val name: MLText,
        type: String
    ) {
        private val ecosType: String = type

        fun getEcosType(): String {
            return ecosType
        }
    }

    class RemoteStorageAtts(
        val id: EntityRef,
        val name: MLText,
        @AttName("_type?localId")
        val type: String
    )

}
