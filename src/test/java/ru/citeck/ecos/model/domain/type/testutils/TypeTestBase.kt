package ru.citeck.ecos.model.domain.type.testutils

import org.junit.jupiter.api.BeforeEach
import ru.citeck.ecos.model.domain.type.TypeRepoMock
import ru.citeck.ecos.model.type.api.records.ResolvedTypeRecordsDao
import ru.citeck.ecos.model.type.api.records.mixin.TypeInhMixin
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao
import ru.citeck.ecos.model.type.api.records.TypeRecordsMutDao
import ru.citeck.ecos.model.type.api.records.mixin.CreateVariantsByIdMixin
import ru.citeck.ecos.model.type.config.TypesConfig
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.TypeService
import ru.citeck.ecos.model.type.service.TypeServiceImpl
import ru.citeck.ecos.records3.RecordsProperties
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory

open class TypeTestBase {

    lateinit var recordsServices: RecordsServiceFactory
    lateinit var typesRepo: TypeRepoMock
    lateinit var typeService: TypeService
    lateinit var artifactHandler: TypeArtifactHandler

    lateinit var records: RecordsService

    @BeforeEach
    fun init() {

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

        records = recordsServices.recordsServiceV1
        val typeRecordsDao = TypeRecordsDao(typeService)
        records.register(typeRecordsDao)
        val resolvedRecordsDao = ResolvedTypeRecordsDao(typeService, typeRecordsDao)
        records.register(resolvedRecordsDao)
        TypeInhMixin(resolvedRecordsDao, typeRecordsDao)
        CreateVariantsByIdMixin(resolvedRecordsDao, typeRecordsDao)

        TypesConfig().typesMutMetaMixin(typeRecordsDao, typeConverter, resolvedRecordsDao)

        records.register(TypeRecordsMutDao(typeService))

        val baseType = TypeEntity()
        baseType.extId = "base"
        typesRepo.save(baseType)
    }
}
