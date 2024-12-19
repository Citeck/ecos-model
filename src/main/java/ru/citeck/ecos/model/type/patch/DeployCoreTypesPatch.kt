package ru.citeck.ecos.model.type.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosLocalPatch
import java.util.concurrent.Callable

@Component
@EcosLocalPatch("deploy-core-types", "2024-12-19T00:00:00Z")
class DeployCoreTypesPatch(
    private val typesService: TypesService,
    private val localAppService: LocalAppService
) : Callable<List<String>> {

    companion object {
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
        typesWithDeps.add(
            TypeDefWithDeps(
                TypeDef.create()
                    // maybe move ecos-vcs-object to emodel?
                    .withId("ecos-vcs-object")
                    .build(), mutableSetOf("base")
            )
        )

        for (artifact in artifacts) {
            if (artifact !is ObjectData) {
                continue
            }
            val id = artifact["id"].asText()
            if (id.isBlank()) {
                continue
            }
            val typeDef = artifact.getAs(TypeDef::class.java) ?: continue
            val deps = if (typeDef.id == "base") {
                mutableSetOf()
            } else {
                mutableSetOf(typeDef.parentRef.getLocalId())
            }
            typesWithDeps.add(TypeDefWithDeps(typeDef, deps))
        }

        val typesToDeploy = ArrayList<TypeDef>()
        val resolvedTypes = HashSet<String>()
        var maxIterations = 10_000
        while (maxIterations-- > 0) {
            val typesWithDepsIt = typesWithDeps.iterator()
            while (typesWithDepsIt.hasNext()) {
                val type = typesWithDepsIt.next()
                if (type.dependencies.isEmpty()) {
                    if (typesService.getByIdOrNull(type.typeDef.id) == null) {
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
            } else {
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
