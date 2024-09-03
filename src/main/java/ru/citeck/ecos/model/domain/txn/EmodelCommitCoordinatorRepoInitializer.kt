package ru.citeck.ecos.model.domain.txn

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService

@Component
class EmodelCommitCoordinatorRepoInitializer(
    private val domainFactory: DbDomainFactory,
    private val discoveryService: WebAppDiscoveryService,
    private val repo: EmodelTwoPhaseCommitCoordinatorRepo
) {

    @PostConstruct
    fun init() {
        repo.init(domainFactory, discoveryService)
    }
}
