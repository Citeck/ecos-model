package ru.citeck.ecos.model.type.service

import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.type.dto.TypeInfo
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import javax.annotation.PostConstruct

@Component
class TypesRepoImpl(
    private val typeService: TypeService,
    private val recordsService: RecordsService,
    private val applicationContext: ApplicationContext
) : TypesRepo {

    private lateinit var typeTypeInfo: TypeInfo

    @PostConstruct
    fun init() {

        val typeConfigFile = applicationContext.getResource("classpath:eapps/artifacts/model/type/type.yml")
        val typeDef = Json.mapper.read(typeConfigFile.file, TypeDef::class.java)!!

        typeTypeInfo = TypeInfo(
            typeDef.id,
            typeDef.name,
            TypeUtils.getTypeRef("base"),
            typeDef.dispNameTemplate,
            RecordRef.EMPTY,
            typeDef.model
        )
    }

    override fun getChildren(typeRef: RecordRef): List<RecordRef> {
        return typeService.getChildren(typeRef.id).map { TypeUtils.getTypeRef(it) }
    }

    override fun getTypeInfo(typeRef: RecordRef): TypeInfo? {
        if (typeRef.id == "type") {
            return typeTypeInfo
        }
        val type = recordsService.getAtts(typeRef.withSourceId("rtype"), TypeInfoAtts::class.java)
        if (type.id.isNullOrBlank()) {
            return null
        }
        return TypeInfo.create {
            withId(type.id)
            withName(type.name ?: MLText.EMPTY)
            withDispNameTemplate(type.dispNameTemplate)
            withParentRef(type.parentRef)
            withModel(type.model)
            withNumTemplateRef(type.numTemplateRef)
        }
    }

    data class TypeInfoAtts(
        val id: String?,
        val name: MLText?,
        val parentRef: RecordRef?,
        val dispNameTemplate: MLText?,
        val numTemplateRef: RecordRef?,
        val model: TypeModelDef?
    )
}
