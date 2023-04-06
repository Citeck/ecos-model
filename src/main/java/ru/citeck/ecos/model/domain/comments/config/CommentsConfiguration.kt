package ru.citeck.ecos.model.domain.comments.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.comments.api.dto.CommentTagType
import ru.citeck.ecos.model.domain.comments.api.dto.CommentTag
import ru.citeck.ecos.model.domain.comments.api.records.COMMENT_RECORD_ATT
import ru.citeck.ecos.model.domain.comments.api.records.COMMENT_REPO_DAO_ID
import ru.citeck.ecos.model.domain.comments.api.records.CommentsMixin
import ru.citeck.ecos.model.domain.comments.event.CommentsEmitEventsDbRecordsListener
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import javax.sql.DataSource

val ECOS_COMMENT_TYPE_REF = ModelUtils.getTypeRef("ecos-comment")

@Configuration
class CommentsConfiguration(private val dbDomainFactory: DbDomainFactory) {

    companion object {
        private val TAGS_DISABLED_EDITING = listOf(CommentTagType.TASK, CommentTagType.ACTION, CommentTagType.INTEGRATION)
    }

    @Bean
    fun commentsRepo(
        dataSource: DataSource,
        commentsEmitEventsDbRecordsListener: CommentsEmitEventsDbRecordsListener,
        recordsService: RecordsService
    ): RecordsDao {

        val permsComponent = object : DbPermsComponent {

            override fun getEntityPerms(entityRef: EntityRef): DbRecordPerms {

                return object : DbRecordPerms {
                    override fun getAuthoritiesWithReadPermission(): Set<String> {
                        return setOf(AuthGroup.EVERYONE)
                    }

                    override fun isCurrentUserHasWritePerms(): Boolean {
                        val commentData = AuthContext.runAsSystem {
                            recordsService.getAtts(entityRef, CommentData::class.java)
                        }

                        if (commentData.tags.any { it.type in TAGS_DISABLED_EDITING }) {
                            return false
                        }

                        return AuthContext.isRunAsAdmin() || commentData.creator == AuthContext.getCurrentUser()
                    }

                    override fun isCurrentUserHasAttReadPerms(name: String): Boolean {
                        if (AuthContext.isRunAsAdmin()) {
                            return true
                        }

                        return AuthContext.runAsSystem {
                            recordsService.getAtt(
                                entityRef,
                                "$COMMENT_RECORD_ATT.permissions._has.Read?bool"
                            ).asBoolean()
                        }
                    }

                    override fun isCurrentUserHasAttWritePerms(name: String): Boolean {
                        return when (name) {
                            COMMENT_RECORD_ATT -> AuthContext.isRunAsAdmin()
                            "tags" -> AuthContext.isRunAsAdmin()
                            else -> isCurrentUserHasWritePerms()
                        }
                    }
                }
            }
        }

        val dao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(COMMENT_REPO_DAO_ID)
                        withTypeRef(ECOS_COMMENT_TYPE_REF)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        // comments should be visible for all, but editable only for concrete persons
                        withTable("ecos_comments")
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema("public")
            .withPermsComponent(permsComponent)
            .build()

        dao.addListener(commentsEmitEventsDbRecordsListener)
        dao.addAttributesMixin(CommentsMixin())

        return dao
    }
}

private data class CommentData(
    @AttName("_creator.id")
    val creator: String,

    @AttName("tags[]")
    val tags: List<CommentTag>
)

