package ru.citeck.ecos.model.domain.contentcheckout.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordContentChangedEvent
import ru.citeck.ecos.model.domain.contentcheckout.service.ContentCheckoutService
import ru.citeck.ecos.records2.predicate.model.AndPredicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName

@Component
class ContentCheckoutListener(
    eventsService: EventsService
) {

    init {
        eventsService.addListener<ContentChangedData> {
            withEventType(RecordContentChangedEvent.TYPE)
            withTransactional(true)
            withDataClass(ContentChangedData::class.java)
            withFilter(
                AndPredicate.of(
                    Predicates.eq("record._aspects._has.content-checkout?bool", true),
                    Predicates.eq("record.checkout:isCheckedOut?bool", true)
                )
            )
            withAction { data -> validateContentChange(data) }
        }
    }

    private fun validateContentChange(data: ContentChangedData) {
        if (AuthContext.isRunAsSystem()) {
            return
        }
        if (data.checkedOutMode == ContentCheckoutService.Mode.MANUAL.name) {
            val currentUser = AuthContext.getCurrentUser()
            if (currentUser == data.checkedOutBy) {
                return
            }
            error("Document content is checked out by '${data.checkedOutBy}'. Content modification is not allowed.")
        }
        error("Document content is checked out by document editor. Content modification is not allowed.")
    }

    class ContentChangedData(
        @AttName("record.checkout:checkedOutBy?localId")
        val checkedOutBy: String = "",
        @AttName("record.checkout:checkedOutMode?str")
        val checkedOutMode: String = ""
    )
}
