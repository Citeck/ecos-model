package ru.citeck.ecos.model.domain.aspects.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.aspects.config.AspectsConfiguration
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.Promises
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import ru.citeck.ecos.webapp.lib.registry.EcosRegistryProps
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
import ru.citeck.ecos.webapp.lib.registry.init.EcosRegistryInitializer

@Component
class AspectsRegistryInitializer(
    private val recordsService: RecordsService,
    private val aspectsConfiguration: AspectsConfiguration
) : EcosRegistryInitializer<AspectDef> {

    companion object {
        const val ORDER = -10f

        private val log = KotlinLogging.logger {}
    }

    private lateinit var registry: MutableEcosRegistry<AspectDef>

    fun updateAspect(aspectDef: AspectDef) {
        registry.setValue(aspectDef.id, aspectDef)
    }

    fun removeAspect(aspectId: String) {
        registry.setValue(aspectId, null)
    }

    override fun init(
        registry: MutableEcosRegistry<AspectDef>,
        values: Map<String, EntityWithMeta<AspectDef>>,
        props: EcosRegistryProps.Initializer
    ): Promise<*> {

        this.registry = registry

        val query = RecordsQuery.create {
            withSourceId(AspectsConfiguration.ASPECTS_DAO_ID)
            withQuery(Predicates.alwaysTrue())
            withPage(QueryPage.create { withMaxItems(5000) })
        }

        val records = AuthContext.runAsSystem {
            recordsService.query(query, AspectDef::class.java).getRecords()
        }

        log.info { "Found aspect records: ${records.size}" }

        records.forEach {
            if (it.id.isNotBlank()) {
                registry.setValue(it.id, it)
            }
        }
        aspectsConfiguration.register(this)

        return Promises.resolve(Unit)
    }

    override fun getOrder(): Float {
        return ORDER
    }

    override fun getKey(): String {
        return "ecos-model-app-aspects"
    }
}
