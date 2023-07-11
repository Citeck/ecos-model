package ru.citeck.ecos.model.domain.secret.service

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ecos.com.fasterxml.jackson210.databind.annotation.JsonPOJOBuilder
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncDef

@IncludeNonDefault
@JsonDeserialize(builder = EcosSecretDto.Builder::class)
class EcosSecretDto(
    val id: String,
    val name: MLText,
    val type: String,
    val data: ObjectData?
) {

    companion object {

        @JvmField
        val EMPTY = create {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): EcosSecretDto {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    @JsonPOJOBuilder
    open class Builder() {

        var id: String = ""
        var name: MLText = MLText.EMPTY
        var type: String = ""
        var data: ObjectData? = null

        constructor(base: EcosSecretDto) : this() {
            id = base.id
            name = base.name
            type = base.type
            data = base.data
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withType(type: String?): Builder {
            this.type = type ?: ""
            return this
        }

        fun withData(data: ObjectData?): Builder {
            this.data = data
            return this
        }

        fun build(): EcosSecretDto {
            return EcosSecretDto(
                id = id,
                name = name,
                type = type,
                data = data
            )
        }
    }
}
