package ru.citeck.ecos.model.domain.events.emitter

import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.events2.type.*

@Component
class DbRecordsEcosEventsAdapter(val emitter: RecordEventsService) : DbRecordsListener {

    override fun onChanged(event: DbRecordChangedEvent) {
        emitter.emitRecChanged(
            RecordChangedEvent(
                event.record,
                event.typeDef,
                event.before,
                event.after
            )
        )
    }

    override fun onCreated(event: DbRecordCreatedEvent) {
        emitter.emitRecCreated(
            RecordCreatedEvent(
                event.record,
                event.typeDef
            )
        )
    }

    override fun onDeleted(event: DbRecordDeletedEvent) {
        emitter.emitRecDeleted(
            RecordDeletedEvent(
                event.record,
                event.typeDef
            )
        )
    }

    override fun onDraftStatusChanged(event: DbRecordDraftStatusChangedEvent) {
        emitter.emitRecDraftStatusChanged(
            RecordDraftStatusChangedEvent(
                event.record,
                event.typeDef,
                event.before,
                event.after
            )
        )
    }

    override fun onStatusChanged(event: DbRecordStatusChangedEvent) {
        emitter.emitRecStatusChanged(
            RecordStatusChangedEvent(
                event.record,
                event.typeDef,
                event.before,
                event.after
            )
        )
    }
}
