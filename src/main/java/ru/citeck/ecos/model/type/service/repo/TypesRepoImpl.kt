package ru.citeck.ecos.model.type.service.repo

import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.service.TypeService
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao
import ru.citeck.ecos.records3.RecordsServiceFactory
import java.util.concurrent.atomic.AtomicBoolean

@Component
class TypesRepoImpl(
    private val env: Environment,
    private val typeService: TypeService,
    private val recordsServiceFactory: RecordsServiceFactory,
    localAppService: LocalAppService
) : TypesRepo {

    companion object {
        val BASE_TYPE_REF = TypeUtils.getTypeRef("base")
    }

    private val typesSyncDao = RemoteSyncRecordsDao("rtype", TypeInfoAtts::class.java)
    private val syncInitialized = AtomicBoolean()

    private val isTestEnv: Boolean by lazy { env.acceptsProfiles("test") }

    private val classpathTypes = TypesClasspathRepo(localAppService)

    override fun getChildren(typeRef: RecordRef): List<RecordRef> {
        return typeService.getChildren(typeRef.id).map { TypeUtils.getTypeRef(it) }
    }

    override fun getTypeInfo(typeRef: RecordRef): TypeInfo? {
        initSync()
        if (typeRef.id == "type") {
            return classpathTypes.getTypeInfo("type")
        }
        val type = if (!isTestEnv) {
            typesSyncDao.getRecord(typeRef.id).orElse(TypeInfoAtts.EMPTY)
        } else {
            TypeInfoAtts.EMPTY
        }
        if (type.id.isNullOrBlank()) {
            return classpathTypes.getTypeInfo(typeRef.id)
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

    private fun initSync() {
        if (!isTestEnv && syncInitialized.compareAndSet(false, true)) {
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
