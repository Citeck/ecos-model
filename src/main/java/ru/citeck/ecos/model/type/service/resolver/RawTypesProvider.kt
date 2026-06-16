package ru.citeck.ecos.model.type.service.resolver

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.lib.workspace.convertToIdInWsSafe
import ru.citeck.ecos.model.lib.workspace.convertToStrIdSafe
import ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

/**
 * Provides raw (unresolved) type defs from the types repo.
 */
@Component
class RawTypesProvider(
    private val typesService: TypesService,
    private val localAppService: LocalAppService,
    private val workspaceService: WorkspaceService? = null
) : TypesProvider {

    private val predefinedTypesInfo: Map<String, PredefinedTypeInfo> by lazy {
        loadClasspathTypes().associate { it.id to PredefinedTypeInfo(it) }
    }

    private fun loadClasspathTypes(): List<TypeDef> {
        val typeArtifacts = localAppService.readStaticLocalArtifacts(
            TypeArtifactHandler.TYPE,
            "json",
            ObjectData.create()
        )
        val result = ArrayList<TypeDef>()
        for (typeArtifact in typeArtifacts) {
            if (typeArtifact !is ObjectData) {
                continue
            }
            val typeDef = typeArtifact.getAs(TypeDef::class.java) ?: continue
            if (typeDef.id.isNotBlank()) {
                result.add(typeDef)
            }
        }
        return result
    }

    private fun isAllAttsFromMapExistsInList(
        attsMap: Map<String, AttributeDef>,
        attsList: List<AttributeDef>
    ): Boolean {
        if (attsMap.isEmpty()) {
            return true
        }
        if (attsList.size < attsMap.size) {
            return false
        }
        var notFoundCount = attsMap.size
        for (att in attsList) {
            if (attsMap.containsKey(att.id)) {
                if (--notFoundCount == 0) {
                    break
                }
            }
        }
        return notFoundCount == 0
    }

    override fun get(id: String): TypeDef? {

        val typeFromRepo = typesService.getByIdOrNull(workspaceService.convertToIdInWsSafe(id)) ?: return null
        val predefinedType = predefinedTypesInfo[id] ?: return typeFromRepo

        // Add missing predefined attributes to the type model loaded from the database.
        // This is needed to protect against potential bugs when a new attribute is added
        // in artifacts/model/type/... but the database still contains an older version
        // of the type without this attribute.
        // The updated type will eventually be deployed, but until that happens we apply this workaround.
        val repoAtts = typeFromRepo.model.attributes
        val repoSysAtts = typeFromRepo.model.systemAttributes

        val foundMissingAtts = !isAllAttsFromMapExistsInList(predefinedType.attributesById, repoAtts)
        val foundMissingSysAtts = !isAllAttsFromMapExistsInList(predefinedType.sysAttsById, repoSysAtts)

        if (!foundMissingAtts && !foundMissingSysAtts) {
            return typeFromRepo
        }

        val newModel = typeFromRepo.model.copy()

        if (foundMissingAtts) {
            val newAtts = ArrayList(repoAtts)
            for (attribute in predefinedType.attributesById.values) {
                if (repoAtts.find { it.id == attribute.id } == null) {
                    newAtts.add(attribute)
                }
            }
            newModel.withAttributes(newAtts)
        }
        if (foundMissingSysAtts) {
            val newSysAtts = ArrayList(repoSysAtts)
            for (attribute in predefinedType.sysAttsById.values) {
                if (repoSysAtts.find { it.id == attribute.id } == null) {
                    newSysAtts.add(attribute)
                }
            }
            newModel.withSystemAttributes(newSysAtts)
        }

        return typeFromRepo.copy()
            .withModel(newModel.build())
            .build()
    }

    override fun getChildren(typeId: String): List<String> {
        return typesService.getChildren(workspaceService.convertToIdInWsSafe(typeId)).map {
            workspaceService.convertToStrIdSafe(it)
        }
    }

    private class PredefinedTypeInfo(
        val typeDef: TypeDef
    ) {
        val attributesById: Map<String, AttributeDef> = typeDef.model.attributes.associateBy { it.id }
        val sysAttsById: Map<String, AttributeDef> = typeDef.model.systemAttributes.associateBy { it.id }
    }
}
