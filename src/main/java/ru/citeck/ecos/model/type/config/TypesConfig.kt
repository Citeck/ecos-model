package ru.citeck.ecos.model.type.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin

@Configuration
class TypesConfig {

    @Bean("typesMutMetaMixin")
    fun typesMutMetaMixin(typeRecordsDao: TypeRecordsDao) : MutMetaMixin {
        val mixin = MutMetaMixin("emodel/type")
        typeRecordsDao.addAttributesMixin(mixin)
        return mixin
    }
}
