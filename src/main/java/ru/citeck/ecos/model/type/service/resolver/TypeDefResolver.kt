package ru.citeck.ecos.model.type.service.resolver

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.lib.type.dto.*
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.service.utils.EModelTypeUtils
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.AssocDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

@Component
class TypeDefResolver {

    companion object {

        const val DEFAULT_FORM = "DEFAULT_FORM"
        const val DEFAULT_JOURNAL = "DEFAULT_JOURNAL"

        const val ATT_ASSOC_TYPE_REF_KEY = "typeRef"
        const val ATT_ASSOC_CHILD_FLAG_KEY = "child"

        private val log = KotlinLogging.logger {}

        private val TYPES_WITHOUT_CREATE_VARIANTS = setOf(
            "base",
            "user-base",
            "case",
            "data-list"
        )
    }

    fun getResolvedTypesWithMeta(
        types: List<EntityWithMeta<TypeDef>>,
        rawProv: TypesProvider,
        resProv: TypesProvider,
        aspectsProv: AspectsProvider
    ): List<EntityWithMeta<TypeDef>> {
        return doWithMeta(types) { getResolvedTypes(it, rawProv, resProv, aspectsProv) }
    }

    fun getResolvedTypes(
        types: List<TypeDef>,
        rawProv: TypesProvider,
        resProv: TypesProvider,
        aspectsProv: AspectsProvider
    ): List<TypeDef> {
        val context = ResolveContext(rawProv, resProv, aspectsProv)
        val resolvedByParentTypes = types.map {
            getResolvedByParentType(it, context)
        }
        context.resetCache()
        resolvedByParentTypes.forEach {
            it.withCreateVariants(getCreateVariants(it.build(), context))
            it.withAssociations(filterAssociations(it, context))
        }
        return resolvedByParentTypes.map { it.build() }
    }

    private fun getResolvedByParentType(typeDef: TypeDef, context: ResolveContext): TypeDef.Builder {

        val resolvedTypeFromContext = context.types[typeDef.id]
        if (resolvedTypeFromContext != null) {
            return resolvedTypeFromContext
        }

        if (typeDef.id.isBlank() || typeDef.id == "base") {
            val result = typeDef.copy()
            context.types[typeDef.id] = result
            return result
        }

        val resTypeDef = typeDef.copy()

        if (EntityRef.isEmpty(resTypeDef.parentRef) && resTypeDef.id != "base") {
            resTypeDef.withParentRef(ModelUtils.getTypeRef("base"))
        }

        val resolvedParentDef = run {
            val parentTypeId = resTypeDef.parentRef.getLocalId()
            val parentTypeDef = if (parentTypeId.isNotBlank()) {
                context.rawProv.get(parentTypeId) ?: TypeDef.EMPTY
            } else {
                TypeDef.EMPTY
            }
            getResolvedByParentType(parentTypeDef, context)
        }

        resTypeDef.withDocLib(getDocLib(typeDef))
            .withModel(getModel(typeDef, resolvedParentDef, context))

        if (resTypeDef.dashboardType.isBlank()) {
            resTypeDef.withDashboardType(resolvedParentDef.dashboardType)
        }

        if (resTypeDef.defaultStatus.isBlank()) {
            resTypeDef.withDefaultStatus(resolvedParentDef.defaultStatus)
        }

        when ((resTypeDef.storageType)) {
            EModelTypeUtils.STORAGE_TYPE_REFERENCE, // todo: set sourceId based on source ref
            EModelTypeUtils.STORAGE_TYPE_DEFAULT,
            "" -> {
                if (resTypeDef.sourceId.isBlank()) {
                    resTypeDef.withStorageType(EModelTypeUtils.STORAGE_TYPE_DEFAULT)
                    resTypeDef.withSourceId(resolvedParentDef.sourceId)
                } else if (resTypeDef.sourceId == "alfresco/") {
                    resTypeDef.withStorageType(EModelTypeUtils.STORAGE_TYPE_ALFRESCO)
                }
            }
            EModelTypeUtils.STORAGE_TYPE_EMODEL -> {
                if (resTypeDef.sourceId.isBlank()) {
                    resTypeDef.withSourceId(EModelTypeUtils.getEmodelSourceId(resTypeDef.id))
                }
            }
            EModelTypeUtils.STORAGE_TYPE_ALFRESCO -> {
                resTypeDef.withSourceId("alfresco/")
            }
        }
        if (resTypeDef.sourceId.isNotEmpty() && !resTypeDef.sourceId.contains(EntityRef.APP_NAME_DELIMITER)) {
            resTypeDef.withSourceId(EcosModelApp.NAME + EntityRef.APP_NAME_DELIMITER + resTypeDef.sourceId)
        }
        if (EntityRef.isEmpty(resTypeDef.metaRecord)) {
            resTypeDef.withMetaRecord(EntityRef.valueOf(resTypeDef.sourceId + "@"))
        }
        if (MLText.isEmpty(resTypeDef.name)) {
            resTypeDef.withName(MLText(resTypeDef.id))
        }
        if (EntityRef.isEmpty(resTypeDef.numTemplateRef) && resTypeDef.inheritNumTemplate) {
            resTypeDef.withNumTemplate(resolvedParentDef.numTemplateRef)
        }
        if (EntityRef.isEmpty(resTypeDef.formRef) && resTypeDef.inheritForm) {
            resTypeDef.withFormRef(resolvedParentDef.formRef)
        }
        if (resTypeDef.formRef.getLocalId() == DEFAULT_FORM) {
            resTypeDef.withFormRef(resTypeDef.formRef.withLocalId("type$" + resTypeDef.id))
        }
        if (resTypeDef.journalRef.getLocalId() == DEFAULT_JOURNAL) {
            resTypeDef.withJournalRef(resTypeDef.journalRef.withLocalId("type$" + resTypeDef.id))
        }
        val contentConfig = resTypeDef.contentConfig.copy()
        if (contentConfig.path.isBlank()) {
            contentConfig.withPath(resolvedParentDef.contentConfig.path)
        }
        if (contentConfig.previewPath.isBlank()) {
            val parentPreviewPath = resolvedParentDef.contentConfig.previewPath
            if (parentPreviewPath.isNotBlank()) {
                contentConfig.withPreviewPath(parentPreviewPath)
            } else {
                contentConfig.withPath(contentConfig.path)
            }
        }
        resTypeDef.withContentConfig(contentConfig.build())

        if (resTypeDef.inheritActions && resolvedParentDef.actions.isNotEmpty()) {
            val actions = ArrayList(resolvedParentDef.actions)
            resTypeDef.actions.filter {
                !actions.contains(it)
            }.forEach {
                actions.add(it)
            }
            resTypeDef.withActions(actions)
        }
        if (EntityRef.isEmpty(resTypeDef.postCreateActionRef)) {
            resTypeDef.withPostCreateActionRef(resolvedParentDef.postCreateActionRef)
        }
        if (EntityRef.isEmpty(resTypeDef.configFormRef)) {
            resTypeDef.withConfigFormRef(resolvedParentDef.configFormRef)
        }
        if (MLText.isEmpty(resTypeDef.dispNameTemplate)) {
            resTypeDef.withDispNameTemplate(resolvedParentDef.dispNameTemplate)
        }

        if (resolvedParentDef.aspects.isNotEmpty()) {
            val fullAspects = linkedMapOf<EntityRef, TypeAspectDef>()
            resolvedParentDef.aspects.forEach { fullAspects[it.ref] = it }
            resTypeDef.aspects.forEach {
                val newConfig = if (it.inheritConfig && fullAspects.containsKey(it.ref)) {
                    fullAspects[it.ref]?.config
                } else {
                    context.aspectsProv.getAspectInfo(it.ref)?.defaultConfig
                }?.deepCopy() ?: ObjectData.create()
                it.config.forEach { key, value ->
                    newConfig[key] = value
                }
                fullAspects[it.ref] = it.copy { withConfig(newConfig) }
            }
            resTypeDef.withAspects(fullAspects.values.toList())
        }

        resTypeDef.withAssociations(getAssocs(typeDef, resolvedParentDef))

        if (resTypeDef.queryPermsPolicy == QueryPermsPolicy.DEFAULT) {
            resTypeDef.withQueryPermsPolicy(resolvedParentDef.queryPermsPolicy)
        }

        context.types[resTypeDef.id] = resTypeDef
        return resTypeDef
    }

    private fun getAssocs(typeDef: TypeDef, parentTypeDef: TypeDef.Builder): List<AssocDef> {

        val assocs = LinkedHashMap<String, AssocDef>()

        parentTypeDef.associations.forEach { assocs[it.id] = it }

        typeDef.associations.forEach { assocDef ->
            val parentAssoc = assocs[assocDef.id] ?: AssocDef.EMPTY
            val newAssoc = assocDef.copy()
            if (newAssoc.attribute.isBlank()) {
                if (parentAssoc.attribute.isNotBlank()) {
                    newAssoc.withAttribute(parentAssoc.attribute)
                } else {
                    newAssoc.withAttribute(newAssoc.id)
                }
            }
            val attDef = typeDef.model.attributes.find {
                it.id == newAssoc.attribute && it.type == AttributeType.ASSOC
            }
            if (newAssoc.target.isEmpty()) {
                if (parentAssoc.target.isNotEmpty()) {
                    newAssoc.withTarget(parentAssoc.target)
                } else if (attDef != null) {
                    newAssoc.withTarget(EntityRef.valueOf(attDef.config[ATT_ASSOC_TYPE_REF_KEY].asText()))
                }
            }
            if (newAssoc.child == null) {
                if (parentAssoc.child != null) {
                    newAssoc.withChild(parentAssoc.child)
                } else if (attDef != null) {
                    newAssoc.withChild(attDef.config[ATT_ASSOC_CHILD_FLAG_KEY].asBoolean(false))
                }
            }
            if (newAssoc.child == true) {
                newAssoc.withJournalsFromTarget(false)
                newAssoc.withJournals(emptyList())
            }
            if (MLText.isEmpty(newAssoc.name)) {
                if (!MLText.isEmpty(parentAssoc.name)) {
                    newAssoc.withName(parentAssoc.name)
                } else if (attDef != null) {
                    newAssoc.withName(attDef.name)
                }
            }
            assocs[newAssoc.id] = newAssoc.build()
        }

        return assocs.values.toList()
    }

    private fun getModel(typeDef: TypeDef, parentTypeDef: TypeDef.Builder, context: ResolveContext): TypeModelDef {

        val roles = mutableMapOf<String, RoleDef>()
        val statuses = mutableMapOf<String, StatusDef>()
        var attributes = mutableMapOf<String, AttributeDef>()
        var systemAttributes = mutableMapOf<String, AttributeDef>()

        parentTypeDef.model.roles.forEach { roles[it.id] = it }
        parentTypeDef.model.statuses.forEach { statuses[it.id] = it }
        parentTypeDef.model.attributes.forEach { attributes[it.id] = it }
        parentTypeDef.model.systemAttributes.forEach { systemAttributes[it.id] = it }

        typeDef.model.roles.forEach { roles[it.id] = it }
        typeDef.model.statuses.forEach { statuses[it.id] = it }
        typeDef.model.attributes.forEach { attributes[it.id] = it }
        typeDef.model.systemAttributes.forEach { systemAttributes[it.id] = it }

        val stages = typeDef.model.stages.ifEmpty {
            parentTypeDef.model.stages
        }

        if (typeDef.aspects.isNotEmpty()) {
            attributes = addAspectsAttributes(typeDef.aspects, attributes, false, context)
            systemAttributes = addAspectsAttributes(typeDef.aspects, systemAttributes, true, context)
        }

        return TypeModelDef.create {
            withRoles(roles.values.toList())
            withStatuses(statuses.values.toList())
            withStages(stages)
            withAttributes(attributes.values.toList())
            withSystemAttributes(systemAttributes.values.toList())
        }
    }

    private fun addAspectsAttributes(
        aspects: List<TypeAspectDef>,
        attributes: MutableMap<String, AttributeDef>,
        isSystem: Boolean,
        context: ResolveContext
    ): MutableMap<String, AttributeDef> {

        for (aspect in aspects) {

            val aspectInfo = context.aspectsProv.getAspectInfo(aspect.ref) ?: continue

            val aspectAttributes = if (isSystem) {
                aspectInfo.systemAttributes
            } else {
                aspectInfo.attributes
            }
            for (attribute in aspectAttributes) {
                if (attribute.id.isBlank()) {
                    continue
                }
                attributes[attribute.id] = attribute
            }
        }
        return attributes
    }

    private fun getDocLib(typeDef: TypeDef): DocLibDef {
        val docLib = typeDef.docLib
        if (!docLib.enabled) {
            return DocLibDef.EMPTY
        }
        return DocLibDef.create {
            enabled = true
            fileTypeRefs = docLib.fileTypeRefs.ifEmpty {
                listOf(ModelUtils.getTypeRef(typeDef.id))
            }
            dirTypeRef = if (EntityRef.isEmpty(docLib.dirTypeRef)) {
                ModelUtils.DOCLIB_DEFAULT_DIR_TYPE
            } else {
                docLib.dirTypeRef
            }
        }
    }

    /* TYPES POSTPROCESSING */

    private fun getCreateVariants(
        resolvedTypeDef: TypeDef?,
        context: ResolveContext,
        result: MutableList<CreateVariantDef> = ArrayList(),
        addTypeInId: Boolean = false
    ): List<CreateVariantDef> {

        resolvedTypeDef ?: return result

        val typeId = resolvedTypeDef.id
        if (TYPES_WITHOUT_CREATE_VARIANTS.contains(typeId)) {
            return emptyList()
        }
        val rawTypeDef = context.rawProv.get(resolvedTypeDef.id) ?: return result

        val variants = ArrayList<CreateVariantDef>()

        val defaultCreateVariant = resolvedTypeDef.defaultCreateVariant ?: rawTypeDef.createVariants.isEmpty()

        if (defaultCreateVariant && EntityRef.isNotEmpty(resolvedTypeDef.formRef)) {
            variants.add(
                CreateVariantDef.create()
                    .withId("DEFAULT")
                    .build()
            )
        }

        variants.addAll(rawTypeDef.createVariants)

        variants.forEach { cv ->

            val variant = cv.copy()
            if (addTypeInId) {
                variant.withId(typeId + "__" + variant.id)
            }

            if (EntityRef.isEmpty(variant.typeRef)) {
                variant.withTypeRef(ModelUtils.getTypeRef(resolvedTypeDef.id))
            }

            if (MLText.isEmpty(variant.name)) {
                variant.withName(resolvedTypeDef.name)
            }
            if (EntityRef.isEmpty(variant.formRef)) {
                variant.withFormRef(resolvedTypeDef.formRef)
            }
            if (variant.sourceId.isEmpty()) {
                variant.withSourceId(resolvedTypeDef.sourceId)
            }
            if (EntityRef.isEmpty(variant.postActionRef)) {
                variant.withPostActionRef(resolvedTypeDef.postCreateActionRef)
            }
            if (variant.sourceId.isNotEmpty()) {
                result.add(variant.build())
            } else {
                log.debug("Create variant without sourceId will be ignored: " + variant.build())
            }
        }

        if (resolvedTypeDef.createVariantsForChildTypes) {
            context.getChildrenByParentId(resolvedTypeDef.id).forEach { childId ->
                getCreateVariants(context.getResolvedType(childId), context, result, true)
            }
        }

        return result
    }

    private fun filterAssociations(typeDef: TypeDef.Builder, context: ResolveContext): List<AssocDef> {

        return typeDef.associations.mapNotNull {
            if (EntityRef.isEmpty(it.target)) {
                null
            } else {
                if (it.child == true) {
                    it
                } else {
                    val journals = preProcessAssocJournals(
                        it.target,
                        it.journalsFromTarget,
                        it.journals,
                        context
                    )
                    if (journals.isEmpty()) {
                        null
                    } else {
                        it.copy()
                            .withJournals(journals)
                            .build()
                    }
                }
            }
        }
    }

    private fun preProcessAssocJournals(
        target: EntityRef,
        journalsFromTarget: Boolean?,
        journals: List<EntityRef>,
        context: ResolveContext
    ): List<EntityRef> {

        if (journalsFromTarget == false || journals.isNotEmpty() && journalsFromTarget == null) {
            return journals
        }

        val targetTypeDef = context.getResolvedType(target.getLocalId())
        if (EntityRef.isNotEmpty(targetTypeDef.journalRef)) {
            return listOf(targetTypeDef.journalRef)
        }

        val existingJournals = HashSet(journals)
        val result = ArrayList(journals)
        for (childId in context.getChildrenByParentId(target.getLocalId())) {

            val childDef = context.getResolvedType(childId)

            if (EntityRef.isNotEmpty(childDef.journalRef)) {
                if (existingJournals.add(childDef.journalRef)) {
                    result.add(childDef.journalRef)
                }
                continue
            }

            context.getChildrenByParentId(childId).forEach { childChildId ->
                val childChildDef = context.getResolvedType(childChildId)
                val journalRef = childChildDef.journalRef
                if (EntityRef.isNotEmpty(journalRef)) {
                    if (existingJournals.add(journalRef)) {
                        result.add(journalRef)
                    }
                }
            }
        }
        return result
    }

    private fun doWithMeta(
        entities: List<EntityWithMeta<TypeDef>>,
        action: (List<TypeDef>) -> List<TypeDef>
    ): List<EntityWithMeta<TypeDef>> {

        val resTypes = action.invoke(entities.map { it.entity })
        return resTypes.zip(entities) { a, b -> EntityWithMeta(a, b.meta) }
    }

    private class ResolveContext(
        val rawProv: TypesProvider,
        val resProv: TypesProvider,
        val aspectsProv: AspectsProvider
    ) {
        val types: MutableMap<String, TypeDef.Builder> = HashMap()

        private val childrenById: MutableMap<String, List<String>> = HashMap()
        private val resolvedTypes: MutableMap<String, TypeDef> = HashMap()

        fun getResolvedType(id: String): TypeDef {
            return resolvedTypes.computeIfAbsent(id) {
                resProv.get(id) ?: types[id]?.build() ?: TypeDef.EMPTY
            }
        }

        fun getChildrenByParentId(parentId: String): List<String> {
            return childrenById.computeIfAbsent(parentId) { id ->
                val result = LinkedHashSet<String>(rawProv.getChildren(id))
                types.values.filter { it.parentRef.getLocalId() == id }.forEach { result.add(it.id) }
                result.toList()
            }
        }

        fun resetCache() {
            resolvedTypes.clear()
            childrenById.clear()
        }
    }
}
