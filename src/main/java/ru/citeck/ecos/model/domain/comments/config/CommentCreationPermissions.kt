package ru.citeck.ecos.model.domain.comments.config

import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class CommentCreationPermissions(private val recordsService: RecordsService) : DbRecordsListenerAdapter() {

    fun checkCreate(parent: EntityRef) {
        // Custom DAOs need not expose permissions or _notExists. Only explicit non-existence denies creation.
        check(!recordsService.getAtt(parent, "${RecordConstants.ATT_NOT_EXISTS}?bool").asBoolean()) {
            "Permission denied: cannot create a comment on a missing or inaccessible record"
        }
    }

    override fun onCreated(event: DbRecordCreatedEvent) {
        // Also protect direct comment-repo mutations. Listener failures roll back the creation.
        checkCreate(recordsService.getAtt(event.record, "${CommentDesc.ATT_RECORD}?id").asText().toEntityRef())
    }
}
