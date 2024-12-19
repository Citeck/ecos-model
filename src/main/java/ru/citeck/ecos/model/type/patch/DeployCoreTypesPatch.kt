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
        for (artifact in artifacts) {
            if (artifact !is ObjectData) {
                continue
            }
            val id = artifact["id"].asText()
            if (id.isBlank()) {
                continue
            }
            val parentRef = artifact["parentRef"].getAs(EntityRef::class.java) ?: ModelUtils.getTypeRef("base")
            val typeDef = TypeDef.create {
                withId(id)
                withName(artifact["name"].getAs(MLText::class.java) ?: MLText.EMPTY)
                withSourceId(artifact["sourceId"].asText())
                withDispNameTemplate(artifact["dispNameTemplate"].getAs(MLText::class.java) ?: MLText.EMPTY)
                withParentRef(parentRef)
                withNumTemplateRef(artifact["numTemplateRef"].getAs(EntityRef::class.java))
                withModel(artifact["model"].getAs(TypeModelDef::class.java))
            }
            typesWithDeps.add(TypeDefWithDeps(typeDef, mutableSetOf(parentRef.getLocalId())))
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
