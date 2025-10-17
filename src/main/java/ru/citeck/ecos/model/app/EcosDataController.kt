package ru.citeck.ecos.model.app

import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry

@RestController
@Secured(AuthRole.ADMIN)
@RequestMapping("/api/ecosdata")
class EcosDataController(
    val recordsService: RecordsService,
    val typesRegistry: EcosTypesRegistry
)
