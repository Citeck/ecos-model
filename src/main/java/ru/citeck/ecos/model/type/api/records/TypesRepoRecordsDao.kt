package ru.citeck.ecos.model.type.api.records

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.commons.json.YamlUtils
import ru.citeck.ecos.events2.type.RecordEventsService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.permissions.dto.PermissionType
import ru.citeck.ecos.model.lib.type.dto.TypeAspectDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.service.TypeDesc
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.perms.RecordPerms
import java.nio.charset.StandardCharsets
import java.time.Instant

@Component
class TypesRepoRecordsDao(
    private val typeService: TypesService,
    private val recordEventsService: RecordEventsService? = null,
    private val typesRepoPermsService: TypesRepoPermsService? = null
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    companion object {
        const val ID = "types-repo"
    }

    init {
        typeService.addListenerWithMeta { before, after ->
            if (after != null) {
                onTypeDefChanged(before, after)
            }
        }
    }

    override fun getId() = ID

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<TypeRecord> {

        val result = RecsQueryRes<TypeRecord>()

        when (recsQuery.language) {

            PredicateService.LANGUAGE_PREDICATE -> {

                val predicate = recsQuery.getQuery(Predicate::class.java)

                val types = typeService.getAllWithMeta(
                    recsQuery.page.maxItems,
                    recsQuery.page.skipCount,
                    predicate,
                    recsQuery.sortBy
                )

                result.setRecords(types.map { TypeRecord(it.entity, it.meta) })
                result.setTotalCount(typeService.getCount(predicate))
            }
            else -> {

                val max: Int = recsQuery.page.maxItems
                val types = if (max < 0) {
                    typeService.getAllWithMeta()
                } else {
                    typeService.getAllWithMeta(max, recsQuery.page.skipCount)
                }
                result.setRecords(types.map { TypeRecord(it.entity, it.meta) })
            }
        }

        return result
    }

    override fun getRecordAtts(recordId: String): TypeRecord? {
        return typeService.getByIdWithMetaOrNull(recordId)?.let {
            TypeRecord(it.entity, it.meta)
        }
    }

    private fun onTypeDefChanged(before: EntityWithMeta<TypeDef>?, after: EntityWithMeta<TypeDef>) {
        recordEventsService?.emitRecChanged(before, after, getId()) {
            TypeRecord(it.entity, it.meta)
        }
    }

    inner class TypeRecord(
        @AttName("...")
        val typeDef: TypeDef,
        private val audit: EntityMeta
    ) {

        fun getCustomAspects(): List<TypeAspectDef> {
            return typeDef.aspects.filter {
                !TypeDesc.NON_CUSTOM_ASPECTS.contains(it.ref.getLocalId())
            }
        }

        fun getAtt(name: String): Any? {
            val aspectCfgKey = TypeDesc.parseAspectCfgKey(name) ?: return null
            val aspectData = typeDef.aspects.find { it.ref.getLocalId() == aspectCfgKey.aspectId }
            if (aspectCfgKey.configKey == TypeDesc.ASPECT_CONFIG_ADDED_FLAG) {
                return aspectData != null
            }
            if (aspectData == null) {
                return null
            }
            return aspectData.config[aspectCfgKey.configKey]
        }

        fun getFormRef(): EntityRef {
            if (typeDef.id.isNotBlank() && typeDef.formRef.getLocalId() == TypeDefResolver.DEFAULT_FORM) {
                return typeDef.formRef.withLocalId("type$" + typeDef.id)
            }
            return typeDef.formRef
        }

        fun getJournalRef(): EntityRef {
            if (typeDef.id.isNotBlank() && typeDef.journalRef.getLocalId() == TypeDefResolver.DEFAULT_JOURNAL) {
                return typeDef.journalRef.withLocalId("type$" + typeDef.id)
            }
            return typeDef.journalRef
        }

        fun getData(): ByteArray {
            return YamlUtils.toNonDefaultString(typeDef).toByteArray(StandardCharsets.UTF_8)
        }

        @AttName("?json")
        fun getJson(): JsonNode {
            return mapper.toNonDefaultJson(typeDef)
        }

        @AttName("?disp")
        fun getDisplayName(): Any {
            if (!MLText.isEmpty(typeDef.name)) {
                return typeDef.name
            }
            return typeDef.id
        }

        @AttName("_type")
        fun getEcosType(): EntityRef {
            return ModelUtils.getTypeRef("type")
        }

        fun getPermissions(): Any? {
            return typesRepoPermsService?.let {
                PermissionsValue(it.getPermissions(this))
            }
        }

        @AttName(RecordConstants.ATT_CREATOR)
        fun getCreator(): EntityRef {
            return AuthorityType.PERSON.getRef(audit.creator)
        }

        @AttName(RecordConstants.ATT_MODIFIER)
        fun getModifier(): EntityRef {
            return AuthorityType.PERSON.getRef(audit.modifier)
        }

        @AttName(RecordConstants.ATT_MODIFIED)
        fun getModified(): Instant {
            return audit.modified
        }

        @AttName(RecordConstants.ATT_CREATED)
        fun getCreated(): Instant {
            return audit.created
        }

        private inner class PermissionsValue(val perms: RecordPerms) : AttValue {
            override fun has(name: String): Boolean {
                if (name.equals(PermissionType.READ.name, true)) {
                    return true
                }
                return perms.hasPermission(name)
            }
        }
    }
}
