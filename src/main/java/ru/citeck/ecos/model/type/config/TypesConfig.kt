package ru.citeck.ecos.model.type.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsDao
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin

@Configuration
class TypesConfig {

    @Bean("typesMutMetaMixin")
    fun typesMutMetaMixin(
        typesRepoRecordsDao: TypesRepoRecordsDao,
        typeConverter: TypeConverter
    ): MutMetaMixin {
        val mixin = MutMetaMixin("emodel/type")
        typesRepoRecordsDao.addAttributesMixin(mixin)
        typeConverter.mutMetaMixin = mixin
        return mixin
    }
}
