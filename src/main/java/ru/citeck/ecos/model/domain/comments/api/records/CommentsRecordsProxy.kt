package ru.citeck.ecos.model.domain.comments.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.comments.api.validator.CommentValidator
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueProxy
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate
import ru.citeck.ecos.records3.record.atts.value.impl.InnerAttValue
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes

const val COMMENT_REPO_DAO_ID = "comment-repo"
const val COMMENT_DAO_ID = "comment"
const val COMMENT_RECORD_ATT = "record"
const val ATT_TEXT = "text"
private const val RECORD_READ_PERM_ATT = "hasReadRecordsPermissions"

@Component
class CommentsRecordsProxy : RecordsDaoProxy(
    COMMENT_DAO_ID,
    COMMENT_REPO_DAO_ID
) {
    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        for (record in records) {
            val text = record.getAtt(ATT_TEXT).asText()
            if (text.isNotBlank()) {
                record.setAtt(ATT_TEXT, CommentValidator.removeVulnerabilities(text))
            }
        }
        return super.mutate(records)
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*> {
        val comments = getCommentsWithPermissions(recsQuery)

        val commentsAvailableToRead = comments.getRecords().filter {
            it[RECORD_READ_PERM_ATT]["/permissions/_has/Read/?bool"].asBoolean()
        }.map {
            val ref = RecordRef.create(COMMENT_DAO_ID, it.getId().id)
            val innerAttValue = InnerAttValue(it.getAtts().getData().asJson())

            ProxyRecVal(ref, innerAttValue)
        }

        val result = RecsQueryRes(commentsAvailableToRead)
        result.setTotalCount(commentsAvailableToRead.size.toLong())

        return result
    }

    private fun getCommentsWithPermissions(recsQuery: RecordsQuery): RecsQueryRes<RecordAtts> {
        val innerAttsMap = AttContext.getInnerAttsMap()

        val attsWithPermissionReadAtt = innerAttsMap.toMutableMap()
        attsWithPermissionReadAtt[RECORD_READ_PERM_ATT] = "$COMMENT_RECORD_ATT.permissions._has.Read?bool!"

        return recordsService.query(recsQuery.withSourceId(COMMENT_REPO_DAO_ID), attsWithPermissionReadAtt, true)
    }

    private class ProxyRecVal(
        private val id: RecordRef,
        base: AttValue
    ) : AttValueDelegate(base), AttValueProxy {

        override fun getId(): Any {
            return id
        }
    }
}
