package ru.citeck.ecos.model.domain.admin.groupaction.execution

import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.groupactions.context.GroupActionContext
import ru.citeck.ecos.groupactions.execution.GroupActionExecution
import ru.citeck.ecos.groupactions.execution.GroupActionExecutionFactory
import ru.citeck.ecos.groupactions.execution.result.ActionResult
import ru.citeck.ecos.groupactions.execution.result.ActionResultOk
import ru.citeck.ecos.webapp.api.entity.EntityRef

abstract class AdminGroupActionBase<C : Any, S>(
    private val actionType: String
) : GroupActionExecutionFactory<EntityRef, C> {

    final override fun createExecution(config: C): GroupActionExecution<EntityRef> {
        if (AuthContext.isNotRunAsSystemOrAdmin()) {
            error("Permission denied")
        }
        val state = getState(config)
        return object : GroupActionExecution<EntityRef> {
            override fun execute(context: GroupActionContext<EntityRef>): ActionResult {
                context.getBatchedValues(30).forEach { entities ->
                    AuthContext.runAsSystem { processImpl(state, entities) }
                }
                return ActionResultOk
            }
            override fun dispose() {}
        }
    }

    abstract fun processImpl(state: S, records: List<EntityRef>)

    abstract fun getState(config: C): S

    final override fun getType(): String {
        return actionType
    }
}
