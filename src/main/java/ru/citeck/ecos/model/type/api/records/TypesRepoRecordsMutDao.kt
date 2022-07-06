package ru.citeck.ecos.model.type.api.records

import ecos.com.fasterxml.jackson210.annotation.JsonProperty
import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

@Component
class TypesRepoRecordsMutDao(
    private val typeService: TypesService
) : RecordMutateDtoDao<TypesRepoRecordsMutDao.TypeMutRecord>, RecordDeleteDao {

    override fun getId() = "types-repo"

    @Secured("ROLE_ADMIN")
    override fun getRecToMutate(recordId: String): TypeMutRecord {

        if (recordId.isEmpty()) {
            return TypeMutRecord(TypeDef.EMPTY)
        }
        val typeDef = typeService.getByIdOrNull(recordId) ?: TypeDef.create { withId(recordId) }
        return TypeMutRecord(typeDef)
    }

    @Secured("ROLE_ADMIN")
    override fun saveMutatedRec(record: TypeMutRecord): String {
        return typeService.save(record.build()).id
    }

    @Secured("ROLE_ADMIN")
    override fun delete(recordId: String): DelStatus {
        typeService.delete(recordId)
        return DelStatus.OK
    }

    class TypeMutRecord(typeDef: TypeDef) : TypeDef.Builder(typeDef) {

        fun setLocalId(localId: String) {
            this.withId(localId)
        }

        fun setModuleId(moduleId: String) {
            this.withId(moduleId)
        }

        fun setModelRoles(roles: List<RoleDef>) {
            withModel(model.copy().withRoles(roles).build())
        }

        fun setModelStatuses(statuses: List<StatusDef>) {
            withModel(model.copy().withStatuses(statuses).build())
        }

        fun setModelAttributes(attributes: List<AttributeDef>) {
            withModel(model.copy().withAttributes(attributes).build())
        }

        fun setDocLibEnabled(enabled: Boolean) {
            withDocLib(docLib.copy().withEnabled(enabled).build())
        }

        fun setDocLibFileTypeRefs(fileTypeRefs: List<RecordRef>) {
            withDocLib(docLib.copy().withFileTypeRefs(fileTypeRefs).build())
        }

        fun setDocLibDirTypeRef(typeRef: RecordRef) {
            withDocLib(docLib.copy().withDirTypeRef(typeRef).build())
        }

        @JsonProperty("_content")
        fun setContent(content: List<ObjectData>) {

            val dataUriContent = content[0].get("url", "")
            val data = Json.mapper.read(dataUriContent, ObjectData::class.java)

            Json.mapper.applyData(this, data)
        }
    }
}
