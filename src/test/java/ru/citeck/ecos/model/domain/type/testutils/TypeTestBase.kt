package ru.citeck.ecos.model.domain.type.testutils

import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.core.env.Environment
import ru.citeck.ecos.apps.EcosAppsServiceFactory
import ru.citeck.ecos.apps.app.domain.artifact.source.ArtifactSourceProvider
import ru.citeck.ecos.apps.app.domain.artifact.source.DirectorySourceProvider
import ru.citeck.ecos.commands.CommandsServiceFactory
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.test.EcosWebAppApiMock
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.EventsServiceFactory
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.type.TypeRepoMock
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsDao
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsMutDao
import ru.citeck.ecos.model.type.api.records.mixin.TypeInhMixin
import ru.citeck.ecos.model.type.config.TypesConfig
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.TypesRegistryInitializer
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.model.type.service.TypesServiceImpl
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.lib.model.type.records.TypeRecordsDao
import ru.citeck.ecos.webapp.lib.model.type.registry.DefaultTypesInitializer
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import ru.citeck.ecos.webapp.lib.model.type.registry.TypeArtifactsInitializer
import ru.citeck.ecos.webapp.lib.registry.EcosRegistryProps
import java.io.File

open class TypeTestBase {

    lateinit var recordsServices: RecordsServiceFactory
    lateinit var typesRepo: TypeRepoMock
    lateinit var typeService: TypesService
    lateinit var artifactHandler: TypeArtifactHandler

    lateinit var records: RecordsService

    lateinit var eventsServiceFactory: EventsServiceFactory
    lateinit var eventsService: EventsService

    @BeforeEach
    fun init() {

        val env = Mockito.mock(Environment::class.java)
        Mockito.`when`(env.acceptsProfiles("test")).thenReturn(true)

        val webAppCtxMock = EcosWebAppApiMock(EcosModelApp.NAME, "123456")
        recordsServices = object : RecordsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi? {
                return webAppCtxMock
            }
        }

        val ecosAppsServiceFactory = object : EcosAppsServiceFactory() {
            override fun createArtifactSourceProviders(): List<ArtifactSourceProvider> {
                return listOf(
                    DirectorySourceProvider(
                        EcosStdFile(
                            File("./src/main/resources/eapps/artifacts")
                        )
                    )
                )
            }
        }
        ecosAppsServiceFactory.recordsServices = recordsServices
        ecosAppsServiceFactory.commandsServices = CommandsServiceFactory()

        typesRepo = TypeRepoMock(recordsServices)
        val typeConverter = TypeConverter(typesRepo)
        typeService = TypesServiceImpl(typeConverter, typesRepo)
        artifactHandler = TypeArtifactHandler(typeService)
        records = recordsServices.recordsServiceV1

        val typesRegistry = EcosTypesRegistry(
            EcosRegistryProps.DEFAULT,
            listOf(
                DefaultTypesInitializer(),
                TypeArtifactsInitializer(ecosAppsServiceFactory.localAppService),
                TypesRegistryInitializer(typeService, records)
            )
        )

        val modelLibServices = object : ModelServiceFactory() {
            override fun createTypesRepo(): TypesRepo {
                return typesRegistry
            }
        }
        modelLibServices.setRecordsServices(recordsServices)

        eventsServiceFactory = EventsServiceFactory()
        eventsServiceFactory.recordsServices = recordsServices
        eventsServiceFactory.modelServices = modelLibServices
        this.eventsService = eventsServiceFactory.eventsService

        val typesRepoRecordsDao = TypesRepoRecordsDao(typeService, eventsServiceFactory.recordEventsService)
        records.register(typesRepoRecordsDao)
        records.register(TypeRecordsDao(typesRegistry, modelLibServices))

/*        val resolvedRecordsDao = ResolvedTypeRecordsDao(typeService, typeRepoRecordsDao)
        records.register(resolvedRecordsDao)*/

        TypeInhMixin(typeService, typesRepoRecordsDao)
        TypesConfig().typesMutMetaMixin(typesRepoRecordsDao, typeConverter)

        records.register(TypesRepoRecordsMutDao(typeService))

        val baseType = TypeEntity()
        baseType.extId = "base"
        typesRepo.save(baseType)
    }
}
