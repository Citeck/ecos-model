package ru.citeck.ecos.model.type.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler
import ru.citeck.ecos.model.type.service.TypeId
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosLocalPatch
import java.util.concurrent.Callable

@Component
// date should be before CreateDefaultGroupsAndPersonsPatch date
@EcosLocalPatch("deploy-core-types", "2024-12-19T00:00:00Z")
class DeployCoreTypesPatch(
    private val typesService: TypesService,
    private val localAppService: LocalAppService
) : Callable<List<String>> {

    companion object {
        private const val BASE_TYPE_ID = "base"

        private val log = KotlinLogging.logger {}
    }

    @Transactional
    override fun call(): List<String> {

        log.info { "Start core types deploying" }

        val artifacts = localAppService.readStaticLocalArtifacts(
            TypeArtifactHandler.TYPE,
            "json",
            ObjectData.create()
        )

        log.info { "Found ${artifacts.size} core types in classpath" }

        val typesWithDeps = ArrayList<TypeDefWithDeps>()

        val allTypesIds = HashSet<String>()

        for (artifact in artifacts) {
            if (artifact !is ObjectData) {
                continue
            }
            val id = artifact["id"].asText()
            if (id.isBlank()) {
                continue
            }
            val typeDef = artifact.getAs(TypeDef::class.java) ?: continue
            val deps = if (typeDef.id == BASE_TYPE_ID) {
                mutableSetOf()
            } else {
                mutableSetOf(typeDef.parentRef.getLocalId())
            }
            allTypesIds.add(typeDef.id)
            typesWithDeps.add(TypeDefWithDeps(typeDef, deps))
        }
        val missingDepTypes = HashSet<String>()
        for (typeWithDeps in typesWithDeps) {
            for (dependencyTypeId in typeWithDeps.dependencies) {
                if (allTypesIds.add(dependencyTypeId)) {
                    // type is not found in resources, but we want it. Add it to types list.
                    missingDepTypes.add(dependencyTypeId)
                }
            }
        }
        for (missingTypeId in missingDepTypes) {
            typesWithDeps.add(
                TypeDefWithDeps(
                    TypeDef.create()
                        .withId(missingTypeId)
                        .withName(MLText(missingTypeId))
                        .build(),
                    mutableSetOf(BASE_TYPE_ID)
                )
            )
        }

        val typesToDeploy = ArrayList<TypeDef>()
        val resolvedTypes = HashSet<String>()
        var maxIterations = 10_000
        while (maxIterations-- > 0 && typesWithDeps.isNotEmpty()) {
            val typesWithDepsIt = typesWithDeps.iterator()
            while (typesWithDepsIt.hasNext()) {
                val type = typesWithDepsIt.next()
                if (type.dependencies.isEmpty()) {
                    if (typesService.getByIdOrNull(TypeId.create(type.typeDef.id)) == null) {
                        typesToDeploy.add(type.typeDef)
                    }
                    typesWithDepsIt.remove()
                    resolvedTypes.add(type.typeDef.id)
                }
            }
            if (resolvedTypes.isNotEmpty()) {
                for (typeWithDeps in typesWithDeps) {
                    typeWithDeps.dependencies.removeAll(resolvedTypes)
                }
                resolvedTypes.clear()
            } else if (typesWithDeps.isNotEmpty()) {
                log.warn {
                    "Resolved types is empty, but typesWithDeps " +
                        "contains ${typesWithDeps.joinToString { it.typeDef.id }}"
                }
                break
            }
        }
        val typesToDeployIds = typesToDeploy.map { it.id }
        if (typesToDeploy.isNotEmpty()) {
            log.info { "Deploy core types: ${typesToDeployIds.joinToString()}" }
            typesService.save(typesToDeploy)
        } else {
            log.info { "Nothing to deploy" }
        }
        return typesToDeployIds
    }

    class TypeDefWithDeps(
        val typeDef: TypeDef,
        val dependencies: MutableSet<String> = HashSet()
    )
}
