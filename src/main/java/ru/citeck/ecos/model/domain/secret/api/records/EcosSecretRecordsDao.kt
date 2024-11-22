package ru.citeck.ecos.model.domain.secret.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.secret.service.EcosSecretDto
import ru.citeck.ecos.model.domain.secret.service.EcosSecretService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.secrets.lib.EcosSecrets
import ru.citeck.ecos.secrets.lib.secret.certificate.CertificateSecretData
import ru.citeck.ecos.secrets.lib.secret.certificate.CertificateValidityStatus
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

@Component
class EcosSecretRecordsDao(
    private val service: EcosSecretService
) : AbstractRecordsDao(), RecordsQueryDao, RecordDeleteDao, RecordAttsDao, RecordMutateDao {

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

    override fun delete(recordId: String): DelStatus {
        service.delete(recordId)
        return DelStatus.OK
    }

    override fun getId(): String {
        return ID
    }

    /**
     * We purposely don`t return secret data here, because it is sensitive information.
     */
    class SecretRecord(
        @AttName("...")
        val dto: EcosSecretDto,
        val meta: EntityMeta
    ) {

        private val certificateData: CertificateSecretData? by lazy {
            EcosSecrets.getSecretOrNull(dto.id)?.getCertificateDataOrNull()
        }

        @get:AttName(".disp")
        val disp: MLText?
            get() = let {
                val name = dto.name
                if (name != MLText.EMPTY) {
                    name
                } else {
                    MLText(dto.id)
                }
            }

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

        fun getCertValidityFrom(): Instant? {
            if (AuthContext.isRunAsSystemOrAdmin()) {
                return AuthContext.runAsSystem {
                    certificateData?.validityFrom
                }
            }

            return null
        }

        fun getCertValidityTo(): Instant? {
            if (AuthContext.isRunAsSystemOrAdmin()) {
                return AuthContext.runAsSystem {
                    certificateData?.validityTo
                }
            }

            return null
        }

        fun getCertificateValidityStatus(): CertificateValidityStatus {
            if (AuthContext.isRunAsSystemOrAdmin()) {
                return AuthContext.runAsSystem {
                    certificateData?.validityStatus ?: CertificateValidityStatus.UNKNOWN
                }
            }

            return CertificateValidityStatus.UNKNOWN
        }
    }
}
