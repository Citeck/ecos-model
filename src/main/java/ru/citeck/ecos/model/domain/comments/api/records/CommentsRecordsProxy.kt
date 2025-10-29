package ru.citeck.ecos.model.domain.comments.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.comments.api.extractor.CommentExtractor
import ru.citeck.ecos.model.domain.comments.api.validator.CommentValidator
import ru.citeck.ecos.model.domain.comments.config.CommentDesc
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
        private const val TEMP_FILE_SRC_ID = "temp-file"
        private const val ATTACHMENT_SRC_ID = "attachment"
        private const val DOCS_ASSOC = "docs:documents"
        private const val ATT_ADD_ASPECTS = "att_add__aspects"

        private val INTERNAL_DOC_ASPECT_REF = ModelUtils.getAspectRef("internal-document")
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        for (record in records) {
            val text = record.getAtt(CommentDesc.ATT_TEXT).asText()
            val hasDocs = isMutAttsHasDocs(record)
            if (text.isNotBlank() || hasDocs) {
                val commentAtts = getCommentAtts(record)
                if (text.isNotBlank()) {
                    val commentText = createDocumentAttachmentsFromTempFiles(text, commentAtts)
                    record.setAtt(CommentDesc.ATT_TEXT, CommentValidator.removeVulnerabilities(commentText))
                }
                if (hasDocs && commentAtts.record.isNotEmpty()) {
                    AuthContext.runAsSystem {
                        recordsService.mutateAtt(
                            commentAtts.record,
                            CommentDesc.ATT_ADD_DOCUMENTS,
                            record.getAtt(CommentDesc.ATT_ADD_DOCUMENTS)
                        )
                    }
                    record.attributes.remove(CommentDesc.ATT_ADD_DOCUMENTS)
                }
            }
        }
        return super.mutate(records)
    }

    private fun isMutAttsHasDocs(record: LocalRecordAtts): Boolean {
        val docs = record.getAtt(CommentDesc.ATT_ADD_DOCUMENTS)
        return docs.isArray() && docs.isNotEmpty() || docs.isTextual() && docs.asText().isNotBlank()
    }

    private fun getCommentAtts(recordAtts: LocalRecordAtts): CommentAtts {
        return if (recordAtts.id.isEmpty()) {
            val record = recordAtts.getAtt(CommentDesc.ATT_RECORD).asText().toEntityRef()
            val tags = recordAtts.getAtt(CommentDesc.ATT_TAGS).map { it["type"].asText() }
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

        val attachments = commentExtractor.extractAttachRefsFromText(text)
        if (attachments.isEmpty()) {
            return text
        }

        val tempFilesRefsEntries = attachments.entries.filter { it.value.getSourceId() == TEMP_FILE_SRC_ID }
        if (tempFilesRefsEntries.isEmpty()) {
            return text
        }

        if (commentAtts.record.isEmpty()) {
            error("Comment record is empty")
        }
        var resText = text
        val isInternalComment = commentAtts.tags.any { it == INTERNAL_TAG_TYPE }

        for ((srcString, tempFileRef) in tempFilesRefsEntries) {
            val mutationAtts = RecordAtts(EntityRef.create(AppName.EMODEL, ATTACHMENT_SRC_ID, ""))
            if (isInternalComment) {
                mutationAtts[ATT_ADD_ASPECTS] = INTERNAL_DOC_ASPECT_REF
            }
            mutationAtts[RecordConstants.ATT_CONTENT] = tempFileRef
            mutationAtts[RecordConstants.ATT_PARENT] = commentAtts.record
            mutationAtts[RecordConstants.ATT_PARENT_ATT] = DOCS_ASSOC
            val newDocRef = recordsService.mutate(mutationAtts)
            val newDocRefStr = if (tempFileRef.getAppName().isEmpty()) {
                newDocRef.withoutAppName()
            } else {
                newDocRef
            }.toString()
            resText = resText.replace(srcString, newDocRefStr)
        }

        return resText
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*> {

        val isRunAsSystem = AuthContext.isRunAsSystem()

        var innerAttsMap = AttContext.getInnerAttsMap()
        if (!isRunAsSystem) {
            innerAttsMap = innerAttsMap.toMutableMap()
            innerAttsMap[RECORD_READ_PERM_ATT] = "${CommentDesc.ATT_RECORD}.permissions._has.Read?bool!"
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
