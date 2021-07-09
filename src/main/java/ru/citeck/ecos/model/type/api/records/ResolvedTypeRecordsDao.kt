package ru.citeck.ecos.model.type.api.records

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.dto.AssocDef
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.model.type.service.TypeService
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.records3.record.request.RequestContext

@Component
class ResolvedTypeRecordsDao(
    val typeService: TypeService,
    val typeRecordsDao: TypeRecordsDao
) : AbstractRecordsDao(), RecordsQueryDao, RecordAttsDao {

    companion object {
        const val ID = "rtype"

        private val log = KotlinLogging.logger {}
    }

    override fun getId() = ID

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<ResolvedTypeRecord> {
        val records = typeRecordsDao.queryRecords(recsQuery)
        return records.withRecords { ResolvedTypeRecord(it, typeService) }
    }

    override fun getRecordAtts(recordId: String): ResolvedTypeRecord? {
        return typeRecordsDao.getRecordAtts(recordId)?.let { ResolvedTypeRecord(it, typeService) }
    }

    fun getResolvedTypeRecord(typeId: String) : ResolvedTypeRecord {
        val ctx = RequestContext.getCurrentNotNull()
        val resolvedRecById = ctx.getMap<String, ResolvedTypeRecord>(
            ResolvedTypeRecordsDao::class.java.name + ".resolvedRecById"
        )
        return resolvedRecById.computeIfAbsent(typeId) {
            getRecordAtts(it) ?: ResolvedTypeRecord(
                TypeRecordsDao.TypeRecord(TypeDef.EMPTY, typeService),
                typeService
            )
        }
    }

    class ResolvedTypeRecord(
        @AttName("...")
        val typeRec: TypeRecordsDao.TypeRecord,
        val typeService: TypeService
    ) {

        private val typeDefById: MutableMap<String, TypeDef>
        private val childrenById: MutableMap<String, List<String>>
        private val resolvedRecById: MutableMap<String, ResolvedTypeRecord>

        init {
            val ctx = RequestContext.getCurrentNotNull()
            typeDefById = ctx.getMap(ResolvedTypeRecordsDao::class.java.name + ".typeDefById")
            childrenById = ctx.getMap(ResolvedTypeRecordsDao::class.java.name + ".childrenById")
            resolvedRecById = ctx.getMap(ResolvedTypeRecordsDao::class.java.name + ".resolvedRecById")

            resolvedRecById[typeRec.typeDef.id] = this
            typeDefById[typeRec.typeDef.id] = typeRec.typeDef
        }

        fun getDispNameTemplate(): MLText {
            return getFirstByAscTypes {
                val dispNameTemplate = it.dispNameTemplate
                if (MLText.isEmpty(dispNameTemplate)) {
                    null
                } else {
                    dispNameTemplate
                }
            } ?: MLText.EMPTY
        }

        fun getParentRef(): RecordRef {
            var parentRef = typeRec.typeDef.parentRef
            if (parentRef.id.isBlank() && typeRec.typeDef.id != "base") {
                parentRef = TypeUtils.getTypeRef("base")
            }
            if (parentRef.id.isBlank()) {
                return RecordRef.EMPTY
            }
            return RecordRef.create(parentRef.appName, ID, parentRef.id)
        }

        fun getAssociations(): List<AssocDef> {
            val assocs = LinkedHashMap<String, AssocDef>()
            forEachAscInv({ true }) { typeDef ->
                typeDef.associations.forEach { assocs[it.id] = it }
            }
            return assocs.values.toList()
        }

        fun getParents(): List<RecordRef> {
            return typeRec.getParents().map {
                RecordRef.create(it.appName, ID, it.id)
            }
        }

        fun getChildren(): List<RecordRef> {
            return typeRec.getChildren().map {
                RecordRef.create(it.appName, ID, it.id)
            }
        }

        fun getFormRef(): RecordRef {
            return getInhRecordRef({ it.inheritForm }) { it.formRef }
        }

        fun getSourceId(): String {
            return getFirstByAscTypes {
                val sourceId = it.sourceId
                if (sourceId.isNotBlank()) {
                    sourceId
                } else {
                    null
                }
            } ?: ""
        }

        fun getProperties(): ObjectData {
            val props = ObjectData.create()
            forEachAscInv({ true }) {
                it.properties.forEach { k, v -> props.set(k, v) }
            }
            return props
        }

        fun getName(): MLText {
            val name = typeRec.typeDef.name
            if (MLText.isEmpty(name)) {
                return MLText(typeRec.typeDef.id)
            }
            return name
        }

        fun getMetaRecord(): RecordRef {
            val metaRecord = typeRec.typeDef.metaRecord
            if (RecordRef.isEmpty(metaRecord)) {
                return RecordRef.valueOf(getSourceId() + "@")
            }
            return metaRecord
        }

        fun getDashboardType(): String {
            return getFirstByAscTypes {
                val dashboardType = it.dashboardType
                dashboardType.ifBlank { null }
            } ?: ""
        }

        fun getNumTemplateRef(): RecordRef {
            return getInhRecordRef({ it.inheritNumTemplate }) { it.numTemplateRef }
        }

        fun getActions(): List<RecordRef> {

            val actions = ArrayList<RecordRef>()
            val actionsSet = HashSet<RecordRef>()

            forEachAscInv({ it.inheritActions }) { typeDef ->
                typeDef.actions.forEach {
                    if (actionsSet.add(it)) {
                        actions.add(it)
                    }
                }
            }

            return actions
        }

        fun getPostCreateActionRef(): RecordRef {
            return getInhRecordRef { it.postCreateActionRef }
        }

        fun getConfigFormRef() : RecordRef {
            return getInhRecordRef { it.configFormRef }
        }

        fun getCreateVariants(): List<CreateVariantDef> {

            val typeId = typeRec.typeDef.id
            if (typeId == "base" || typeId == "user-base" || typeId == "case" || typeId == "data-list") {
                return typeRec.typeDef.createVariants
            }

            val result = ArrayList<CreateVariantDef>()

            forEachDescTypes(typeRec.typeDef.id) { typeDef ->

                val variants = ArrayList<CreateVariantDef>()

                val defaultCreateVariant = typeDef.defaultCreateVariant ?: typeDef.createVariants.isEmpty()

                if (defaultCreateVariant && RecordRef.isNotEmpty(typeDef.formRef)) {
                    variants.add(CreateVariantDef.create()
                        .withId("DEFAULT")
                        .build())
                }

                variants.addAll(typeDef.createVariants)

                variants.forEach { cv ->

                    val variant = cv.copy()
                    if (RecordRef.isEmpty(variant.typeRef)) {
                        variant.withTypeRef(TypeUtils.getTypeRef(typeDef.id))
                    }
                    val resolvedCVTypeRef = getResolvedRecById(typeDef.id)

                    if (MLText.isEmpty(variant.name)) {
                        variant.withName(resolvedCVTypeRef.getName())
                    }
                    if (RecordRef.isEmpty(variant.formRef)) {
                        variant.withFormRef(resolvedCVTypeRef.getFormRef())
                    }
                    if (variant.sourceId.isEmpty()) {
                        variant.withSourceId(resolvedCVTypeRef.getSourceId())
                    }
                    if (RecordRef.isEmpty(variant.postActionRef)) {
                        variant.withPostActionRef(resolvedCVTypeRef.getPostCreateActionRef());
                    }
                    if (variant.sourceId.isNotEmpty()) {
                        result.add(variant.build());
                    } else {
                        log.warn("Create variant without sourceId will be ignored: " + variant.build());
                    }
                }
            }

            return result
        }

        fun getModel(): TypeModelDef {

            val roles = mutableMapOf<String, RoleDef>()
            val statuses = mutableMapOf<String, StatusDef>()
            val attributes = mutableMapOf<String, AttributeDef>()

            forEachAscInv({ true }) { typeDef ->
                typeDef.model.roles.forEach { roles[it.id] = it }
                typeDef.model.statuses.forEach { statuses[it.id] = it }
                typeDef.model.attributes.forEach { attributes[it.id] = it }
            }

            return TypeModelDef.create {
                withRoles(roles.values.toList())
                withStatuses(statuses.values.toList())
                withAttributes(attributes.values.toList())
            }
        }

        fun getDocLib(): DocLibDef {

            val docLib = typeRec.typeDef.docLib
            if (!docLib.enabled) {
                return DocLibDef.EMPTY
            }

            return DocLibDef.create {
                enabled = true
                fileTypeRefs = if (docLib.fileTypeRefs.isEmpty()) {
                    listOf(TypeUtils.getTypeRef(typeRec.typeDef.id))
                } else {
                    docLib.fileTypeRefs
                }
                dirTypeRef = if (RecordRef.isEmpty(docLib.dirTypeRef)) {
                    TypeUtils.DOCLIB_DEFAULT_DIR_TYPE
                } else {
                    docLib.dirTypeRef
                }
            }
        }

        private fun getInhRecordRef(getRef: (TypeDef) -> RecordRef): RecordRef {
            return getInhRecordRef({ true }, getRef)
        }

        private fun getInhRecordRef(continueCondition: (TypeDef) -> Boolean,
                                    getRef: (TypeDef) -> RecordRef): RecordRef {

            return getFirstByAscTypes {
                val ref = getRef.invoke(it)
                if (RecordRef.isNotEmpty(ref)) {
                    ref
                } else {
                    if (continueCondition.invoke(it)) {
                        null
                    } else {
                        RecordRef.EMPTY
                    }
                }
            } ?: RecordRef.EMPTY
        }

        private fun forEachAscInv(ascWhile: (TypeDef) -> Boolean, action: (TypeDef) -> Unit) {

            val ascTypes = mutableListOf<TypeDef>()
            getFirstByAscTypes {
                ascTypes.add(it);
                if (ascWhile.invoke(it)) {
                    null
                } else {
                    true
                }
            }
            for (i in ascTypes.lastIndex downTo 0) {
                action.invoke(ascTypes[i])
            }
        }

        private fun <K : Any> getFirstByAscTypes(action: (TypeDef) -> K?): K? {

            var itTypeDef: TypeDef? = typeRec.typeDef

            while (itTypeDef != null) {

                val res = action.invoke(itTypeDef)
                if (res != null) {
                    return res
                }
                val parentId = itTypeDef.parentRef.id
                val parentTypeDef = getTypeDefById(parentId)

                itTypeDef = if (parentTypeDef == null && itTypeDef.id != "base") {
                    getTypeDefById("base")
                } else {
                    parentTypeDef
                }
            }
            return null
        }

        private fun forEachDescTypes(typeId: String, action: (TypeDef) -> Unit) {

            val typeDef = getTypeDefById(typeId) ?: return
            action.invoke(typeDef)

            val children = getChildrenById(typeDef.id)
            for (child in children) {
                forEachDescTypes(child, action)
            }
        }

        private fun getTypeDefById(id: String): TypeDef? {
            if (id.isBlank()) {
                return null
            }
            val res = typeDefById.computeIfAbsent(id) {
                typeService.getByIdOrNull(id) ?: TypeDef.EMPTY
            }
            return if (res === TypeDef.EMPTY) {
                null
            } else {
                res
            }
        }

        private fun getChildrenById(id: String): List<String> {
            return childrenById.computeIfAbsent(id) { typeService.getChildren(it) }
        }

        private fun getResolvedRecById(id: String): ResolvedTypeRecord {
            return resolvedRecById.computeIfAbsent(id) {
                val typeDef = TypeRecordsDao.TypeRecord(getTypeDefById(id)
                    ?: TypeDef.EMPTY, typeService)
                ResolvedTypeRecord(typeDef, typeService)
            }
        }
    }
}
