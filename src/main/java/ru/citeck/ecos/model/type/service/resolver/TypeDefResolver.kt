package ru.citeck.ecos.model.type.service.resolver

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.service.utils.EModelTypeUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.AssocDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

@Component
class TypeDefResolver {
    companion object {
        private val log = KotlinLogging.logger {}

        private val TYPES_WITHOUT_CREATE_VARIANTS_COMPUTATION = setOf(
            "base",
            "user-base",
            "case",
            "data-list"
        )
    }

    fun getResolvedTypesWithMeta(
        types: List<EntityWithMeta<TypeDef>>,
        rawProv: TypesProvider,
        resProv: TypesProvider
    ): List<EntityWithMeta<TypeDef>> {
        return doWithMeta(types) { getResolvedTypes(it, rawProv, resProv) }
    }

    fun getResolvedTypes(
        types: List<TypeDef>,
        rawProv: TypesProvider,
        resProv: TypesProvider
    ): List<TypeDef> {
        val context = ResolveContext(rawProv, resProv)
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

        if (typeDef.id.isBlank() || typeDef.id == "base") {
            return typeDef.copy()
        }

        val resolvedTypeFromContext = context.types[typeDef.id]
        if (resolvedTypeFromContext != null) {
            return resolvedTypeFromContext
        }

        val resTypeDef = typeDef.copy()

        if (RecordRef.isEmpty(resTypeDef.parentRef) && resTypeDef.id != "base") {
            resTypeDef.withParentRef(TypeUtils.getTypeRef("base"))
        }

        val resolvedParentDef = context.types.computeIfAbsent(resTypeDef.parentRef.id) {
            val parentTypeDef = if (it.isNotBlank()) {
                context.rawProv.get(it) ?: TypeDef.EMPTY
            } else {
                TypeDef.EMPTY
            }
            getResolvedByParentType(parentTypeDef, context)
        }

        resTypeDef.withDocLib(getDocLib(typeDef))
            .withModel(getModel(typeDef, resolvedParentDef))

        if (resTypeDef.dashboardType.isBlank()) {
            resTypeDef.withDashboardType(resolvedParentDef.dashboardType)
        }

        when ((resTypeDef.sourceType ?: "").ifBlank { EModelTypeUtils.STORAGE_TYPE_DEFAULT }) {
            EModelTypeUtils.STORAGE_TYPE_REFERENCE, //todo: set sourceId based on ref
            EModelTypeUtils.STORAGE_TYPE_DEFAULT -> {
                resTypeDef.withSourceType(EModelTypeUtils.STORAGE_TYPE_DEFAULT)
                if (resTypeDef.sourceId.isBlank()) {
                    resTypeDef.withSourceId(resolvedParentDef.sourceId)
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
        if (!resTypeDef.sourceId.contains(EntityRef.APP_NAME_DELIMITER)) {
            resTypeDef.withSourceId(EcosModelApp.NAME + EntityRef.APP_NAME_DELIMITER + resTypeDef.sourceId)
        }
        if (RecordRef.isEmpty(resTypeDef.metaRecord)) {
            resTypeDef.withMetaRecord(RecordRef.valueOf(resTypeDef.sourceId + "@"))
        }
        if (MLText.isEmpty(resTypeDef.name)) {
            resTypeDef.withName(MLText(resTypeDef.id))
        }
        if (RecordRef.isEmpty(resTypeDef.numTemplateRef) && resTypeDef.inheritNumTemplate) {
            resTypeDef.withNumTemplate(resolvedParentDef.numTemplateRef)
        }
        if (RecordRef.isEmpty(resTypeDef.formRef) && resTypeDef.inheritForm) {
            resTypeDef.withFormRef(resolvedParentDef.formRef)
        }
        if (resTypeDef.formRef.id == "DEFAULT_FORM") {
            resTypeDef.withFormRef(resTypeDef.formRef.withId("type$" + resTypeDef.id))
        }
        if (resTypeDef.journalRef.id == "DEFAULT_JOURNAL") {
            resTypeDef.withJournalRef(resTypeDef.journalRef.withId("type$" + resTypeDef.id))
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
        if (RecordRef.isEmpty(resTypeDef.postCreateActionRef)) {
            resTypeDef.withPostCreateActionRef(resolvedParentDef.postCreateActionRef)
        }
        if (RecordRef.isEmpty(resTypeDef.configFormRef)) {
            resTypeDef.withConfigFormRef(resolvedParentDef.configFormRef)
        }
        if (MLText.isEmpty(resTypeDef.dispNameTemplate)) {
            resTypeDef.withDispNameTemplate(resolvedParentDef.dispNameTemplate)
        }
        resTypeDef.withAssociations(getAssocs(typeDef, resolvedParentDef))

        return resTypeDef
    }

    private fun getAssocs(typeDef: TypeDef, parentTypeDef: TypeDef.Builder): List<AssocDef> {

        val assocs = LinkedHashMap<String, AssocDef>()

        parentTypeDef.associations.forEach { assocs[it.id] = it }
        typeDef.associations.forEach { assocs[it.id] = it }

        return assocs.values.toList()
    }

    private fun getModel(typeDef: TypeDef, parentTypeDef: TypeDef.Builder): TypeModelDef {

        val roles = mutableMapOf<String, RoleDef>()
        val statuses = mutableMapOf<String, StatusDef>()
        val attributes = mutableMapOf<String, AttributeDef>()
        val systemAttributes = mutableMapOf<String, AttributeDef>()

        fun apply(model: TypeModelDef) {
            model.roles.forEach { roles[it.id] = it }
            model.statuses.forEach { statuses[it.id] = it }
            model.attributes.forEach { attributes[it.id] = it }
            model.systemAttributes.forEach { systemAttributes[it.id] = it }
        }

        apply(parentTypeDef.model)
        apply(typeDef.model)

        return TypeModelDef.create {
            withRoles(roles.values.toList())
            withStatuses(statuses.values.toList())
            withAttributes(attributes.values.toList())
            withSystemAttributes(systemAttributes.values.toList())
        }
    }

    private fun getDocLib(typeDef: TypeDef): DocLibDef {
        val docLib = typeDef.docLib
        if (!docLib.enabled) {
            return DocLibDef.EMPTY
        }
        return DocLibDef.create {
            enabled = true
            fileTypeRefs = docLib.fileTypeRefs.ifEmpty {
                listOf(TypeUtils.getTypeRef(typeDef.id))
            }
            dirTypeRef = if (RecordRef.isEmpty(docLib.dirTypeRef)) {
                TypeUtils.DOCLIB_DEFAULT_DIR_TYPE
            } else {
                docLib.dirTypeRef
            }
        }
    }

    /* TYPES POSTPROCESSING */

    private fun getCreateVariants(
        typeDef: TypeDef?,
        context: ResolveContext,
        result: MutableList<CreateVariantDef> = ArrayList()
    ): List<CreateVariantDef> {

        typeDef ?: return result

        val typeId = typeDef.id
        if (TYPES_WITHOUT_CREATE_VARIANTS_COMPUTATION.contains(typeId)) {
            return typeDef.createVariants
        }

        val variants = ArrayList<CreateVariantDef>()

        val defaultCreateVariant = typeDef.defaultCreateVariant ?: typeDef.createVariants.isEmpty()

        if (defaultCreateVariant && RecordRef.isNotEmpty(typeDef.formRef)) {
            variants.add(
                CreateVariantDef.create()
                    .withId("DEFAULT")
                    .build()
            )
        }

        variants.addAll(typeDef.createVariants)

        variants.forEach { cv ->

            val variant = cv.copy()
            if (RecordRef.isEmpty(variant.typeRef)) {
                variant.withTypeRef(TypeUtils.getTypeRef(typeDef.id))
            }

            if (MLText.isEmpty(variant.name)) {
                variant.withName(typeDef.name)
            }
            if (RecordRef.isEmpty(variant.formRef)) {
                variant.withFormRef(typeDef.formRef)
            }
            if (variant.sourceId.isEmpty()) {
                variant.withSourceId(typeDef.sourceId)
            }
            if (RecordRef.isEmpty(variant.postActionRef)) {
                variant.withPostActionRef(typeDef.postCreateActionRef)
            }
            if (variant.sourceId.isNotEmpty()) {
                result.add(variant.build())
            } else {
                log.debug("Create variant without sourceId will be ignored: " + variant.build())
            }
        }

        context.getChildrenByParentId(typeDef.id).forEach { childId ->
            getCreateVariants(context.getResolvedType(childId), context, result)
        }

        return result
    }

    private fun filterAssociations(typeDef: TypeDef.Builder, context: ResolveContext): List<AssocDef> {

        return typeDef.associations.mapNotNull {
            if (RecordRef.isEmpty(it.target)) {
                null
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
                        .withAttribute(it.attribute.ifBlank { it.id })
                        .build()
                }
            }
        }
    }

    private fun preProcessAssocJournals(
        target: RecordRef,
        journalsFromTarget: Boolean?,
        journals: List<RecordRef>,
        context: ResolveContext
    ): List<RecordRef> {

        if (journalsFromTarget == false || journals.isNotEmpty() && journalsFromTarget == null) {
            return journals
        }

        val targetTypeDef = context.getResolvedType(target.id)
        if (RecordRef.isNotEmpty(targetTypeDef.journalRef)) {
            return listOf(targetTypeDef.journalRef)
        }

        val existingJournals = HashSet(journals)
        val result = ArrayList(journals)
        for (childId in context.getChildrenByParentId(target.id)) {

            val childDef = context.getResolvedType(childId)

            if (RecordRef.isNotEmpty(childDef.journalRef)) {
                if (existingJournals.add(childDef.journalRef)) {
                    result.add(childDef.journalRef)
                }
                continue
            }

            context.getChildrenByParentId(childId).forEach { childChildId ->
                val childChildDef = context.getResolvedType(childChildId)
                val journalRef = childChildDef.journalRef
                if (RecordRef.isNotEmpty(journalRef)) {
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
                types.values.filter { it.parentRef.id == id }.forEach { result.add(it.id) }
                result.toList()
            }
        }

        fun resetCache() {
            resolvedTypes.clear()
            childrenById.clear()
        }
    }
}
