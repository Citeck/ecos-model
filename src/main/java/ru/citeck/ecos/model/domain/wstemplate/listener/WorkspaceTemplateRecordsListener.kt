package ru.citeck.ecos.model.domain.wstemplate.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.concurrent.CopyOnWriteArrayList

@Component
class WorkspaceTemplateRecordsListener : DbRecordsListenerAdapter() {

    private val createdUpdatedListeners = CopyOnWriteArrayList<(EntityRef) -> Unit>()

    override fun onCreated(event: DbRecordCreatedEvent) {
        createdUpdatedListeners.forEach { it(event.localRef) }
    }

    override fun onChanged(event: DbRecordChangedEvent) {
        createdUpdatedListeners.forEach { it(event.localRef) }
    }

    fun listenCreatedOrUpdated(action: (record: EntityRef) -> Unit) {
        createdUpdatedListeners.add(action)
    }
}
