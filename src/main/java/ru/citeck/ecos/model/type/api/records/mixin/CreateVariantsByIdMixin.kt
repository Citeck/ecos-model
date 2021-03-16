package ru.citeck.ecos.model.type.api.records.mixin

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.type.api.records.ResolvedTypeRecordsDao
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

@Component
final class CreateVariantsByIdMixin(
        resolvedTypeRecordsDao: ResolvedTypeRecordsDao,
        typeRecordsDao: TypeRecordsDao
) : AttMixin {

    companion object {
        private const val ATT_CREATE_VARIANTS_BY_ID = "createVariantsById"

        private val ATTRIBUTES = setOf(
            ATT_CREATE_VARIANTS_BY_ID
        )
    }

    init {
        typeRecordsDao.addAttributesMixin(this)
        resolvedTypeRecordsDao.addAttributesMixin(this)
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return CreateVarsAttValue(value.getAtts(AllCreateVarsAtts::class.java))
    }

    override fun getProvidedAtts(): Collection<String> {
        return ATTRIBUTES
    }

    class AllCreateVarsAtts(
        val createVariants: List<CreateVariantDef>
    )

    class CreateVarsAttValue(private val variants: AllCreateVarsAtts) : AttValue {
        override fun getAtt(name: String): Any? {
            return variants.createVariants.find { it.id == name }
        }
    }
}
