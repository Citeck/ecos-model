package ru.citeck.ecos.model.type.service.repo

import mu.KotlinLogging
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.records2.RecordRef

class TypesClasspathRepo(private val localAppService: LocalAppService) : TypesRepo {

    companion object {
        val log = KotlinLogging.logger {}
    }

    private val typesFromClasspath: Map<String, TypeInfo> by lazy {
        evalTypesFromClasspath()
    }

    override fun getChildren(typeRef: RecordRef): List<RecordRef> {
        return emptyList()
    }

    fun getTypeInfo(id: String): TypeInfo? {
        return typesFromClasspath[id]
    }

    override fun getTypeInfo(typeRef: RecordRef): TypeInfo? {
        return getTypeInfo(typeRef.id)
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
                withDispNameTemplate(artifact.get("dispNameTemplate").getAs(MLText::class.java) ?: MLText.EMPTY)
                withParentRef(artifact.get("parentRef").getAs(RecordRef::class.java) ?: TypesRepoImpl.BASE_TYPE_REF)
                withNumTemplateRef(artifact.get("numTemplateRef").getAs(RecordRef::class.java))
                withModel(artifact.get("model").getAs(TypeModelDef::class.java))
            }
        }
        val resultWithParents = HashMap<String, TypeInfo>()
        result.forEach { (id, info) ->
            resultWithParents[id] = processClasspathParents(info, result)!!
        }
        log.info { "Found types from classpath: ${resultWithParents.size}" }
        return resultWithParents
    }

    private fun processClasspathParents(typeInfo: TypeInfo?, typesConfig: MutableMap<String, TypeInfo>): TypeInfo? {

        typeInfo ?: return null

        if (typeInfo.parentRef.id.isBlank() || typeInfo.parentRef.id == TypesRepoImpl.BASE_TYPE_REF.id) {
            return typeInfo
        }

        val parentTypeInfo = processClasspathParents(typesConfig[typeInfo.parentRef.id], typesConfig) ?: return typeInfo
        val parentModel = parentTypeInfo.model

        val roles = mutableMapOf<String, RoleDef>()
        val statuses = mutableMapOf<String, StatusDef>()
        val attributes = mutableMapOf<String, AttributeDef>()
        val systemAttributes = mutableMapOf<String, AttributeDef>()

        val putAllForModel = { model: TypeModelDef ->
            roles.putAll(model.roles.associateBy { it.id })
            statuses.putAll(model.statuses.associateBy { it.id })
            attributes.putAll(model.attributes.associateBy { it.id })
            systemAttributes.putAll(model.systemAttributes.associateBy { it.id })
        }
        putAllForModel(parentModel)
        putAllForModel(typeInfo.model)

        return typeInfo.copy {
            withModel(typeInfo.model.copy {
                withRoles(roles.values.toList())
                withStatuses(statuses.values.toList())
                withAttributes(attributes.values.toList())
                withSystemAttributes(systemAttributes.values.toList())
            })
        }
    }
}
