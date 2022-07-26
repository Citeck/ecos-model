package ru.citeck.ecos.model.type.converter

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.dto.AssocDef
import ru.citeck.ecos.model.type.dto.EcosTypeContentConfig
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.dao.TypeRepoDao
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMeta
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin
import ru.citeck.ecos.webapp.lib.model.type.dto.AssocDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.time.Instant
import java.util.*
import kotlin.collections.LinkedHashSet

@Component
class TypeConverter(private val typeRepoDao: TypeRepoDao) {

    var mutMetaMixin: MutMetaMixin? = null

    fun toEntity(dto: TypeDef): TypeEntity {

        var entity = typeRepoDao.findByExtId(dto.id)
        if (entity == null) {
            entity = TypeEntity()
            entity.extId = dto.id.ifBlank {
                UUID.randomUUID().toString()
            }
        }

        val typeDef = dto.copy().build()

        if (RecordRef.isEmpty(typeDef.parentRef)) {

            entity.parent = null
        } else {

            val parentEntity = typeRepoDao.findByExtId(typeDef.parentRef.id)
                ?: error("Parent type is not found: ${typeDef.parentRef.id}")
            entity.parent = parentEntity
        }

        entity.name = Json.mapper.toString(typeDef.name)
        entity.description = Json.mapper.toString(typeDef.description)
        entity.system = typeDef.system
        entity.sourceType = typeDef.sourceType
        entity.sourceId = typeDef.sourceId
        entity.sourceRef = typeDef.sourceRef.toString()
        entity.metaRecord = typeDef.metaRecord.toString()
        entity.form = typeDef.formRef.toString()
        entity.journal = typeDef.journalRef.toString()
        entity.board = typeDef.boardRef.toString()
        entity.dashboardType = typeDef.dashboardType
        entity.inheritForm = typeDef.inheritForm
        entity.inheritActions = typeDef.inheritActions
        entity.inheritNumTemplate = typeDef.inheritNumTemplate
        entity.dispNameTemplate = Json.mapper.toString(typeDef.dispNameTemplate)
        entity.numTemplateRef = typeDef.numTemplateRef.toString()
        entity.actions = Json.mapper.toString(typeDef.actions)
        entity.associations = Json.mapper.toString(typeDef.associations)
        entity.defaultCreateVariant = typeDef.defaultCreateVariant
        entity.createVariants = Json.mapper.toString(typeDef.createVariants)
        entity.postCreateActionRef = typeDef.postCreateActionRef.toString()
        entity.configForm = typeDef.configFormRef.toString()
        entity.config = Json.mapper.toString(typeDef.config)
        entity.model = Json.mapper.toString(typeDef.model)
        entity.docLib = Json.mapper.toString(typeDef.docLib)
        entity.attributes = Json.mapper.toString(typeDef.properties)
        entity.contentConfig = Json.mapper.toString(typeDef.contentConfig)

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

    fun toDto(entity: TypeEntity): TypeDef {
        return toDtoWithMeta(entity).entity
    }

    fun toDtoWithMeta(entity: TypeEntity): EntityWithMeta<TypeDef> {

        mutMetaMixin?.addCtxMeta(
            entity.extId,
            MutMeta(
                entity.createdBy ?: "anonymous",
                entity.createdDate ?: Instant.EPOCH,
                entity.lastModifiedBy ?: "anonymous",
                entity.lastModifiedDate ?: Instant.EPOCH
            )
        )

        val typeDef = TypeDef.create()
            .withId(entity.extId)
            .withName(Json.mapper.read(entity.name, MLText::class.java))
            .withDescription(Json.mapper.read(entity.description, MLText::class.java))
            .withSystem(entity.system)
            .withSourceType(entity.sourceType)
            .withSourceId(entity.sourceId)
            .withSourceRef(RecordRef.valueOf(entity.sourceRef))
            .withMetaRecord(RecordRef.valueOf(entity.metaRecord))
            .withParentRef(RecordRef.valueOf(TypeUtils.getTypeRef(entity.parent?.extId ?: "")))
            .withFormRef(RecordRef.valueOf(entity.form))
            .withJournalRef(RecordRef.valueOf(entity.journal))
            .withBoardRef(RecordRef.valueOf(entity.board))
            .withDashboardType(entity.dashboardType)
            .withInheritForm(entity.inheritForm)
            .withInheritActions(entity.inheritActions)
            .withInheritNumTemplate(entity.inheritNumTemplate)
            .withDispNameTemplate(Json.mapper.read(entity.dispNameTemplate, MLText::class.java))
            .withNumTemplateRef(RecordRef.valueOf(entity.numTemplateRef))
            .withActions(DataValue.create(entity.actions).asList(RecordRef::class.java))
            .withAssociations(DataValue.create(entity.associations).asList(AssocDef::class.java))
            .withDefaultCreateVariant(entity.defaultCreateVariant)
            .withCreateVariants(DataValue.create(entity.createVariants).asList(CreateVariantDef::class.java))
            .withPostCreateActionRef(RecordRef.valueOf(entity.postCreateActionRef))
            .withConfigFormRef(RecordRef.valueOf(entity.configForm))
            .withConfig(ObjectData.create(entity.config))
            .withModel(Json.mapper.read(entity.model, TypeModelDef::class.java))
            .withDocLib(Json.mapper.read(entity.docLib, DocLibDef::class.java))
            .withContentConfig(Json.mapper.read(entity.contentConfig, EcosTypeContentConfig::class.java))
            .withProperties(ObjectData.create(entity.attributes))
            .build()

        return EntityWithMeta(
            typeDef,
            EntityMeta.create {
                withCreated(entity.createdDate)
                withCreator(entity.createdBy ?: "anonymous")
                withModified(entity.lastModifiedDate)
                withModifier(entity.lastModifiedBy)
            }
        )
    }
}
