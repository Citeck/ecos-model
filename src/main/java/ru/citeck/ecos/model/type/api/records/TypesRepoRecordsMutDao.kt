package ru.citeck.ecos.model.type.api.records

import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.procstages.dto.ProcStageDef
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

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    override fun getRecToMutate(recordId: String): TypeMutRecord {
        if (recordId.isEmpty()) {
            return TypeMutRecord(TypeDef.EMPTY)
        }
        return TypeMutRecord(typeService.getById(recordId))
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    override fun saveMutatedRec(record: TypeMutRecord): String {

        val newTypeDef = record.build()

        if (newTypeDef.id.isEmpty()) {
            error("Type identifier is empty")
        }

        val baseId = record.baseTypeDef.id
        val clonedRecord = baseId.isNotEmpty() && baseId != newTypeDef.id
        return typeService.save(newTypeDef, clonedRecord).id
    }

    @Secured("ROLE_ADMIN")
    override fun delete(recordId: String): DelStatus {
        typeService.delete(recordId)
        return DelStatus.OK
    }

    class TypeMutRecord(val baseTypeDef: TypeDef) : TypeDef.Builder(baseTypeDef) {

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

        fun withDocLibFileTypeRefs(fileTypeRefs: List<RecordRef>) {
            withDocLib(docLib.copy().withFileTypeRefs(fileTypeRefs).build())
        }

        fun withDocLibDirTypeRef(typeRef: RecordRef) {
            withDocLib(docLib.copy().withDirTypeRef(typeRef).build())
        }
    }
}
