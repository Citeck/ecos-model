package ru.citeck.ecos.model.domain.authsync.service

import ru.citeck.ecos.commons.data.ObjectData

interface AuthoritiesSyncContext<T> {

    fun setState(state: T?)

    fun updateAuthorities(type: AuthorityType, authorities: List<ObjectData>)
}
