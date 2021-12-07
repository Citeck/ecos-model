package ru.citeck.ecos.model.type.service

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService

@Component
class TypesRepoImpl(
    private val typeService: TypeService,
    private val recordsService: RecordsService,
    private val localAppService: LocalAppService
) : TypesRepo {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val typesFromClasspath: Map<String, TypeInfo> by lazy { evalTypesFromClasspath() }

    override fun getChildren(typeRef: RecordRef): List<RecordRef> {
        return typeService.getChildren(typeRef.id).map { TypeUtils.getTypeRef(it) }
    }

    override fun getTypeInfo(typeRef: RecordRef): TypeInfo? {
        if (typeRef.id == "type") {
            return typesFromClasspath["type"]
        }
        val type = recordsService.getAtts(typeRef.withSourceId("rtype"), TypeInfoAtts::class.java)
        if (type.id.isNullOrBlank()) {
            return typesFromClasspath[typeRef.id]
        }
        return TypeInfo.create {
            withId(type.id)
            withName(type.name ?: MLText.EMPTY)
            withDispNameTemplate(type.dispNameTemplate)
            withParentRef(type.parentRef)
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
            val baseParentRef = TypeUtils.getTypeRef("base")
            result[id] = TypeInfo.create {
                withId(id)
                withName(artifact.get("name").getAs(MLText::class.java) ?: MLText.EMPTY)
                withSourceId(artifact.get("sourceId").asText())
                withDispNameTemplate(artifact.get("name").getAs(MLText::class.java) ?: MLText.EMPTY)
                withParentRef(artifact.get("parentRef").getAs(RecordRef::class.java) ?: baseParentRef)
                withNumTemplateRef(artifact.get("numTemplateRef").getAs(RecordRef::class.java))
                withModel(artifact.get("model").getAs(TypeModelDef::class.java))
            }
        }
        log.info { "Found types from classpath: ${result.size}" }
        return result
    }

    data class TypeInfoAtts(
        val id: String?,
        val name: MLText?,
        val parentRef: RecordRef?,
        val dispNameTemplate: MLText?,
        val numTemplateRef: RecordRef?,
        val model: TypeModelDef?
    )
}
