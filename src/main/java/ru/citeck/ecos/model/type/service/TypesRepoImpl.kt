package ru.citeck.ecos.model.type.service

import mu.KotlinLogging
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao
import ru.citeck.ecos.records3.RecordsServiceFactory
import java.util.concurrent.atomic.AtomicBoolean

@Component
class TypesRepoImpl(
    private val typeService: TypeService,
    private val localAppService: LocalAppService,
    private val recordsServiceFactory: RecordsServiceFactory
) : TypesRepo {

    companion object {
        private val log = KotlinLogging.logger {}

        private val BASE_TYPE_REF = TypeUtils.getTypeRef("base")
    }

    private val typesFromClasspath: Map<String, TypeInfo> by lazy {
        evalTypesFromClasspath()
    }

    private val typesSyncDao = RemoteSyncRecordsDao("rtype", TypeInfoAtts::class.java)
    private val syncInitialized = AtomicBoolean()

    override fun getChildren(typeRef: RecordRef): List<RecordRef> {
        return typeService.getChildren(typeRef.id).map { TypeUtils.getTypeRef(it) }
    }

    override fun getTypeInfo(typeRef: RecordRef): TypeInfo? {
        initSync()
        if (typeRef.id == "type") {
            return typesFromClasspath["type"]
        }
        val type = typesSyncDao.getRecord(typeRef.id).orElse(TypeInfoAtts.EMPTY)
        if (type.id.isNullOrBlank()) {
            return typesFromClasspath[typeRef.id]
        }
        return TypeInfo.create {
            withId(type.id)
            withName(type.name ?: MLText.EMPTY)
            withDispNameTemplate(type.dispNameTemplate)
            withParentRef(type.parentRef?.withSourceId("type") ?: BASE_TYPE_REF)
            withModel(type.model)
            withNumTemplateRef(type.numTemplateRef)
        }
    }

    private fun evalTypesFromClasspath(): Map<String, TypeInfo> {
        val artifacts = localAppService.readStaticLocalArtifacts(
            "model/type",
            "json",
            ObjectData.create()
        )
        val result = HashMap<String, TypeInfo>()
        for (artifact in artifacts) {
            if (artifact !is ObjectData) {
                continue
            }
            val id = artifact.get("id").asText()
            if (id.isBlank()) {
                continue
            }
            result[id] = TypeInfo.create {
                withId(id)
                withName(artifact.get("name").getAs(MLText::class.java) ?: MLText.EMPTY)
                withSourceId(artifact.get("sourceId").asText())
                withDispNameTemplate(artifact.get("name").getAs(MLText::class.java) ?: MLText.EMPTY)
                withParentRef(artifact.get("parentRef").getAs(RecordRef::class.java) ?: BASE_TYPE_REF)
                withNumTemplateRef(artifact.get("numTemplateRef").getAs(RecordRef::class.java))
                withModel(artifact.get("model").getAs(TypeModelDef::class.java))
            }
        }
        log.info { "Found types from classpath: ${result.size}" }
        return result
    }

    private fun initSync() {
        if (syncInitialized.compareAndSet(false, true)) {
            typesSyncDao.setRecordsServiceFactory(recordsServiceFactory)
            recordsServiceFactory.jobExecutor.addSystemJob(typesSyncDao.jobs[0])
        }
    }

    @EventListener
    fun onServicesInitialized(event: ContextRefreshedEvent) {
        initSync()
    }

    data class TypeInfoAtts(
        val id: String?,
        val name: MLText?,
        val parentRef: RecordRef?,
        val dispNameTemplate: MLText?,
        val numTemplateRef: RecordRef?,
        val model: TypeModelDef?
    ) {
        companion object {
            val EMPTY = TypeInfoAtts(
                null,
                null,
                null,
                null,
                null,
                null
            )
        }
    }
}
