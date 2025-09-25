package ru.citeck.ecos.model.num.dto

import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef

@IncludeNonDefault
open class NumTemplateDto {

    var id = ""
    var name = ""
    var workspace = ""
    var counterKey = ""

    constructor()

    constructor(id: String) {
        this.id = id
    }

    constructor(other: NumTemplateDef) {
        this.id = other.id
        this.name = other.name
        this.workspace = other.workspace
        this.counterKey = other.counterKey
    }

    constructor(other: NumTemplateDto) {
        this.id = other.id
        this.name = other.name
        this.workspace = other.workspace
        this.counterKey = other.counterKey
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as NumTemplateDto
        return id == other.id &&
            name == other.name &&
            workspace == other.workspace &&
            counterKey == other.counterKey
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + workspace.hashCode()
        result = 31 * result + counterKey.hashCode()
        return result
    }

    override fun toString(): String {
        return "NumTemplateDto(" +
            "id='$id', " +
            "name='$name', " +
            "workspace='$workspace', " +
            "counterKey='$counterKey')"
    }
}
