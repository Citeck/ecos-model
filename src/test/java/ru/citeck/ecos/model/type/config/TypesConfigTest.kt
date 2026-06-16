package ru.citeck.ecos.model.type.config

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.model.type.service.utils.EModelTypeUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.util.function.BiConsumer

class TypesConfigTest {

    private val ws = "custom"
    private val wsPrefix = "custom-sys-id" + IdInWs.WS_DELIM

    private val rawTypes = HashMap<String, TypeDef>()
    private lateinit var typesRegistry: EcosTypesRegistry
    private lateinit var recordsService: RecordsService
    private lateinit var listener: BiConsumer<TypeDef?, TypeDef?>

    @BeforeEach
    fun beforeEach() {

        // abstract roots carry the storage policy: a DEFAULT child of `base` stays DEFAULT (no
        // auto DAO), a DEFAULT child of the business abstract `user-base` inherits ECOS_MODEL
        rawTypes["base"] = TypeDef.create().withId("base").build()
        rawTypes["user-base"] = TypeDef.create()
            .withId("user-base")
            .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
            .build()

        val rawProv = object : TypesProvider {
            override fun get(id: String): TypeDef? = rawTypes[id]
            override fun getChildren(typeId: String): List<String> = rawTypes.values.filter { it.parentRef.getLocalId() == typeId }.map { it.id }
        }

        val workspaceService = fakeWorkspaceService()
        val emodelTypeUtils = EModelTypeUtils()
        emodelTypeUtils.workspaceService = workspaceService

        typesRegistry = mock<EcosTypesRegistry>()
        whenever(typesRegistry.getAllValues()).thenReturn(emptyMap())
        recordsService = mock<RecordsService>()
        val typesService = mock<TypesService>()

        val typesConfig = TypesConfig(
            emodelTypeUtils,
            TypeDefResolver(workspaceService, emodelTypeUtils),
            rawProv
        )
        typesConfig.setDbDomainFactory(mock<DbDomainFactory>())
        typesConfig.setTypesRegistry(typesRegistry)
        typesConfig.setRecordsService(recordsService)
        typesConfig.setTypesService(typesService)
        typesConfig.init()

        val listenerCaptor = argumentCaptor<BiConsumer<TypeDef?, TypeDef?>>()
        verify(typesService).addListener(eq(-100f), listenerCaptor.capture())
        listener = listenerCaptor.firstValue
    }

    @Test
    fun `does not fail when ws-scoped storage becomes explicit and resolves to the same source id`() {

        // ws-scoped type always resolves to ECOS_MODEL with the same generated source id,
        // so flipping the raw storageType is a no-op for the registered DAO
        val rawBefore = TypeDef.create()
            .withId("simple-record")
            .withWorkspace(ws)
            .build()
        val rawAfter = rawBefore.copy()
            .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
            .build()

        whenever(recordsService.getRecordsDao("${wsPrefix}simple-record")).thenReturn(mock<RecordsDao>())

        assertThatCode { listener.accept(rawBefore, rawAfter) }.doesNotThrowAnyException()
    }

    @Test
    fun `does not fail when ws-scoped explicit sourceId is ignored and the generated id is free`() {

        // resolution ignores an explicit sourceId for ws-scoped types and always generates
        // its own, so the foreign DAO under the explicit value is never consulted
        val rawAfter = TypeDef.create()
            .withId("simple-record")
            .withWorkspace(ws)
            .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
            .withSourceId("foreign-src-id")
            .build()

        whenever(recordsService.getRecordsDao("foreign-src-id")).thenReturn(mock<RecordsDao>())

        assertThatCode { listener.accept(null, rawAfter) }.doesNotThrowAnyException()
    }

    @Test
    fun `does not veto a DEFAULT type under the abstract root even if a DAO is registered`() {

        // a type under `base` stays DEFAULT and never gets an auto DAO, so an existing DAO under
        // its id (e.g. a manually-registered one) is none of the save guard's business
        val rawAfter = TypeDef.create()
            .withId("record-version")
            .build()

        whenever(recordsService.getRecordsDao("record-version")).thenReturn(mock<RecordsDao>())

        assertThatCode { listener.accept(null, rawAfter) }.doesNotThrowAnyException()
    }

    @Test
    fun `does not fail when the registry already attributes the occupied source id to this type`() {

        // on a fresh database the DAO is registered from the registry (classpath artifacts)
        // before DeployCoreTypesPatch saves the same ECOS_MODEL type into the repo
        val rawAfter = TypeDef.create()
            .withId("known-type")
            .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
            .build()

        whenever(recordsService.getRecordsDao("known-type")).thenReturn(mock<RecordsDao>())
        whenever(typesRegistry.getValue("known-type")).thenReturn(
            TypeDef.create()
                .withId("known-type")
                .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
                .build()
        )

        assertThatCode { listener.accept(null, rawAfter) }.doesNotThrowAnyException()
    }

    @Test
    fun `fails when a new explicit ECOS_MODEL type collides with a foreign DAO`() {

        // a new type that explicitly declares ECOS_MODEL storage and whose generated source id
        // is occupied by a DAO the registry does not attribute to it is a real conflict
        val rawAfter = TypeDef.create()
            .withId("known-type")
            .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
            .build()

        whenever(recordsService.getRecordsDao("known-type")).thenReturn(mock<RecordsDao>())

        assertThatThrownBy { listener.accept(null, rawAfter) }
            .hasMessageContaining("'known-type' already registered")
    }

    @Test
    fun `fails when an auto-resolved ECOS_MODEL type collides with a foreign DAO`() {

        // even without explicit storage: a DEFAULT type under the business abstract `user-base`
        // resolves to ECOS_MODEL, so a collision of its generated source id is vetoed too —
        // it makes no difference how ECOS_MODEL was derived
        val rawAfter = TypeDef.create()
            .withId("biz-type")
            .withParentRef(EntityRef.valueOf("emodel/type@user-base"))
            .build()

        whenever(recordsService.getRecordsDao("biz-type")).thenReturn(mock<RecordsDao>())

        assertThatThrownBy { listener.accept(null, rawAfter) }
            .hasMessageContaining("'biz-type' already registered")
    }

    @Test
    fun `fails when an explicit emodel sourceId is owned by another type`() {

        val rawAfter = TypeDef.create()
            .withId("other-type")
            .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
            .withSourceId("occupied-src-id")
            .build()

        whenever(recordsService.getRecordsDao("occupied-src-id")).thenReturn(mock<RecordsDao>())

        assertThatThrownBy { listener.accept(null, rawAfter) }
            .hasMessageContaining("'occupied-src-id' already registered")
    }

    private fun fakeWorkspaceService(): WorkspaceService {
        val service = mock<WorkspaceService>()
        whenever(service.addWsPrefixToId(any(), any())).doAnswer { inv ->
            val localId = inv.getArgument<String>(0)
            val workspace = inv.getArgument<String>(1)
            if (workspace != ws || localId.startsWith(wsPrefix)) localId else "$wsPrefix$localId"
        }
        return service
    }
}
