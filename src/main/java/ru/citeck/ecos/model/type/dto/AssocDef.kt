package ru.citeck.ecos.model.type.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.records2.RecordRef

@IncludeNonDefault
@JsonDeserialize(builder = AssocDef.Builder::class)
data class AssocDef(
    val id: String,
    val name: MLText,
    val attribute: String,
    val target: RecordRef,
    val direction: AssocDirection
) {
    companion object {

        @JvmField
        val EMPTY = create {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): AssocDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): AssocDef {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    open class Builder() {

        var id: String = ""
        var name: MLText = MLText.EMPTY
        var attribute: String = ""
        var target: RecordRef = RecordRef.EMPTY
        var direction: AssocDirection = AssocDirection.TARGET

        constructor(base: AssocDef) : this() {
            withId(base.id)
            withName(base.name)
            withAttribute(base.attribute)
            withTarget(base.target)
            withDirection(base.direction)
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withAttribute(attribute: String?): Builder {
            this.attribute = attribute ?: ""
            return this
        }

        fun withTarget(target: RecordRef?): Builder {
            this.target = target ?: RecordRef.EMPTY
            return this
        }

        fun withDirection(direction: AssocDirection?): Builder {
            this.direction = direction ?: AssocDirection.TARGET
            return this
        }

        fun build(): AssocDef {
            return AssocDef(
                id,
                name,
                attribute,
                target,
                direction
            )
        }
    }
}
