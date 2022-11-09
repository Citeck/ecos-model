package ru.citeck.ecos.model.domain.version.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class VersionRecords : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "version"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val record = recsQuery.getQuery(VersionQuery::class.java).record
        if (record.isAlfRecord()) {
            val alfQuery = recsQuery.withSourceId("alfresco/version")
            return recordsService.query(alfQuery, AttContext.getInnerAttsMap())
        }

        if (versionDaoIsNotExists(record)) {
            return null
        }

        val revQuery = recsQuery.withSourceId(
            record.getAppName() + "/" + record.getSourceId() + "-$ID"
        )

        return recordsService.query(revQuery, AttContext.getInnerAttsMap())
    }

    private fun versionDaoIsNotExists(record: EntityRef): Boolean {
        return recordsService.getAtt(
            "${record.getAppName()}/src@${record.getSourceId()}-$ID",
            "_notExists?bool"
        ).asBoolean()
    }

    private data class VersionQuery(
        val record: EntityRef
    )
}

fun EntityRef.isAlfRecord(): Boolean {
    return getAppName() == AppName.ALFRESCO || getLocalId().startsWith("workspace://")
}
