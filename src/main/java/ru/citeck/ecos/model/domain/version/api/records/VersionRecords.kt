package ru.citeck.ecos.model.domain.version.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class VersionRecords :
    AbstractRecordsDao(),
    RecordsQueryDao {

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

        if (versionDaoIsExists(record)) {
            val revQuery = recsQuery.withSourceId(
                record.getAppName() + "/" + record.getSourceId() + "-$ID"
            )
            return recordsService.query(revQuery, AttContext.getInnerAttsMap())
        }

        if (record.getAppName() == AppName.EMODEL) {
            val revQuery = recsQuery.withSourceId(
                record.getAppName() + "/record-version"
            )
            return recordsService.query(revQuery, AttContext.getInnerAttsMap())
        }
        return null
    }

    private fun versionDaoIsExists(record: EntityRef): Boolean {
        val notExists = recordsService.getAtt(
            "${record.getAppName()}/src@${record.getSourceId()}-$ID",
            "${RecordConstants.ATT_NOT_EXISTS}?bool"
        ).asBoolean()
        return !notExists
    }

    private data class VersionQuery(
        val record: EntityRef
    )
}

fun EntityRef.isAlfRecord(): Boolean {
    return getAppName() == AppName.ALFRESCO || getLocalId().startsWith("workspace://")
}
