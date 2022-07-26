package ru.citeck.ecos.model.type.dto

import ecos.com.fasterxml.jackson210.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import com.fasterxml.jackson.databind.annotation.JsonDeserialize as JackJsonDeserialize

@IncludeNonDefault
@JsonDeserialize(builder = EcosTypeContentConfig.Builder::class)
@JackJsonDeserialize(builder = EcosTypeContentConfig.Builder::class)
data class EcosTypeContentConfig(
    val path: String,
    val previewPath: String
) {

    companion object {

        @JvmField
        val EMPTY = create {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): EcosTypeContentConfig {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    fun copy(): Builder {
        return Builder(this)
    }

    fun copy(builder: Builder.() -> Unit): EcosTypeContentConfig {
        val builderObj = Builder(this)
        builder.invoke(builderObj)
        return builderObj.build()
    }

    class Builder() {

        var path: String = ""
        var previewPath: String = ""

        constructor(base: EcosTypeContentConfig) : this() {
            this.path = base.path
            this.previewPath = base.previewPath
        }

        fun withPath(path: String?): Builder {
            this.path = path ?: ""
            return this
        }

        fun withPreviewPath(previewPath: String?): Builder {
            this.previewPath = previewPath ?: ""
            return this
        }

        fun build(): EcosTypeContentConfig {
            return EcosTypeContentConfig(path, previewPath)
        }
    }
}
