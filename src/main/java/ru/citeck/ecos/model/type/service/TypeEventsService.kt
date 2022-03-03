package ru.citeck.ecos.model.type.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.model.domain.events.emitter.RecordEventsEmitter
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName

@Component
class TypeEventsService {

    private lateinit var recordEventsEmitter: RecordEventsEmitter
    private lateinit var typesRepo: TypesRepo
    private lateinit var typeRecordsDao: TypeRecordsDao

    fun onTypeCreated(typeDef: TypeDef) {
        val rec = EventRecord(typeRecordsDao.getRecord(typeDef))
        recordEventsEmitter.emitRecCreatedEvent(RecordCreatedEvent(rec, getTypeInfoForEvent()))
    }

    fun onTypeChanged(before: TypeDef, after: TypeDef) {

        val beforeAtts = ObjectData.create(before)
        val afterAtts = ObjectData.create(after)

        val changedAttsBefore = mutableMapOf<String, Any?>()
        val changedAttsAfter = mutableMapOf<String, Any?>()

        val typeInfo = getTypeInfoForEvent()

        typeInfo.model.attributes.forEach { attName ->
            changedAttsBefore[attName.id] = beforeAtts.get(attName.id)
            changedAttsAfter[attName.id] = afterAtts.get(attName.id)
        }

        recordEventsEmitter.emitRecChanged(
            RecordChangedEvent(
                EventRecord(typeRecordsDao.getRecord(after)),
                getTypeInfoForEvent(),
                changedAttsBefore,
                changedAttsAfter
            )
        )
    }

    private fun getTypeInfoForEvent(): TypeInfo {
        return typesRepo.getTypeInfo(TypeUtils.getTypeRef("type"))
            ?: error("Type info for 'type' doesn't found")
    }

    @Lazy
    @Autowired
    fun setTypesRepo(typesRepo: TypesRepo) {
        this.typesRepo = typesRepo
    }

    @Lazy
    @Autowired
    fun setRecordEventsEmitter(recordEventsEmitter: RecordEventsEmitter) {
        this.recordEventsEmitter = recordEventsEmitter
    }

    @Lazy
    @Autowired
    fun setTypeRecordsDao(typeRecordsDao: TypeRecordsDao) {
        this.typeRecordsDao = typeRecordsDao
    }

    class EventRecord(
        @AttName("...")
        val rec: TypeRecordsDao.TypeRecord
    ) {
        @AttName("?id")
        fun getId(): RecordRef {
            return RecordRef.create("emodel", "type", rec.typeDef.id)
        }
    }
}
