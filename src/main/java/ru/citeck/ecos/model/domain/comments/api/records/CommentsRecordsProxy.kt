package ru.citeck.ecos.model.domain.comments.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.comments.api.extractor.CommentExtractor
import ru.citeck.ecos.model.domain.comments.api.validator.CommentValidator
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueProxy
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate
import ru.citeck.ecos.records3.record.atts.value.impl.InnerAttValue
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

const val COMMENT_REPO_DAO_ID = "comment-repo"
const val COMMENT_DAO_ID = "comment"
const val COMMENT_RECORD_ATT = "record"
const val ATT_TEXT = "text"
private const val RECORD_READ_PERM_ATT = "hasReadRecordsPermissions"

@Component
class CommentsRecordsProxy(
    private val commentExtractor: CommentExtractor
) : RecordsDaoProxy(
    COMMENT_DAO_ID,
    COMMENT_REPO_DAO_ID
) {

    companion object {
        private const val INTERNAL_TAG_TYPE = "INTERNAL"
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        for (record in records) {
            val text = record.getAtt(ATT_TEXT).asText()
            if (text.isNotBlank()) {
                val commentAtts = getCommentAtts(record)
                val commentText = createDocumentAttachmentsFromTempFiles(text, commentAtts)
                record.setAtt(ATT_TEXT, CommentValidator.removeVulnerabilities(commentText))
            }
        }
        return super.mutate(records)
    }

    private fun getCommentAtts(recordAtts: LocalRecordAtts): CommentAtts {
        return if (recordAtts.id.isEmpty()) {
            val record = recordAtts.getAtt("record").asText().toEntityRef()
            val tags = recordAtts.getAtt("tags").map { it["type"].asText() }
            CommentAtts(record, tags)
        } else {
            AuthContext.runAsSystem {
                recordsService.getAtts(
                    EntityRef.create(COMMENT_REPO_DAO_ID, recordAtts.id),
                    CommentAtts::class.java
                )
            }
        }
    }

    private fun createDocumentAttachmentsFromTempFiles(text: String, commentAtts: CommentAtts): String {

        val attachments = commentExtractor.extractAttachmentsRefs(
            commentExtractor.extractJsonStrings(text)
        )
        if (attachments.isEmpty()) {
            return text
        }

        val tempFilesRefs = attachments.filter { it.getSourceId() == "temp-file" }
        if (tempFilesRefs.isEmpty()) {
            return text
        }

        if (commentAtts.record.isEmpty()) {
            error("Comment record is empty")
        }
        var resText = text
        val isInternalComment = commentAtts.tags.any { it == INTERNAL_TAG_TYPE }

        for (tempFileRef in tempFilesRefs) {
            val mutationAtts = RecordAtts(EntityRef.create(AppName.EMODEL, "attachment", ""))
            if (isInternalComment) {
                mutationAtts["att_add__aspects"] = ModelUtils.getAspectRef("internal-document")
            }
            mutationAtts["_content"] = tempFileRef
            mutationAtts[RecordConstants.ATT_PARENT] = commentAtts.record
            mutationAtts[RecordConstants.ATT_PARENT_ATT] = "docs:documents"
            val newDocRef = recordsService.mutate(mutationAtts)
            resText = resText.replace(tempFileRef.toString(), newDocRef.toString())
        }

        return resText
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*> {

        val isRunAsSystem = AuthContext.isRunAsSystem()

        var innerAttsMap = AttContext.getInnerAttsMap()
        if (!isRunAsSystem) {
            innerAttsMap = innerAttsMap.toMutableMap()
            innerAttsMap[RECORD_READ_PERM_ATT] = "$COMMENT_RECORD_ATT.permissions._has.Read?bool!"
        }

        var commentsRecs = recordsService.query(
            recsQuery.withSourceId(COMMENT_REPO_DAO_ID),
            innerAttsMap,
            true
        ).getRecords()

        if (!isRunAsSystem) {
            commentsRecs = commentsRecs.filter {
                it[RECORD_READ_PERM_ATT]["/permissions/_has/Read/?bool"].asBoolean()
            }
        }
        val resRecords = commentsRecs.map {
            val ref = EntityRef.create(COMMENT_DAO_ID, it.getId().getLocalId())
            val innerAttValue = InnerAttValue(it.getAtts().getData().asJson())
            ProxyRecVal(ref, innerAttValue)
        }
        return RecsQueryRes(resRecords)
    }

    private class ProxyRecVal(
        private val id: EntityRef,
        base: AttValue
    ) : AttValueDelegate(base), AttValueProxy {

        override fun getId(): Any {
            return id
        }
    }

    private class CommentAtts(
        val record: EntityRef,
        @AttName("tags[].type")
        val tags: List<String>
    )
}
