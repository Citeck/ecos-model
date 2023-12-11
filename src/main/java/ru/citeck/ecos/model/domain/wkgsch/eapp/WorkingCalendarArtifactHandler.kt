package ru.citeck.ecos.model.domain.wkgsch.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.wkgsch.lib.calendar.dto.WorkingCalendarDateDto
import ru.citeck.ecos.wkgsch.lib.calendar.dto.WorkingCalendarDto
import java.time.LocalDate
import java.util.function.Consumer

@Component
class WorkingCalendarArtifactHandler(
    private val recordsService: RecordsService,
    private val eventsService: EventsService
) : EcosArtifactHandler<WorkingCalendarDto> {

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(toRef(artifactId))
    }

    override fun deployArtifact(artifact: WorkingCalendarDto) {
        recordsService.mutate(
            EntityRef.create("working-calendar", ""),
            artifact
        )
    }

    override fun getArtifactType(): String {
        return "model/working-calendar"
    }

    override fun listenChanges(listener: Consumer<WorkingCalendarDto>) {
        eventsService.addListener<PermissionSettingsEventAtts> {
            withDataClass(PermissionSettingsEventAtts::class.java)
            withFilter(Predicates.eq("typeDef.id", "working-calendar"))
            withLocal(true)
            withAction {
                listener.accept(
                    WorkingCalendarDto(
                        id = it.id,
                        extensionFor = it.extensionFor,
                        from = LocalDate.parse(it.from.ifBlank { LocalDate.MIN.toString() }),
                        until = LocalDate.parse(it.until.ifBlank { LocalDate.MIN.toString() }),
                        enabled = it.enabled,
                        dates = it.dates
                    )
                )
            }
        }
    }

    private fun toRef(id: String): EntityRef {
        return EntityRef.create("working-calendar", id)
    }

    private class PermissionSettingsEventAtts(
        @AttName("record?localId")
        val id: String,
        @AttName("record.extensionFor?id!")
        val extensionFor: EntityRef,
        @AttName("record.from!")
        val from: String,
        @AttName("record.until!")
        val until: String,
        @AttName("record.enabled!true")
        val enabled: Boolean,
        @AttName("record.dates[]?json!")
        val dates: List<WorkingCalendarDateDto>
    )
}
