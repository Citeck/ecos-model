package ru.citeck.ecos.model.domain.secret.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.domain.secret.service.EcosSecretDto
import ru.citeck.ecos.model.domain.secret.service.EcosSecretService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

@Component
class EcosSecretRecordsDao(
    private val service: EcosSecretService
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao, RecordMutateDao {

    companion object {
        const val ID = "secret"
    }

    override fun getRecordAtts(recordId: String): Any? {
        return service.getByIdWithMeta(recordId)?.let {
            SecretRecord(it.entity, it.meta)
        }
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        if (recsQuery.language.isNotEmpty() && recsQuery.language != PredicateService.LANGUAGE_PREDICATE) {
            return null
        }
        val predicate = recsQuery.getQuery(Predicate::class.java)
        val result = service.find(
            recsQuery.page.maxItems,
            recsQuery.page.skipCount,
            predicate,
            recsQuery.sortBy
        ).map { SecretRecord(it.entity, it.meta) }
        val res = RecsQueryRes(result)
        res.setTotalCount(service.getCount(predicate))
        return res
    }

    override fun mutate(record: LocalRecordAtts): String {
        val recToMutate = if (record.id.isNotEmpty()) {
            service.getById(record.id)?.copy() ?: error("Record with id '${record.id}' is not found")
        } else {
            val id = record.getAtt("id").asText()
            if (id.isBlank()) {
                error("Secret id is undefined")
            }
            service.getById(id)?.copy() ?: EcosSecretDto.create()
        }
        Json.mapper.applyData(recToMutate, record.getAtts())
        return service.save(recToMutate.build()).id
    }

    override fun getId(): String {
        return ID
    }

    class SecretRecord(
        @AttName("...")
        val dto: EcosSecretDto,
        val meta: EntityMeta
    ) {
        fun getEcosType(): String {
            return "secret"
        }
        fun getCreated(): Instant {
            return meta.created
        }
        fun getCreator(): EntityRef {
            return AuthorityType.PERSON.getRef(meta.creator)
        }
        fun getModified(): Instant {
            return meta.modified
        }
        fun getModifier(): EntityRef {
            return AuthorityType.PERSON.getRef(meta.modifier)
        }
        fun getAsJson(): EcosSecretDto {
            return dto
        }
    }
}
