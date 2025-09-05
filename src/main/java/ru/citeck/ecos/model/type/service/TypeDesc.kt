package ru.citeck.ecos.model.type.service

object TypeDesc {

    const val ASPECT_CONFIG_ATT_PREFIX = "aspectCfg$"
    const val ASPECT_CONFIG_ADDED_FLAG = "added"

    val NON_CUSTOM_ASPECTS = setOf("listview", "doclib")

    fun parseAspectCfgKey(key: String): AspectCfgKey? {

        if (!key.startsWith(ASPECT_CONFIG_ATT_PREFIX)) {
            return null
        }
        val aspectIdDelimIdx = key.indexOf("$", ASPECT_CONFIG_ATT_PREFIX.length)
        if (aspectIdDelimIdx < 1) {
            return null
        }
        val aspectId = key.substring(ASPECT_CONFIG_ATT_PREFIX.length, aspectIdDelimIdx)
        val configPropKey = key.substring(aspectIdDelimIdx + 1)

        return AspectCfgKey(aspectId, configPropKey)
    }

    class AspectCfgKey(
        val aspectId: String,
        val configKey: String
    )
}
