package ru.citeck.ecos.model.domain.authsync.service

interface AuthoritiesSyncFactory<C: Any, S: Any> {

    fun createSync(config: C,
                   authorityType: AuthorityType,
                   context: AuthoritiesSyncContext<S>): AuthoritiesSync<S>

    fun getType(): String
}
