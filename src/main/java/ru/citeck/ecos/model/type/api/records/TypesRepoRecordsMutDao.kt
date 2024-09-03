package ru.citeck.ecos.model.type.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.procstages.dto.ProcStageDef
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

@Component
class TypesRepoRecordsMutDao(
    private val typeService: TypesService,
    private val typesRepoPermsService: TypesRepoPermsService? = null
) : RecordMutateDtoDao<TypesRepoRecordsMutDao.TypeMutRecord>, RecordDeleteDao {

    override fun getId() = "types-repo"

    override fun getRecToMutate(recordId: String): TypeMutRecord {
        if (recordId.isEmpty()) {
            return TypeMutRecord(TypeDef.EMPTY)
        }
        return TypeMutRecord(typeService.getById(recordId))
    }

    override fun saveMutatedRec(record: TypeMutRecord): String {

        val newTypeDef = record.build()

        if (newTypeDef.id.isEmpty()) {
            error("Type identifier is empty")
        }

        typesRepoPermsService?.checkMutationPermissions(record)

        val baseId = record.baseTypeDef.id
        val clonedRecord = baseId.isNotEmpty() && baseId != newTypeDef.id

        return typeService.save(newTypeDef, clonedRecord).id
    }

    override fun delete(recordId: String): DelStatus {
        typesRepoPermsService?.checkDeletePermissions(recordId)
        typeService.delete(recordId)
        return DelStatus.OK
    }

    class TypeMutRecord(val baseTypeDef: TypeDef) : TypeDef.Builder(baseTypeDef) {

        fun isNewRec(): Boolean {
            return this.id != baseTypeDef.id
        }

        fun withLocalId(localId: String) {
            this.withId(localId)
        }

        fun withModuleId(moduleId: String) {
            this.withId(moduleId)
        }

        fun withModelRoles(roles: List<RoleDef>) {
            model.withRoles(roles)
        }

        fun withModelStatuses(statuses: List<StatusDef>) {
            model.withStatuses(statuses)
        }

        fun withModelStages(stages: List<ProcStageDef>) {
            model.withStages(stages)
        }

        fun withModelAttributes(attributes: List<AttributeDef>) {
            model.withAttributes(attributes)
        }

        fun withDocLibEnabled(enabled: Boolean) {
            withDocLib(docLib.copy().withEnabled(enabled).build())
        }

        fun withDocLibFileTypeRefs(fileTypeRefs: List<EntityRef>) {
            withDocLib(docLib.copy().withFileTypeRefs(fileTypeRefs).build())
        }

        fun withDocLibDirTypeRef(typeRef: EntityRef) {
            withDocLib(docLib.copy().withDirTypeRef(typeRef).build())
        }
    }
}
