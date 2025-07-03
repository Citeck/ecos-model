package ru.citeck.ecos.model.domain.admin.groupaction

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.groupactions.GroupActionsService
import ru.citeck.ecos.model.domain.admin.groupaction.execution.UpdateCalculatedAttsAdminAction
import ru.citeck.ecos.model.domain.admin.groupaction.execution.UpdatePermissionsAdminAction
import ru.citeck.ecos.model.domain.admin.groupaction.execution.UpdateWorkspaceAdminAction
import ru.citeck.ecos.model.domain.admin.groupaction.values.AdminActionRecordsOfTypeValues
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry

@Component
class GroupActionsConfigurator(
    private val groupActionsService: GroupActionsService,
    private val recordsService: RecordsService,
    private val typesRegistry: EcosTypesRegistry
) {

    @PostConstruct
    fun init() {
        this.groupActionsService.registerValuesFactory(
            AdminActionRecordsOfTypeValues(recordsService, typesRegistry)
        )
        this.groupActionsService.registerExecutionFactory(
            UpdatePermissionsAdminAction(recordsService)
        )
        this.groupActionsService.registerExecutionFactory(
            UpdateWorkspaceAdminAction(recordsService)
        )
        this.groupActionsService.registerExecutionFactory(
            UpdateCalculatedAttsAdminAction(recordsService)
        )
    }
}
