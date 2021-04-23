package ru.citeck.ecos.model.type.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.model.type.api.records.ResolvedTypeRecordsDao
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin

@Configuration
class TypesConfig {

    @Bean("typesMutMetaMixin")
    fun typesMutMetaMixin(typeRecordsDao: TypeRecordsDao,
                          typeConverter: TypeConverter,
                          resolvedTypeRecordsDao: ResolvedTypeRecordsDao) : MutMetaMixin {
        val mixin = MutMetaMixin("emodel/type")
        typeRecordsDao.addAttributesMixin(mixin)
        resolvedTypeRecordsDao.addAttributesMixin(mixin)
        typeConverter.mutMetaMixin = mixin
        return mixin
    }
}
