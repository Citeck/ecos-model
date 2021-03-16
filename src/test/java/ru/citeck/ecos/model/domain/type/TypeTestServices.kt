package ru.citeck.ecos.model.domain.type

import ru.citeck.ecos.model.type.api.records.ResolvedTypeRecordsDao
import ru.citeck.ecos.model.type.api.records.mixin.TypeInhMixin
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao
import ru.citeck.ecos.model.type.api.records.mixin.CreateVariantsByIdMixin
import ru.citeck.ecos.model.type.config.TypesConfig
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler
import ru.citeck.ecos.model.type.service.TypeService
import ru.citeck.ecos.model.type.service.TypeServiceImpl
import ru.citeck.ecos.records3.RecordsProperties
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory

class TypeTestServices {

    val recordsServices: RecordsServiceFactory
    val typesRepo: TypeRepoMock
    val typeService: TypeService
    val artifactHandler: TypeArtifactHandler

    val records: RecordsService

    init {

        recordsServices = object : RecordsServiceFactory() {
            override fun createProperties(): RecordsProperties {
                val props = super.createProperties()
                props.appInstanceId = "123456"
                props.appName = "emodel"
                return props
            }
        }

        typesRepo = TypeRepoMock(recordsServices)
        val typeConverter = TypeConverter(typesRepo)
        typeService = TypeServiceImpl(typeConverter, typesRepo)
        artifactHandler = TypeArtifactHandler(typeService)

        val typeRecordsDao = TypeRecordsDao(typeService)
        recordsServices.recordsServiceV1.register(typeRecordsDao)
        val resolvedRecordsDao = ResolvedTypeRecordsDao(typeService, typeRecordsDao)
        recordsServices.recordsServiceV1.register(resolvedRecordsDao)
        TypeInhMixin(resolvedRecordsDao, typeRecordsDao)
        CreateVariantsByIdMixin(resolvedRecordsDao, typeRecordsDao)

        TypesConfig().typesMutMetaMixin(typeRecordsDao, typeConverter, resolvedRecordsDao)

        records = recordsServices.recordsServiceV1
    }
}
