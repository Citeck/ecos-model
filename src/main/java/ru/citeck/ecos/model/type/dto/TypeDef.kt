package ru.citeck.ecos.model.type.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordRef

@IncludeNonDefault
@JsonDeserialize(builder = TypeDef.Builder::class)
data class TypeDef(

    val id: String,
    val name: MLText,
    val description: MLText,

    val system: Boolean,

    val sourceId: String,
    val metaRecord: RecordRef,

    val parentRef: RecordRef,
    val formRef: RecordRef,
    val journalRef: RecordRef,

    val dashboardType: String,

    val inheritForm: Boolean,
    val inheritActions: Boolean,
    val inheritNumTemplate: Boolean,

    val dispNameTemplate: MLText,
    val numTemplateRef: RecordRef,

    val actions: List<RecordRef>,

    /* create */
    val defaultCreateVariant: Boolean,
    val createVariants: List<CreateVariantDef>,
    val postCreateActionRef: RecordRef,

    /* config */
    val configFormRef: RecordRef,
    val config: ObjectData,

    val model: TypeModelDef,
    val docLib: DocLibDef,

    val properties: ObjectData
) {

    companion object {

        @JvmField
        val EMPTY = create {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): TypeDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): TypeDef {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    class Builder() {

        var id: String = ""
        var name: MLText = MLText.EMPTY
        var description: MLText = MLText.EMPTY

        var system: Boolean = false

        var sourceId: String = ""
        var metaRecord: RecordRef = RecordRef.EMPTY

        var parentRef: RecordRef = RecordRef.EMPTY
        var formRef: RecordRef = RecordRef.EMPTY
        var journalRef: RecordRef = RecordRef.EMPTY

        var dashboardType: String = ""

        var inheritForm: Boolean = false
        var inheritActions: Boolean = true
        var inheritNumTemplate: Boolean = false

        var dispNameTemplate: MLText = MLText.EMPTY
        var numTemplateRef: RecordRef = RecordRef.EMPTY

        var actions: List<RecordRef> = emptyList()

        /* create */
        var defaultCreateVariant: Boolean = true
        var createVariants: List<CreateVariantDef> = emptyList()
        var postCreateActionRef: RecordRef = RecordRef.EMPTY

        /* config */
        var configFormRef: RecordRef = RecordRef.EMPTY
        var config: ObjectData = ObjectData.create()

        var model: TypeModelDef = TypeModelDef.EMPTY
        var docLib: DocLibDef = DocLibDef.EMPTY

        var properties: ObjectData = ObjectData.create()

        constructor(base: TypeDef) : this() {

            this.id = base.id
            this.name = base.name
            this.description = base.description

            this.system = base.system

            this.sourceId = base.sourceId
            this.metaRecord = base.metaRecord

            this.parentRef = base.parentRef
            this.formRef = base.formRef
            this.journalRef = base.journalRef

            this.dashboardType = base.dashboardType

            this.inheritForm = base.inheritForm
            this.inheritActions = base.inheritActions
            this.inheritNumTemplate = base.inheritNumTemplate

            this.dispNameTemplate = base.dispNameTemplate
            this.numTemplateRef = base.numTemplateRef

            this.actions = DataValue.create(base.actions).asList(RecordRef::class.java)

            /* create */
            this.defaultCreateVariant = base.defaultCreateVariant
            this.createVariants = DataValue.create(base.createVariants).asList(CreateVariantDef::class.java)
            this.postCreateActionRef = base.postCreateActionRef

            /* config */
            this.configFormRef = base.configFormRef
            this.config = ObjectData.deepCopyOrNew(base.config)

            this.model = base.model
            this.docLib = base.docLib

            this.properties = ObjectData.deepCopyOrNew(base.properties)
        }

        fun withId(id: String): Builder {
            this.id = id
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withDescription(description: MLText?): Builder {
            this.description = description ?: MLText.EMPTY
            return this
        }


        fun withSystem(system: Boolean?): Builder {
            this.system = system == true
            return this
        }


        fun withSourceId(sourceId: String?): Builder {
            this.sourceId = sourceId ?: ""
            return this
        }

        fun withMetaRecord(metaRecord: RecordRef?): Builder {
            this.metaRecord = RecordRef.valueOf(metaRecord)
            return this
        }

        fun withParentRef(parentRef: RecordRef?): Builder {
            this.parentRef = RecordRef.valueOf(parentRef)
            if (RecordRef.isEmpty(this.parentRef) && this.id != "base") {
                this.parentRef = TypeUtils.getTypeRef("base")
            }
            return this
        }

        fun withFormRef(formRef: RecordRef?): Builder {
            this.formRef = RecordRef.valueOf(formRef)
            return this
        }

        fun withJournalRef(journalRef: RecordRef?): Builder {
            this.journalRef = RecordRef.valueOf(journalRef)
            return this
        }


        fun withDashboardType(dashboardType: String?): Builder {
            this.dashboardType = dashboardType ?: ""
            return this
        }


        fun withInheritForm(inheritForm: Boolean?): Builder {
            this.inheritForm = inheritForm == true
            return this
        }

        fun withInheritActions(inheritActions: Boolean?): Builder {
            this.inheritActions = inheritActions != false
            return this
        }

        fun withInheritNumTemplate(inheritNumTemplate: Boolean?): Builder {
            this.inheritNumTemplate = inheritNumTemplate == true
            return this
        }


        fun withDispNameTemplate(dispNameTemplate: MLText?): Builder {
            this.dispNameTemplate = dispNameTemplate ?: MLText.EMPTY
            return this
        }

        fun withNumTemplateRef(numTemplateRef: RecordRef?): Builder {
            this.numTemplateRef = RecordRef.valueOf(numTemplateRef)
            return this
        }

        fun withActions(actions: List<RecordRef>?): Builder {
            this.actions = actions ?: emptyList()
            return this
        }

        /* create */
        fun withDefaultCreateVariant(defaultCreateVariant: Boolean?): Builder {
            this.defaultCreateVariant = defaultCreateVariant != false
            return this
        }

        fun withCreateVariants(createVariants: List<CreateVariantDef>?): Builder {
            this.createVariants = createVariants?.filter { it.id.isNotBlank() } ?: emptyList()
            return this
        }

        fun withPostCreateActionRef(postCreateActionRef: RecordRef?): Builder {
            this.postCreateActionRef = RecordRef.valueOf(postCreateActionRef)
            return this
        }

        /* config */
        fun withConfigFormRef(configFormRef: RecordRef?): Builder {
            this.configFormRef = RecordRef.valueOf(configFormRef)
            return this
        }

        fun withConfig(config: ObjectData?): Builder {
            this.config = config ?: ObjectData.create()
            return this
        }

        fun withModel(model: TypeModelDef?): Builder {
            this.model = model ?: TypeModelDef.EMPTY
            return this
        }

        fun withDocLib(docLib: DocLibDef?): Builder {
            this.docLib = docLib ?: DocLibDef.EMPTY
            return this
        }


        fun withProperties(properties: ObjectData?): Builder {
            this.properties = properties ?: ObjectData.create()
            return this
        }

        fun build(): TypeDef {
            return TypeDef(
                id,
                name,
                description,
                system,
                sourceId,
                metaRecord,
                parentRef,
                formRef,
                journalRef,
                dashboardType,
                inheritForm,
                inheritActions,
                inheritNumTemplate,
                dispNameTemplate,
                numTemplateRef,
                actions,
                defaultCreateVariant,
                createVariants,
                postCreateActionRef,
                configFormRef,
                config,
                model,
                docLib,
                properties
            )
        }
    }
}
