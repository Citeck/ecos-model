package ru.citeck.ecos.model.domain.activity.event

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.exception.I18nRuntimeException
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class ActivityParentCycleValidator(
    eventsService: EventsService,
    private val recordsService: RecordsService
) {

    companion object {
        private const val ACTIVITY_ATTS_ASPECT = "activity-atts"
        private const val ATT_ACTIVITY_PARENT = "activity:parent"
        private const val MAX_DEPTH = 100

        private const val MSG_SELF_REFERENCE = "ecos-model.activity.parent.self-reference"
        private const val MSG_CYCLE_DETECTED = "ecos-model.activity.parent.cycle-detected"
        private const val MSG_MAX_DEPTH_EXCEEDED = "ecos-model.activity.parent.max-depth-exceeded"
        private val log = KotlinLogging.logger {}
    }

    init {
        eventsService.addListener<ActivityParentChangedAtts> {
            withEventType(RecordChangedEvent.TYPE)
            withTransactional(true)
            withDataClass(ActivityParentChangedAtts::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("record._aspects._has.$ACTIVITY_ATTS_ASPECT?bool", true),
                    Predicates.eq("diff._has.$ATT_ACTIVITY_PARENT?bool", true)
                )
            )
            withAction { validate(it) }
        }
    }

    private fun validate(atts: ActivityParentChangedAtts) {
        val self = atts.ref
        val parent = atts.parent
        if (self == EntityRef.EMPTY || parent == null || parent == EntityRef.EMPTY) {
            return
        }
        val selfLabel = formatLabel(self, atts.refDisp)
        val parentLabel = formatLabel(parent, atts.parentDisp)
        if (parent == self) {
            throw I18nRuntimeException(MSG_SELF_REFERENCE, mapOf("ref" to selfLabel))
        }
        AuthContext.runAsSystem {
            val visited = HashSet<EntityRef>()
            visited.add(self)
            var current: EntityRef = parent
            var depth = 0
            while (current != EntityRef.EMPTY) {
                if (current == self) {
                    throw I18nRuntimeException(
                        MSG_CYCLE_DETECTED,
                        mapOf("ref" to selfLabel, "parent" to parentLabel)
                    )
                }
                if (!visited.add(current)) {
                    log.warn {
                        "Pre-existing cycle detected in activity:parent chain " +
                            "while validating '$self' (new parent '$parent'). " +
                            "Stopped at '$current'."
                    }
                    return@runAsSystem
                }
                if (++depth > MAX_DEPTH) {
                    throw I18nRuntimeException(
                        MSG_MAX_DEPTH_EXCEEDED,
                        mapOf("ref" to selfLabel, "parent" to parentLabel, "maxDepth" to MAX_DEPTH)
                    )
                }
                current = recordsService.getAtt(current, "$ATT_ACTIVITY_PARENT?id")
                    .asText()
                    .let { if (it.isBlank()) EntityRef.EMPTY else EntityRef.valueOf(it) }
            }
        }
    }

    private fun formatLabel(ref: EntityRef, disp: String?): String {
        val refStr = ref.toString()
        return if (disp.isNullOrBlank() || disp == refStr) refStr else "$disp ($refStr)"
    }

    class ActivityParentChangedAtts(
        @AttName("record?id")
        val ref: EntityRef,
        @AttName("record?disp")
        val refDisp: String?,
        @AttName("record.activity:parent?id")
        val parent: EntityRef?,
        @AttName("record.activity:parent?disp")
        val parentDisp: String?
    )
}
