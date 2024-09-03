package ru.citeck.ecos.model.app

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.util.*
import javax.sql.DataSource

@RestController
@Secured(AuthRole.ADMIN)
@RequestMapping("/api/ecosdata")
class EcosDataController(
    val recordsService: RecordsService,
    val typesRegistry: EcosTypesRegistry,
    val dataSource: DataSource
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
