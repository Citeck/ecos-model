package ru.citeck.ecos.model.domain.wkgsch.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.wkgsch.lib.schedule.dto.WorkingScheduleDto
import java.util.function.Consumer

@Component
class WorkingScheduleArtifactHandler(
    private val recordsService: RecordsService,
    private val eventsService: EventsService
) : EcosArtifactHandler<WorkingScheduleDto> {

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(toRef(artifactId))
    }

    override fun deployArtifact(artifact: WorkingScheduleDto) {
        recordsService.mutate(
            EntityRef.create("working-schedule", ""),
            artifact
        )
    }

    override fun getArtifactType(): String {
        return "model/working-schedule"
    }

    override fun listenChanges(listener: Consumer<WorkingScheduleDto>) {
        eventsService.addListener<WorkingScheduleEventAtts> {
            withDataClass(WorkingScheduleEventAtts::class.java)
            withFilter(Predicates.eq("typeDef.id", "working-schedule"))
            withLocal(true)
            withAction {
                listener.accept(
                    WorkingScheduleDto(
                        id = it.id,
                        version = it.version,
                        country = it.country,
                        type = it.type,
                        config = it.config
                    )
                )
            }
        }
    }

    private fun toRef(id: String): EntityRef {
        return EntityRef.create("working-schedule", id)
    }

    private class WorkingScheduleEventAtts(
        @AttName("record?localId")
        val id: String,
        @AttName("record.version?num!")
        val version: Int,
        @AttName("record.country!")
        val country: String,
        @AttName("record.type!")
        val type: String,
        @AttName("record.config?json!")
        val config: ObjectData
    )
}
