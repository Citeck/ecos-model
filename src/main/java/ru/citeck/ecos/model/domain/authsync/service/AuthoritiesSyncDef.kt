package ru.citeck.ecos.model.domain.authsync.service

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.model.lib.authorities.AuthorityType

@IncludeNonDefault
@JsonDeserialize(builder = AuthoritiesSyncDef.Builder::class)
class AuthoritiesSyncDef(
    val id: String,
    val name: MLText,
    val version: Int,
    val type: String,
    val enabled: Boolean,
    val authorityType: AuthorityType,
    val manageNewAuthorities: Boolean,
    val repeatDelayDuration: String,
    val config: ObjectData
) {
    companion object {

        @JvmField
        val EMPTY = create {}

        @JvmStatic
        fun create(): Builder {
            return Builder()
        }

        @JvmStatic
        fun create(builder: Builder.() -> Unit): AuthoritiesSyncDef {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    open class Builder() {

        var id: String = ""
        var name: MLText = MLText.EMPTY
        var version: Int = 0
        var type: String = ""
        var enabled: Boolean = false
        var authorityType: AuthorityType = AuthorityType.PERSON
        var manageNewAuthorities: Boolean = false
        var repeatDelayDuration: String = ""
        var config: ObjectData = ObjectData.create()

        constructor(base: AuthoritiesSyncDef) : this() {
            this.id = base.id
            this.name = base.name
            this.version = base.version
            this.type = base.type
            this.enabled = base.enabled
            this.authorityType = base.authorityType
            this.manageNewAuthorities = base.manageNewAuthorities
            this.repeatDelayDuration = base.repeatDelayDuration
            this.config = base.config.deepCopy()
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withVersion(version: Int?): Builder {
            this.version = version ?: 0
            return this
        }

        fun withType(type: String?): Builder {
            this.type = type ?: ""
            return this
        }

        fun withEnabled(enabled: Boolean?): Builder {
            this.enabled = enabled ?: false
            return this
        }

        fun withAuthorityType(authorityType: AuthorityType?): Builder {
            this.authorityType = authorityType ?: AuthorityType.PERSON
            return this
        }

        fun withManageNewAuthorities(manageNewAuthorities: Boolean?): Builder {
            this.manageNewAuthorities = manageNewAuthorities ?: false
            return this
        }

        fun withRepeatDelayDuration(period: String?): Builder {
            this.repeatDelayDuration = period ?: ""
            return this
        }

        fun withConfig(config: ObjectData?): Builder {
            this.config = config ?: ObjectData.create()
            return this
        }

        fun build(): AuthoritiesSyncDef {
            return AuthoritiesSyncDef(
                id = id,
                name = name,
                version = version,
                type = type,
                enabled = enabled,
                authorityType = authorityType,
                manageNewAuthorities = manageNewAuthorities,
                repeatDelayDuration = repeatDelayDuration,
                config = config
            )
        }
    }
}
