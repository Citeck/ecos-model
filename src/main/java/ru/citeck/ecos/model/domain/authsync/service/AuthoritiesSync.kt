package ru.citeck.ecos.model.domain.authsync.service

import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts

interface AuthoritiesSync<T : Any> {

    /**
     * @return true if some updates was performed or false otherwise
     */
    fun execute(state: T?): Boolean

    fun mutate(record: LocalRecordAtts, newRecord: Boolean): String

    fun getManagedAtts(): Set<String>

    fun start() {}

    fun stop() {}
}
