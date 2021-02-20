package ru.citeck.ecos.model.type.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.api.records.ResolvedTypeRecordsDao
import ru.citeck.ecos.records2.RecordRef

@Component
class TypesRepoImpl(
    private val resolvedTypeRecordsDao: ResolvedTypeRecordsDao,
    private val typeService: TypeService
) : TypesRepo {

    override fun getChildren(typeRef: RecordRef): List<RecordRef> {
        return typeService.getChildren(typeRef.id).map { TypeUtils.getTypeRef(it) }
    }

    override fun getModel(typeRef: RecordRef): TypeModelDef {
        return resolvedTypeRecordsDao.getRecordAtts(typeRef.id)?.getModel() ?: TypeModelDef.EMPTY
    }

    override fun getParent(typeRef: RecordRef): RecordRef {
        return RecordRef.valueOf(typeService.getByIdOrNull(typeRef.id)?.parentRef)
    }
}
