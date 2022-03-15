package ru.citeck.ecos.model.domain.type.testutils

import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.core.env.Environment
import ru.citeck.ecos.apps.EcosAppsServiceFactory
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceProvider
import ru.citeck.ecos.apps.app.domain.artifact.source.DirectorySourceProvider
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.EventsServiceFactory
import ru.citeck.ecos.model.domain.type.TypeRepoMock
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
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
import ru.citeck.ecos.model.type.service.repo.TypesRepoImpl
import ru.citeck.ecos.records3.RecordsProperties
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import java.io.File

open class TypeTestBase {

    lateinit var recordsServices: RecordsServiceFactory
    lateinit var typesRepo: TypeRepoMock
    lateinit var typeService: TypeService
    lateinit var artifactHandler: TypeArtifactHandler

    lateinit var records: RecordsService

    lateinit var eventsServiceFactory: EventsServiceFactory
    lateinit var eventsService: EventsService

    @BeforeEach
    fun init() {

        val env = Mockito.mock(Environment::class.java)
        Mockito.`when`(env.acceptsProfiles("test")).thenReturn(true)

        recordsServices = object : RecordsServiceFactory() {
            override fun createProperties(): RecordsProperties {
                val props = super.createProperties()
                props.appInstanceId = "123456"
                props.appName = "emodel"
                return props
            }
        }

        val ecosAppsServiceFactory = object : EcosAppsServiceFactory() {
            override fun createArtifactSourceProviders(): List<ArtifactSourceProvider> {
                return listOf(
                    DirectorySourceProvider(EcosStdFile(
                        File("./src/main/resources/eapps/artifacts")
                    ))
                )
            }
        }
        ecosAppsServiceFactory.recordsServices = recordsServices
        ecosAppsServiceFactory.commandsServices = CommandsServiceFactory()

        typesRepo = TypeRepoMock(recordsServices)
        val typeConverter = TypeConverter(typesRepo)
        typeService = TypeServiceImpl(typeConverter, typesRepo)
        artifactHandler = TypeArtifactHandler(typeService)

        val modelLibServices = object : ModelServiceFactory() {
            override fun createTypesRepo(): TypesRepo {
                return TypesRepoImpl(
                    env,
                    typeService,
                    recordsServices,
                    ecosAppsServiceFactory.localAppService
                )
            }
        }
        modelLibServices.setRecordsServices(recordsServices)

        eventsServiceFactory = EventsServiceFactory()
        eventsServiceFactory.recordsServices = recordsServices
        eventsServiceFactory.modelServices = modelLibServices
        this.eventsService = eventsServiceFactory.eventsService

        records = recordsServices.recordsServiceV1
        val typeRecordsDao = TypeRecordsDao(typeService, eventsServiceFactory.recordEventsService)
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
