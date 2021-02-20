package ru.citeck.ecos.model.type.converter

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.association.converter.AssociationConverter
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.domain.TypeEntity
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.model.type.repository.TypeRepository
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMeta
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin
import java.time.Instant
import java.util.*
import kotlin.collections.LinkedHashSet

@Component
class TypeConverter(private val typeRepository: TypeRepository,
                    private val associationConverter: AssociationConverter,
                    @Qualifier("typesMutMetaMixin")
                    private val mutMetaMixin: MutMetaMixin) {

    fun dtoToEntity(dto: TypeDef): TypeEntity {

        var entity = typeRepository.findByExtId(dto.id)
        if (entity == null) {
            entity = TypeEntity()
            entity.extId = UUID.randomUUID().toString()
        }

        val typeDef = dto.copy()
            .withParentRef(dto.parentRef)
            .build()

        if (RecordRef.isEmpty(typeDef.parentRef)) {

            entity.parent = null

        } else {

            val parentEntity = typeRepository.findByExtId(typeDef.parentRef.id)
                    ?: error("Parent type is not found: ${typeDef.parentRef.id}")
            entity.parent = parentEntity
        }

        entity.name = Json.mapper.toString(typeDef.name)
        entity.description = Json.mapper.toString(typeDef.description)
        entity.system = typeDef.system
        entity.sourceId = typeDef.sourceId
        entity.metaRecord = typeDef.metaRecord.toString()
        entity.form = typeDef.formRef.toString()
        entity.journal = typeDef.journalRef.toString()
        entity.dashboardType = typeDef.dashboardType
        entity.inheritForm = typeDef.inheritForm
        entity.inheritActions = typeDef.inheritActions
        entity.inheritNumTemplate = typeDef.inheritNumTemplate
        entity.dispNameTemplate = Json.mapper.toString(typeDef.dispNameTemplate)
        entity.numTemplateRef = typeDef.numTemplateRef.toString()
        entity.actions = Json.mapper.toString(typeDef.actions)
        entity.defaultCreateVariant = typeDef.defaultCreateVariant
        entity.createVariants = Json.mapper.toString(typeDef.createVariants)
        entity.postCreateActionRef = typeDef.postCreateActionRef.toString()
        entity.configForm = typeDef.configFormRef.toString()
        entity.config = Json.mapper.toString(typeDef.config)
        entity.model = Json.mapper.toString(typeDef.model)
        entity.docLib = Json.mapper.toString(typeDef.docLib)
        entity.attributes = Json.mapper.toString(typeDef.properties)

        checkCyclicDependencies(entity)

        return entity
    }

    private fun checkCyclicDependencies(entity: TypeEntity) {

        val typesSet = LinkedHashSet<String>()
        var itEntity: TypeEntity? = entity

        while (itEntity != null) {
            if (!typesSet.add(itEntity.extId)) {
                error("Cyclic dependencies! $typesSet ${itEntity.extId}")
            }
            itEntity = itEntity.parent
        }
    }

    fun entityToDto(entity: TypeEntity): TypeDef {

        mutMetaMixin.addCtxMeta(entity.extId, MutMeta(
            entity.createdBy ?: "anonymous",
            entity.createdDate ?: Instant.EPOCH,
            entity.lastModifiedBy ?: "anonymous",
            entity.lastModifiedDate ?: Instant.EPOCH
        ))

        return TypeDef.create()
            .withId(entity.extId)
            .withName(Json.mapper.read(entity.name, MLText::class.java))
            .withDescription(Json.mapper.read(entity.description, MLText::class.java))
            .withSystem(entity.system)
            .withSourceId(entity.sourceId)
            .withMetaRecord(RecordRef.valueOf(entity.metaRecord))
            .withParentRef(RecordRef.valueOf(TypeUtils.getTypeRef(entity.parent?.extId ?: "")))
            .withFormRef(RecordRef.valueOf(entity.form))
            .withJournalRef(RecordRef.valueOf(entity.journal))
            .withDashboardType(entity.dashboardType)
            .withInheritForm(entity.inheritForm)
            .withInheritActions(entity.inheritActions)
            .withInheritNumTemplate(entity.inheritNumTemplate)
            .withDispNameTemplate(Json.mapper.read(entity.dispNameTemplate, MLText::class.java))
            .withNumTemplateRef(RecordRef.valueOf(entity.numTemplateRef))
            .withActions(DataValue.create(entity.actions).asList(RecordRef::class.java))
            .withDefaultCreateVariant(entity.defaultCreateVariant)
            .withCreateVariants(DataValue.create(entity.createVariants).asList(CreateVariantDef::class.java))
            .withPostCreateActionRef(RecordRef.valueOf(entity.postCreateActionRef))
            .withConfigFormRef(RecordRef.valueOf(entity.configForm))
            .withConfig(ObjectData.create(entity.config))
            .withModel(Json.mapper.read(entity.model, TypeModelDef::class.java))
            .withDocLib(Json.mapper.read(entity.docLib, DocLibDef::class.java))
            .withProperties(ObjectData.create(entity.attributes))
            .build()
    }
}
