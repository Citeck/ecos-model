package ru.citeck.ecos.model.domain.gantt.api.records

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class GanttProjectManagementRecordsDao : RecordsQueryDao {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override fun getId(): String {
        return "gantt-project-management"
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<EntityRef> {
        log.info {"Querying records with: $recsQuery" }
        return RecsQueryRes()
    }

}
