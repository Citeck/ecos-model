package ru.citeck.ecos.model.domain.content.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsServiceFactory
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

        private val CONTENT_STORAGE_TYPE = ModelUtils.getTypeRef("content-storage")

        private val LOCAL_STORAGE_RECORD = StorageRecord(
            EntityRef.create(AppName.EMODEL, ID, "LOCAL"),
            MLText(
                I18nContext.RUSSIAN to "Локальное хранишлище в БД",
                I18nContext.ENGLISH to "Local DB storage"
            )
        )
        private val DEFAULT_STORAGE_RECORD = StorageRecord(
            EntityRef.create(AppName.EMODEL, ID, "DEFAULT"),
            MLText(
                I18nContext.RUSSIAN to "Хранишлище по умолчанию",
                I18nContext.ENGLISH to "Default storage"
            )
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
        allRecords.add(LOCAL_STORAGE_RECORD)
        allRecords.add(DEFAULT_STORAGE_RECORD)

        getStorageSourceIds().forEach {
            if (remoteWebAppsApi.isAppAvailable(it.substringBefore('/'))) {
                val query = recsQuery.copy().withSourceId(it).build()
                allRecords.addAll(recordsService.query(query, StorageRecord::class.java).getRecords())
            }
        }

        return allRecords
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
        val name: MLText
    )
}
