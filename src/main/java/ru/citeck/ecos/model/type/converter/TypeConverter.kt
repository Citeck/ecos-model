package ru.citeck.ecos.model.type.converter

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.type.dto.*
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.dao.TypeRepoDao
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMeta
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin
import ru.citeck.ecos.webapp.api.entity.EntityRef
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

        if (EntityRef.isEmpty(typeDef.parentRef)) {

            entity.parent = null
        } else {

            val parentEntity = typeRepoDao.findByExtId(typeDef.parentRef.getLocalId())
                ?: error("Parent type is not found: ${typeDef.parentRef.getLocalId()}")
            entity.parent = parentEntity
        }

        entity.name = Json.mapper.toString(typeDef.name)
        entity.description = Json.mapper.toString(typeDef.description)
        entity.system = typeDef.system
        entity.sourceType = typeDef.storageType
        entity.sourceId = typeDef.sourceId
        entity.sourceRef = typeDef.sourceRef.toString()
        entity.metaRecord = typeDef.metaRecord.toString()
        entity.form = typeDef.formRef.toString()
        entity.journal = typeDef.journalRef.toString()
        entity.defaultStatus = typeDef.defaultStatus
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
        entity.createVariantsForChildTypes = typeDef.createVariantsForChildTypes
        entity.postCreateActionRef = typeDef.postCreateActionRef.toString()
        entity.configForm = typeDef.configFormRef.toString()
        entity.config = Json.mapper.toString(typeDef.config)
        entity.model = Json.mapper.toString(typeDef.model)
        entity.docLib = Json.mapper.toString(typeDef.docLib)
        entity.attributes = Json.mapper.toString(typeDef.properties)
        entity.contentConfig = Json.mapper.toString(typeDef.contentConfig)
        entity.aspects = Json.mapper.toString(typeDef.aspects)
        entity.queryPermsPolicy = typeDef.queryPermsPolicy
        entity.assignablePerms = Json.mapper.toString(typeDef.assignablePerms)

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
            .withStorageType(entity.sourceType)
            .withSourceId(entity.sourceId)
            .withSourceRef(EntityRef.valueOf(entity.sourceRef))
            .withMetaRecord(EntityRef.valueOf(entity.metaRecord))
            .withParentRef(EntityRef.valueOf(TypeUtils.getTypeRef(entity.parent?.extId ?: "")))
            .withFormRef(EntityRef.valueOf(entity.form))
            .withJournalRef(EntityRef.valueOf(entity.journal))
            .withDefaultStatus(entity.defaultStatus)
            .withBoardRef(EntityRef.valueOf(entity.board))
            .withDashboardType(entity.dashboardType)
            .withInheritForm(entity.inheritForm)
            .withInheritActions(entity.inheritActions)
            .withInheritNumTemplate(entity.inheritNumTemplate)
            .withDispNameTemplate(Json.mapper.read(entity.dispNameTemplate, MLText::class.java))
            .withNumTemplateRef(EntityRef.valueOf(entity.numTemplateRef))
            .withActions(DataValue.create(entity.actions).asList(EntityRef::class.java))
            .withAssociations(DataValue.create(entity.associations).asList(AssocDef::class.java))
            .withDefaultCreateVariant(entity.defaultCreateVariant)
            .withCreateVariants(DataValue.create(entity.createVariants).asList(CreateVariantDef::class.java))
            .withCreateVariantsForChildTypes(entity.createVariantsForChildTypes)
            .withPostCreateActionRef(EntityRef.valueOf(entity.postCreateActionRef))
            .withConfigFormRef(EntityRef.valueOf(entity.configForm))
            .withConfig(ObjectData.create(entity.config))
            .withModel(Json.mapper.read(entity.model, TypeModelDef::class.java))
            .withDocLib(Json.mapper.read(entity.docLib, DocLibDef::class.java))
            .withContentConfig(Json.mapper.read(entity.contentConfig, TypeContentConfig::class.java))
            .withProperties(ObjectData.create(entity.attributes))
            .withAspects(DataValue.create(entity.aspects).asList(TypeAspectDef::class.java))
            .withQueryPermsPolicy(entity.queryPermsPolicy)
            .withAssignablePerms(DataValue.create(entity.assignablePerms).asList(EntityRef::class.java))
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
