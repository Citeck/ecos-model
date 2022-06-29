package ru.citeck.ecos.model.domain.authsync.service.impl

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSync
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncContext
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncFactory
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.Consistency
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

@Component
class AlfrescoAuthoritiesSyncFactory(
    private val recordsService: RecordsService
) : AuthoritiesSyncFactory<AlfrescoAuthoritiesSyncFactory.Config, AlfrescoAuthoritiesSyncFactory.State> {

    companion object {
        private const val MODIFIED_ATT_ALIAS = "__lastModified"
        private const val SYS_DBID_ATT_ALIAS = "__sys_dbid"
        private const val AUTHORITY_ID_ALIAS = "__authorityId"

        private const val ALF_MODIFIED_ATT = "cm:modified"
        private const val ALF_USERNAME_ATT = "cm:userName"
        private const val ALF_GROUP_ID_WITHOUT_PREFIX_ATT = "cm:authorityName|rxg('GROUP_(.+)')"
        private const val ALF_NODE_DBID_ATT = "sys:node-dbid"
        private const val ALF_TYPE_PERSON = "cm:person"
        private const val ALF_TYPE_GROUP = "cm:authorityContainer"

        private const val DEFAULT_BATCH_SIZE = 10

        private val SIMPLE_ALF_ATT_REGEX = "^\\w+:\\w+(\\?[a-zA-Z]+)?$".toRegex()

        private val log = KotlinLogging.logger {}
    }

    override fun createSync(
        config: Config,
        authorityType: AuthorityType,
        context: AuthoritiesSyncContext<State>
    ): AuthoritiesSync<State> {
        return Sync(config, authorityType, context)
    }

    inner class Sync(
        config: Config,
        private val authorityType: AuthorityType,
        private val context: AuthoritiesSyncContext<State>
    ) : AuthoritiesSync<State> {

        private val config: Config
        private val typePredicate: Predicate
        private val mutationAtts: Map<String, String>
        private val syncAtts: Map<String, String>

        init {
            val batchSize = if (config.batchSize <= 0) {
                DEFAULT_BATCH_SIZE
            } else {
                config.batchSize
            }
            this.config = config.copy(batchSize = batchSize)
            this.typePredicate = Predicates.eq(
                "TYPE",
                when (authorityType) {
                    AuthorityType.PERSON -> ALF_TYPE_PERSON
                    AuthorityType.GROUP -> ALF_TYPE_GROUP
                }
            )
            mutationAtts = config.attributes.entries.filter {
                it.value.matches(SIMPLE_ALF_ATT_REGEX)
            }.associate { it.key to it.value }
            syncAtts = config.attributes.entries.associate {
                val value = if (it.value.contains('.') || it.value.contains("?")) {
                    it.value
                } else {
                    it.value + "?str"
                }
                it.key to value
            }
        }

        override fun execute(state: State?): Boolean {

            val currentState = state ?: State(
                // first sync should be based on id because not all authorities has cm:modified field
                syncById = true,
                lastId = -1,
                lastModified = Instant.EPOCH
            )
            return if (currentState.syncById) {
                if (!updateAuthoritiesByDbId(currentState, context)) {
                    context.setState(
                        currentState.copy(
                            syncById = false,
                            lastModified = Instant.now().minus(2, ChronoUnit.DAYS)
                        )
                    )
                    return true
                }
                return false
            } else {
                updateAuthoritiesByLastModified(currentState, context)
            }
        }

        override fun getManagedAtts(): Set<String> {
            return mutationAtts.keys
        }

        override fun mutate(record: LocalRecordAtts, newRecord: Boolean): String {

            val newAtts = ObjectData.create()
            mutationAtts.forEach { (k, v) ->
                if (record.attributes.has(k)) {
                    var value: DataValue = record.attributes.get(k)
                    if (authorityType == AuthorityType.GROUP && k == "name") {
                        value = DataValue.create(value.getAs(MLText::class.java)?.getClosest(Locale.ENGLISH))
                    }
                    newAtts.set(v, value)
                }
            }
            if (record.id.isNotBlank() && newAtts.size() == 0) {
                return record.id
            }

            if (record.attributes.has("id")) {
                var recId = record.attributes.get("id").asText()
                if (authorityType == AuthorityType.PERSON) {
                    recId = recId.lowercase()
                }
                newAtts.set("id", recId)
            }

            val targetSourceId = when (authorityType) {
                AuthorityType.PERSON -> "people"
                AuthorityType.GROUP -> "authority-group"
            }
            val refToMutate = RecordRef.create("alfresco", targetSourceId, record.id)
            val refToSync = if (refToMutate.id.isEmpty()) {
                refToMutate.withId(newAtts.get("id").asText())
            } else {
                refToMutate
            }

            if (newRecord) {
                recordsService.mutate(RecordAtts(refToMutate, newAtts))
                val groupRecs = prepareGroupsUpdateRecords(refToSync, record.attributes, false)
                if (groupRecs.isNotEmpty()) {
                    recordsService.mutate(groupRecs)
                }
            } else {
                val recsToMutate = mutableListOf<RecordAtts>()
                recsToMutate.addAll(prepareGroupsUpdateRecords(refToMutate, record.attributes, true))
                recsToMutate.add(RecordAtts(refToMutate, newAtts))
                recordsService.mutate(recsToMutate)
            }

            val attsToSync = HashMap(syncAtts)
            attsToSync[AUTHORITY_ID_ALIAS] = getAuthorityIdAtt()
            val attsAfterMutation = recordsService.getAtts(refToSync, attsToSync)
            updateAuthorities(context, listOf(attsAfterMutation))

            return refToSync.id
        }

        private fun prepareGroupsUpdateRecords(
            authorityRef: RecordRef,
            atts: ObjectData,
            withRemove: Boolean
        ): List<RecordAtts> {

            if (!atts.has(AuthorityConstants.ATT_AUTHORITY_GROUPS)) {
                return emptyList()
            }
            val newGroups = atts.get(AuthorityConstants.ATT_AUTHORITY_GROUPS).asList(RecordRef::class.java)

            val recsToMutate = mutableListOf<RecordAtts>()

            val groupsAtt = syncAtts[AuthorityConstants.ATT_AUTHORITY_GROUPS]
            if (groupsAtt.isNullOrBlank()) {
                return emptyList()
            }

            val oldGroups = recordsService.getAtt(authorityRef, groupsAtt).asList(RecordRef::class.java)

            if (withRemove) {
                val removedGroups = HashSet(oldGroups)
                removedGroups.removeAll(newGroups.toSet())
                recsToMutate.addAll(prepareAddOrRemoveGroupsActions(authorityRef, false, removedGroups))
            }

            val addedGroups = HashSet(newGroups)
            addedGroups.removeAll(oldGroups.toSet())
            recsToMutate.addAll(prepareAddOrRemoveGroupsActions(authorityRef, true, addedGroups))

            return recsToMutate
        }

        private fun prepareAddOrRemoveGroupsActions(
            recRef: RecordRef,
            add: Boolean,
            groups: Set<RecordRef>
        ): List<RecordAtts> {
            if (groups.isEmpty()) {
                return emptyList()
            }
            if (add) {
                log.info { "Add new groups for $recRef. Groups: $groups" }
            } else {
                log.info { "Remove groups for $recRef. Groups: $groups" }
            }
            val groupsAlfAuthRefs = groups.map {
                RecordRef.create("alfresco", "authority", "GROUP_${it.id}")
            }.toList()

            val groupsNodeRefs = recordsService.getAtts(groupsAlfAuthRefs, listOf("nodeRef")).map {
                it.getAtt("nodeRef").asText()
            }.toList()
            val refAlfAuthRef = RecordRef.create(
                "alfresco", "authority",
                when (authorityType) {
                    AuthorityType.PERSON -> recRef.id
                    AuthorityType.GROUP -> "GROUP_${recRef.id}"
                }
            )
            val recRefNodeRef = recordsService.getAtt(refAlfAuthRef, "nodeRef").asText()

            return groupsNodeRefs.map {
                val groupAlfRef = RecordRef.create("alfresco", "assoc-actions", "")
                val groupAtts = ObjectData.create()
                groupAtts.set("action", if (add) { "CREATE" } else { "REMOVE" })
                groupAtts.set("sourceRef", it)
                groupAtts.set("targetRef", recRefNodeRef)
                groupAtts.set("association", "cm:member")
                RecordAtts(groupAlfRef, groupAtts)
            }
        }

        private fun updateAuthoritiesByLastModified(state: State, context: AuthoritiesSyncContext<State>): Boolean {

            val atts = HashMap(syncAtts)
            atts[SYS_DBID_ATT_ALIAS] = ALF_NODE_DBID_ATT
            atts[MODIFIED_ATT_ALIAS] = ALF_MODIFIED_ATT
            atts[AUTHORITY_ID_ALIAS] = getAuthorityIdAtt()

            val records = recordsService.query(
                RecordsQuery.create {
                    withSourceId("alfresco/")
                    withQuery(
                        Predicates.and(
                            typePredicate,
                            ValuePredicate.gt(ALF_MODIFIED_ATT, state.lastModified.toString())
                        )
                    )
                    withMaxItems(config.batchSize)
                    withSortBy(SortBy(ALF_MODIFIED_ATT, true))
                    withConsistency(Consistency.EVENTUAL)
                },
                atts
            )

            if (updateAuthorities(context, records.getRecords())) {

                val lastRec = records.getRecords().last()
                val lastModified = lastRec
                    .getAtt(MODIFIED_ATT_ALIAS)
                    .getAs(Instant::class.java)
                    ?: error(
                        "Last modified date is not valid for record ${lastRec.getId()}. " +
                            "Date: ${lastRec.getAtt(MODIFIED_ATT_ALIAS)}"
                    )

                context.setState(state.copy(lastModified = lastModified))
                return true
            }
            return false
        }

        private fun updateAuthoritiesByDbId(state: State, context: AuthoritiesSyncContext<State>): Boolean {

            val atts = HashMap(syncAtts)
            atts[SYS_DBID_ATT_ALIAS] = ALF_NODE_DBID_ATT
            atts[AUTHORITY_ID_ALIAS] = getAuthorityIdAtt()

            val records = recordsService.query(
                RecordsQuery.create {
                    withSourceId("alfresco/")
                    withQuery(
                        Predicates.and(
                            typePredicate,
                            Predicates.gt(ALF_NODE_DBID_ATT, state.lastId)
                        )
                    )
                    withMaxItems(config.batchSize)
                    withSortBy(SortBy(ALF_NODE_DBID_ATT, true))
                    withConsistency(Consistency.EVENTUAL)
                },
                atts
            )

            if (updateAuthorities(context, records.getRecords())) {
                val dbId = records.getRecords().last().getAtt(SYS_DBID_ATT_ALIAS).asLong()
                context.setState(state.copy(lastId = dbId))
                return true
            }
            return false
        }

        private fun updateAuthorities(
            context: AuthoritiesSyncContext<State>,
            authorities: List<RecordAtts>
        ): Boolean {

            if (authorities.isEmpty()) {
                return false
            }
            AuthContext.runAsSystem {
                val authoritiesToUpdate = authorities.map {
                    val atts = it.getAtts().deepCopy()
                    atts["id"] = it.getAtt(AUTHORITY_ID_ALIAS)
                    if (authorityType == AuthorityType.PERSON) {
                        preparePersonUpdating(atts)
                    } else {
                        atts
                    }
                }
                context.updateAuthorities(authorityType, authoritiesToUpdate)
            }
            return true
        }

        private fun preparePersonUpdating(personAtts: ObjectData): ObjectData {

            val id = personAtts.get("id", "")

            val newPhotoCacheKey = personAtts.get(PersonConstants.ATT_PHOTO_CACHE_KEY, "")
            val currPhotoCacheKey = recordsService.getAtt(
                AuthorityType.PERSON.getRef(id),
                PersonConstants.ATT_PHOTO_CACHE_KEY
            ).asText()

            if (newPhotoCacheKey != currPhotoCacheKey) {
                val alfRef = RecordRef.create("alfresco", "people", id)
                val photoAtts = recordsService.getAtts(alfRef, PhotoAtts::class.java)
                if (!photoAtts.bytes.isNullOrBlank()) {
                    val mimeType = photoAtts.mimeType?.ifBlank { "image/jpeg" } ?: "image/jpeg"
                    val contentUrl = "data:$mimeType;base64,${photoAtts.bytes}"
                    personAtts.set(PersonConstants.ATT_PHOTO, Collections.singletonMap("url", contentUrl))
                }
            }

            if (personAtts.has(PersonConstants.ATT_AT_WORKPLACE) &&
                personAtts.get(PersonConstants.ATT_AT_WORKPLACE).asText().isBlank()
            ) {

                personAtts.set(PersonConstants.ATT_AT_WORKPLACE, true)
            }

            return personAtts
        }

        private fun getAuthorityIdAtt(): String {
            return when (authorityType) {
                AuthorityType.PERSON -> ALF_USERNAME_ATT
                AuthorityType.GROUP -> ALF_GROUP_ID_WITHOUT_PREFIX_ATT
            }
        }
    }

    override fun getType(): String {
        return "alfresco"
    }

    data class Config(
        val batchSize: Int = DEFAULT_BATCH_SIZE,
        val attributes: Map<String, String>
    )

    data class State(
        val syncById: Boolean,
        val lastId: Long,
        val lastModified: Instant
    )

    data class PhotoAtts(
        @AttName("ecos:photo.bytes")
        val bytes: String?,
        @AttName("ecos:photo.mimetype")
        val mimeType: String?
    )
}
