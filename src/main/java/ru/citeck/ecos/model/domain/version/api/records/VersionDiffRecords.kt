package ru.citeck.ecos.model.domain.version.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.version.service.VersionDiffService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class VersionDiffRecords(
    private val versionDiffService: VersionDiffService
) : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "version-diff"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val first = recsQuery.getQuery(VersionDiffQuery::class.java).first
        val second = recsQuery.getQuery(VersionDiffQuery::class.java).second

        recordsToDiffMustBeSameSource(first, second)

        if (first.isAlfRecord()) {
            val alfQuery = recsQuery.withSourceId("alfresco/version-diff")
            return recordsService.query(alfQuery, AttContext.getInnerAttsMap())
        }

        val firstData = recordsService.getAtts(first, DataDto::class.java)
        val secondData = recordsService.getAtts(second, DataDto::class.java)
        if (firstData.format != secondData.format) {
            error("Records format to diff must be the same")
        }

        val diffResult: String = versionDiffService.getDiff(
            String(firstData.data, Charsets.UTF_8),
            String(secondData.data, Charsets.UTF_8),
            firstData.format
        )

        val result = RecsQueryRes<VersionDiffResult>()
        result.setRecords(listOf(VersionDiffResult(diffResult)))

        return result
    }

    private fun recordsToDiffMustBeSameSource(first: EntityRef, second: EntityRef) {
        if (!(first.getAppName() == second.getAppName() && first.getSourceId() == second.getSourceId())) {
            error("Records to diff must be same source. First: $first, second: $second")
        }
    }

    private data class VersionDiffQuery(
        val first: EntityRef,
        val second: EntityRef
    )

    private data class DataDto(
        val data: ByteArray,
        @AttName("format!")
        val format: String = ""
    )

    private data class VersionDiffResult(
        val diff: String
    )
}
