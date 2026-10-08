package ru.citeck.ecos.model.domain.perms

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.AuthoritiesHelper
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.perms.eapp.TypePermsArtifactHandler
import ru.citeck.ecos.model.domain.perms.repo.TypePermsEntity
import ru.citeck.ecos.model.domain.perms.repo.TypePermsRepository
import ru.citeck.ecos.model.domain.perms.service.TypePermsInitializer
import ru.citeck.ecos.model.domain.perms.service.TypePermsService
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.perms.registry.TypePermissionsRegistry
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.registry.EcosRegistryProps
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistryDelegate
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypePermsWorkspaceTest {

    @Autowired private lateinit var types: TypesService
    @Autowired private lateinit var records: RecordsService
    @Autowired private lateinit var workspaces: WorkspaceService
    @Autowired private lateinit var emodelWorkspaces: EmodelWorkspaceService
    @Autowired private lateinit var authorities: AuthoritiesHelper
    @Autowired private lateinit var service: TypePermsService
    @Autowired private lateinit var repository: TypePermsRepository
    @Autowired private lateinit var artifacts: TypePermsArtifactHandler
    @Autowired private lateinit var registry: TypePermissionsRegistry

    private val manager = "perms-ws-manager"
    private val member = "perms-ws-member"
    private lateinit var ws1: String
    private lateinit var ws2: String

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            authorities.createPerson(manager)
            authorities.createPerson(member)
            ws1 = createWorkspace("perms-ws-one")
            ws2 = createWorkspace("perms-ws-two")
        }
    }

    private fun createWorkspace(id: String): String = emodelWorkspaces.deployWorkspace(
        Workspace.create().withId(id).withName(MLText(id)).withWorkspaceMembers(
            listOf(manager to WorkspaceMemberRole.MANAGER, member to WorkspaceMemberRole.USER).map { (user, role) ->
                WorkspaceMember.create().withMemberId(user).withMemberRole(role)
                    .withAuthorities(listOf(AuthorityType.PERSON.getRef(user))).build()
            }
        ).build()
    )

    private fun type(workspace: String, id: String): EntityRef {
        val scopedId = workspaces.addWsPrefixToId(id, workspace)
        return ModelUtils.getTypeRef(scopedId)
    }

    private fun matrix(workspace: String, id: String, typeRef: EntityRef = type(workspace, id)): TypePermsDef {
        return TypePermsDef.create()
            .withId(id)
            .withTypeRef(typeRef)
            .build()
    }

    private fun manager(action: () -> Unit) = AuthContext.runAs(manager, listOf(AuthRole.USER), action)

    @Test
    fun managerCreatesAndEditsOwnMatrixThroughRecordsTest() = manager {
        val type = type(ws1, "ui-type")
        val ref = records.create(
            "perms",
            ObjectData.create()
                .set("typeRef", type.toString()).set("_workspace", "emodel/workspace@$ws1")
        )
        assertThat(ref.getLocalId()).isEqualTo(workspaces.addWsPrefixToId("ui-type", ws1))
        assertThat(records.getAtt(ref, "workspace?str").asText()).isEqualTo(ws1)
        assertThat(records.getAtt(ref, "permissions._has.Write?bool").asBoolean()).isTrue()
        records.mutate(ref, ObjectData.create().set("permissions?json", mapOf("matrix" to mapOf("EVERYONE" to mapOf("draft" to "READ")))))
        assertThat(service.getPermsForType(type)?.permissions?.matrix).isNotEmpty()
        val result = records.query(
            RecordsQuery.create().withSourceId("perms").withLanguage("type")
                .withQuery(mapOf("typeRef" to type)).build(),
            listOf("workspace?str")
        )
        assertThat(result.getRecords().single().getId()).isEqualTo(ref)
        assertThat(result.getRecords().single().get("workspace?str").asText()).isEqualTo(ws1)
        records.delete(ref)
        assertThat(service.getPermsForType(type)).isNull()
    }

    @Test
    fun sameIdInDifferentWorkspacesDoesNotCollideTest() = manager {
        val first = service.save(matrix(ws1, "same-id"))
        val second = service.save(matrix(ws2, "same-id"))
        assertThat(service.getPermsForType(first.typeRef)).isEqualTo(first)
        assertThat(service.getPermsForType(second.typeRef)).isEqualTo(second)
        assertThat(registry.getPermissionsForType(first.typeRef)).isEqualTo(first)
        assertThat(registry.getPermissionsForType(second.typeRef)).isEqualTo(second)
        assertThat(service.getPermsForType(ModelUtils.getTypeRef("same-id"))).isNull()
        service.delete(service.getId(first))
        assertThat(service.getPermsForType(second.typeRef)).isEqualTo(second)
    }

    @Test
    fun matrixCannotTargetGlobalOrForeignTypeEvenAsSystemTest() {
        for (ref in listOf(ModelUtils.getTypeRef("base"), type(ws2, "foreign"))) {
            AuthContext.runAsSystem {
                assertThatThrownBy { service.save(matrix(ws1, "attack", ref), ws1) }
                    .hasMessageContaining("same workspace")
                assertThatThrownBy { artifacts.deployArtifact(matrix("", "attack", ref), ws1) }
                    .hasMessageContaining("same workspace")
            }
        }
        manager {
            assertThatThrownBy {
                records.create(
                    "perms",
                    ObjectData.create()
                        .set("id", "attack-ui").set("typeRef", ModelUtils.getTypeRef("base").toString())
                        .set("_workspace", ws1)
                )
            }.hasMessageContaining("Permission denied")
            assertThatThrownBy { service.save(matrix("", "global-denied")) }.hasMessageContaining("Permission denied")
        }
    }

    @Test
    fun memberCannotWriteOrDeleteAndManagerCannotDeleteGlobalMatrixTest() {
        val def = AuthContext.runAsSystem { service.save(matrix(ws1, "member-denied")) }
        AuthContext.runAs(member, listOf(AuthRole.USER)) {
            assertThat(records.getAtt(EntityRef.create("perms", workspaces.convertToStrId(service.getId(def))), "permissions._has.Write?bool").asBoolean()).isFalse()
            assertThatThrownBy { service.save(def) }.hasMessageContaining("Permission denied")
            assertThatThrownBy { service.delete(service.getId(def)) }.hasMessageContaining("Permission denied")
        }
        val global = AuthContext.runAsSystem { service.save(matrix("", "global-delete-denied")) }
        manager {
            assertThatThrownBy { service.delete(service.getId(global)) }.hasMessageContaining("Permission denied")
        }
    }

    @Test
    fun completeTypeReferenceIgnoresPageWorkspaceOnCreationTest() {
        manager {
            for ((id, context) in listOf("full-ref-global-page" to "", "full-ref-other-page" to ws2)) {
                val target = type(ws1, id)
                val ref = records.create(
                    "perms",
                    ObjectData.create().set("typeRef", target.toString())
                        .set("_workspace", "emodel/workspace@$context")
                )
                assertThat(records.getAtt(ref, "workspace?str").asText()).isEqualTo(ws1)
                assertThat(service.getPermsById(workspaces.convertToIdInWs(ref.getLocalId()))?.typeRef).isEqualTo(target)
            }
            assertThatThrownBy {
                records.create(
                    "perms",
                    ObjectData.create().set("id", workspaces.addWsPrefixToId("wrong-ref-scope", ws2))
                        .set("typeRef", type(ws1, "wrong-ref-scope").toString()).set("_workspace", ws1)
                )
            }.hasMessageContaining("same workspace")
        }
        AuthContext.runAsSystem {
            val target = ModelUtils.getTypeRef("global-from-ws-page")
            val ref = records.create("perms", ObjectData.create().set("typeRef", target.toString()).set("_workspace", ws1))
            assertThat(records.getAtt(ref, "workspace?str").asText()).isEmpty()
            assertThat(service.getPermsForType(target)).isNotNull()
        }
    }

    @Test
    fun portableTypeReferenceUsesContextWhenCreatingNewMatrixTest() = manager {
        val ref = records.create(
            "perms",
            ObjectData.create().set("typeRef", "emodel/type@CURRENT_WS:portable-new")
                .set("_workspace", "emodel/workspace@$ws1")
        )
        assertThat(service.getPermsById(workspaces.convertToIdInWs(ref.getLocalId()))?.typeRef).isEqualTo(type(ws1, "portable-new"))
        assertThat(records.getAtt(ref, "workspaceRef?localId").asText()).isEqualTo(ws1)
    }

    @Test
    fun existingMatrixKeepsWorkspaceWhenEditedFromAnotherContextTest() = manager {
        val def = service.save(matrix(ws1, "existing"))
        val ref = EntityRef.create("perms", workspaces.convertToStrId(service.getId(def)))
        records.mutate(
            ref,
            ObjectData.create().set("_workspace", "emodel/workspace@")
                .set("attributes?json", emptyMap<String, Any>())
        )
        assertThat(service.getPermsById(workspaces.convertToIdInWs(ref.getLocalId()))?.let { service.getWorkspace(it) }).isEqualTo(ws1)
        records.mutate(ref, ObjectData.create().set("workspace", ws2))
        assertThat(records.getAtt(ref, "workspace?str").asText()).isEqualTo(ws1)
        assertThat(records.getAtt(ref, "workspaceRef?localId").asText()).isEqualTo(ws1)
        assertThat(repository.findByWorkspaceAndExtId(ws1, def.id)?.workspace).isEqualTo(ws1)
    }

    @Test
    fun recordsInferWorkspaceAndRejectCrossScopeRetargetingTest() = manager {
        val target = type(ws1, "type-derived")
        val ref = records.create("perms", ObjectData.create().set("typeRef", target.toString()))
        assertThat(records.getAtt(ref, "workspace?str").asText()).isEqualTo(ws1)
        assertThat(records.getAtt(ref, "workspaceRef?localId").asText()).isEqualTo(ws1)
        assertThat(repository.findByWorkspaceAndExtId(ws1, "type-derived")).isNotNull()
        for (other in listOf(ModelUtils.getTypeRef("base"), type(ws2, "other"))) {
            assertThatThrownBy { records.mutate(ref, ObjectData.create().set("typeRef", other.toString())) }
                .hasMessageContaining("same workspace")
        }
        assertThat(service.getPermsById(workspaces.convertToIdInWs(ref.getLocalId()))?.typeRef).isEqualTo(target)
        val global = AuthContext.runAsSystem { service.save(matrix("", "original-global")) }
        assertThatThrownBy {
            records.mutate(EntityRef.create("perms", global.id), ObjectData.create().set("typeRef", target.toString()))
        }.hasMessageContaining("Permission denied")
    }

    @Test
    fun quarantinedStorageScopeIsPreservedDuringRepairTest() {
        val id = "repair-shared-id"
        val global = AuthContext.runAsSystem {
            val saved = service.save(matrix("", id))
            val entity = TypePermsEntity()
            entity.extId = workspaces.addWsPrefixToId(id, ws1)
            entity.typeRef = saved.typeRef.toString()
            entity.permissions = "{}"
            entity.attributes = "{}"
            repository.saveAndFlush(entity)
            service.migrateLegacyPermissions()
            saved
        }
        manager {
            val ref = EntityRef.create("perms", workspaces.addWsPrefixToId(id, ws1))
            val result = records.query(
                RecordsQuery.create().withSourceId("perms").withLanguage("predicate")
                    .withQuery(mapOf("t" to "eq", "att" to "id", "val" to id))
                    .withWorkspaces(listOf(ws1)).build(),
                listOf("workspace?str")
            )
            assertThat(result.getRecords().single().getId()).isEqualTo(ref.withAppName("emodel"))
            assertThat(result.getRecords().single().get("workspace?str").asText()).isEmpty()
            assertThatThrownBy { records.mutate(ref, ObjectData.create().set("attributes", emptyMap<String, Any>())) }
                .hasMessageContaining("same workspace")
            records.mutate(ref, ObjectData.create().set("typeRef", type(ws1, id).toString()))
            assertThat(service.getPermsById(workspaces.convertToIdInWs(ref.getLocalId()))?.typeRef).isEqualTo(type(ws1, id))
            assertThat(registry.getPermissionsForType(global.typeRef)).isEqualTo(global)
            assertThat(registry.getPermissionsForType(type(ws1, id))).isNotNull()
            records.delete(ref)
            assertThat(registry.getPermissionsForType(global.typeRef)).isEqualTo(global)
        }
    }

    @Test
    fun portableJsonRoundtripPreservesExistingWorkspaceTest() = manager {
        val def = service.save(matrix(ws1, "json-roundtrip"))
        val ref = EntityRef.create("perms", workspaces.convertToStrId(service.getId(def)))
        val exported = records.getAtt(ref, "?json").asObjectData()
        assertThat(exported.has("workspace")).isFalse()
        assertThat(exported.fieldNamesList()).containsExactlyInAnyOrder("id", "typeRef", "permissions", "attributes")
        assertThat(exported.get("permissions").asObjectData().fieldNamesList()).containsExactlyInAnyOrder("matrix", "rules")
        assertThat(exported.get("id").asText()).isEqualTo(def.id)
        assertThat(exported.get("typeRef").asText()).isEqualTo("emodel/type@CURRENT_WS:json-roundtrip")
        exported.set("permissions", mapOf("matrix" to mapOf("EVERYONE" to mapOf("draft" to "READ"))))
        records.mutate(ref, ObjectData.create().set("_self", exported))
        val updated = service.getPermsById(workspaces.convertToIdInWs(ref.getLocalId()))!!
        assertThat(service.getWorkspace(updated)).isEqualTo(ws1)
        assertThat(updated.typeRef).isEqualTo(def.typeRef)
        assertThat(updated.permissions.matrix).isNotEmpty()
        assertThat(service.getPermsForType(ModelUtils.getTypeRef(def.id))).isNull()
    }

    @Test
    fun artifactPlaceholdersAndWorkspaceSystemPermissionsTest() {
        workspaces.runAsWsSystem(ws1) {
            artifacts.deployArtifact(matrix("", "deployed", ModelUtils.getTypeRef("CURRENT_WS:deployed")), ws1)
            assertThat(service.getPermsForType(type(ws1, "deployed"))?.let { service.getWorkspace(it) }).isEqualTo(ws1)
            assertThatThrownBy { artifacts.deployArtifact(matrix("", "attack-ws-system", ModelUtils.getTypeRef("base")), ws1) }
                .hasMessageContaining("same workspace")
            assertThatThrownBy { service.save(matrix(ws2, "other-ws")) }.hasMessageContaining("Permission denied")
        }
        val exported = AuthContext.runAsSystem {
            records.getAtt(EntityRef.create("perms", workspaces.addWsPrefixToId("deployed", ws1)), "?json")
        }
        assertThat(exported.get("typeRef").asText()).isEqualTo("emodel/type@CURRENT_WS:deployed")
        assertThat(exported.asObjectData().has("workspace")).isFalse()
        workspaces.runAsWsSystem(ws2) {
            artifacts.deployArtifact(matrix("", "deployed", EntityRef.valueOf(exported.get("typeRef").asText())), ws2)
            assertThat(service.getPermsForType(type(ws2, "deployed"))?.let { service.getWorkspace(it) }).isEqualTo(ws2)
            artifacts.deleteArtifact("deployed", ws2)
            assertThat(service.getPermsForType(type(ws2, "deployed"))).isNull()
            assertThat(service.getPermsForType(type(ws1, "deployed"))).isNotNull()
        }
    }

    @Test
    fun legacyWorkspaceMatrixOfGlobalTypeIsQuarantinedTest(): Unit = AuthContext.runAsSystem {
        val entity = TypePermsEntity()
        entity.extId = workspaces.addWsPrefixToId("legacy-invalid", ws1)
        entity.typeRef = ModelUtils.getTypeRef("legacy-global").toString()
        entity.permissions = "{}"
        entity.attributes = "{}"
        repository.saveAndFlush(entity)
        service.migrateLegacyPermissions()
        assertThat(repository.findByWorkspaceAndExtId(ws1, "legacy-invalid")).isNotNull()
        assertThat(service.getPermsForType(ModelUtils.getTypeRef("legacy-global"))).isNull()
        assertThat(service.allWithMeta.map { it.definition.id }).doesNotContain("legacy-invalid")
    }

    @Test
    fun emodelPublishesOnlyApplicableMatricesOnInitializationRepairAndDeletionTest(): Unit = AuthContext.runAsSystem {
        val global = service.save(matrix("", "publisher-global"))
        val invalidIds = listOf(global.id, "publisher-repair", "publisher-foreign", "unknown:publisher")
        for (id in invalidIds) {
            val entity = TypePermsEntity()
            entity.workspace = ws1
            entity.extId = id
            entity.typeRef = when (id) {
                global.id -> global.typeRef.toString()
                "publisher-foreign" -> type(ws2, id).toString()
                else -> ModelUtils.getTypeRef("$id-target").toString()
            }
            entity.permissions = "{}"
            entity.attributes = "{}"
            repository.saveAndFlush(entity)
        }
        val invalidGlobalKey = workspaces.addWsPrefixToId(global.id, ws1)
        assertThat(service.getRecordDataById(IdInWs.create(ws1, global.id))?.definition).isEqualTo(global)
        val published = TypePermissionsRegistry(EcosRegistryProps.DEFAULT, emptyList())
        val changedKeys = mutableListOf<String>()
        val publisher = object : MutableEcosRegistryDelegate<TypePermsDef>(published) {
            override fun setValue(key: String, value: EntityWithMeta<TypePermsDef>?) {
                changedKeys.add(key)
                super.setValue(key, value)
            }
        }
        TypePermsInitializer(service, workspaces).init(publisher, emptyMap(), EcosRegistryProps.Initializer.DEFAULT).get()
        assertThat(published.getPermissionsForType(global.typeRef)).isEqualTo(global)
        for (id in invalidIds) {
            assertThat(published.getValue(workspaces.addWsPrefixToId(id, ws1))).isNull()
        }
        changedKeys.clear()
        service.delete(IdInWs.create(ws1, global.id))
        assertThat(changedKeys).isEmpty()
        assertThat(published.getPermissionsForType(global.typeRef)).isEqualTo(global)

        val repairId = "publisher-repair"
        val repaired = service.save(matrix(ws1, repairId), ws1)
        val repairKey = workspaces.addWsPrefixToId(repairId, ws1)
        assertThat(changedKeys).containsExactly(repairKey)
        assertThat(published.getPermissionsForType(repaired.typeRef)).isEqualTo(repaired)
        assertThat(published.getPermissionsForType(global.typeRef)).isEqualTo(global)
        service.delete(service.getId(repaired))
        assertThat(changedKeys).containsExactly(repairKey, repairKey)
        assertThat(published.getPermissionsForType(repaired.typeRef)).isNull()
        assertThat(published.getPermissionsForType(global.typeRef)).isEqualTo(global)
    }

    @Test
    fun legacyTypeWorkspaceAndRetargetedMatrixTest(): Unit = AuthContext.runAsSystem {
        val legacy = TypePermsEntity()
        legacy.extId = "legacy-valid"
        legacy.typeRef = type(ws1, "legacy-valid").toString()
        legacy.permissions = "{}"
        legacy.attributes = "{}"
        repository.saveAndFlush(legacy)
        service.migrateLegacyPermissions()
        assertThat(service.getPermsForType(type(ws1, "legacy-valid"))?.let { service.getWorkspace(it) }).isEqualTo(ws1)
        val before = service.save(matrix(ws1, "retarget", type(ws1, "old-target")))
        val after = service.save(before.copy().withTypeRef(type(ws1, "new-target")).build())
        assertThat(registry.getPermissionsForType(before.typeRef)).isNull()
        assertThat(registry.getPermissionsForType(after.typeRef)).isEqualTo(after)
    }

    @Test
    fun resolvedTypeReportsItsWorkspaceForTheEditorTest(): Unit = AuthContext.runAsSystem {
        types.save(TypeDef.create().withId("real-type").withWorkspace(ws1).build())
        val typeRef = type(ws1, "real-type").withSourceId("rtype")
        assertThat(records.getAtt(typeRef, "workspaceRef?localId").asText()).isEqualTo(ws1)
        val coDeployed = ArtifactDeployMeta.create().withCoDeployedArtifacts(listOf(ModelUtils.getTypeRef("real-type"))).build()
        ArtifactDeployMeta.doWithMeta(coDeployed) {
            artifacts.deployArtifact(matrix("", "real-type", ModelUtils.getTypeRef("real-type")), ws1)
        }
        assertThat(service.getPermsForType(type(ws1, "real-type"))).isNotNull()
    }

    @Test
    fun legacyIdCollisionPreservesBothTypesTest(): Unit = AuthContext.runAsSystem {
        for ((id, target) in listOf("legacy-collision" to "legacy-first", workspaces.addWsPrefixToId("legacy-collision", ws1) to "legacy-second")) {
            val entity = TypePermsEntity()
            entity.extId = id
            entity.typeRef = type(ws1, target).toString()
            entity.permissions = "{}"
            entity.attributes = "{}"
            repository.saveAndFlush(entity)
        }
        service.migrateLegacyPermissions()
        val first = service.getPermsForType(type(ws1, "legacy-first"))
        val second = service.getPermsForType(type(ws1, "legacy-second"))
        assertThat(first).isNotNull()
        assertThat(second).isNotNull()
        assertThat(first!!.id).isNotEqualTo(second!!.id)
    }

    @Test
    fun globalTypeWithNamespaceKeepsItsDefaultMatrixIdTest(): Unit = AuthContext.runAsSystem {
        val type = ModelUtils.getTypeRef("namespace:contract")
        val saved = service.save(matrix("", "", type))
        assertThat(saved.id).isEqualTo("namespace:contract")
        assertThat(service.getWorkspace(saved)).isEmpty()
        assertThat(service.getPermsById(service.getId(saved))).isEqualTo(saved)
        assertThat(service.getPermsForType(type)).isEqualTo(saved)
        assertThat(registry.getPermissionsForType(type)).isEqualTo(saved)
    }

    @Test
    fun structuredIdsKeepGlobalAndWorkspaceReadMetadataAndDeletionIndependentTest() {
        val localId = "typed-shared-id"
        val definitions = AuthContext.runAsSystem {
            listOf("", ws1, ws2).associateWith { service.save(matrix(it, localId)) }
        }
        for ((workspace, def) in definitions) {
            val id = IdInWs.create(workspace, localId)
            assertThat(service.getId(def)).isEqualTo(id)
            assertThat(service.getPermsById(id)).isEqualTo(def)
            assertThat(service.getRecordDataById(id)?.id).isEqualTo(id)
            assertThat(service.getPermsMeta(id)).isNotNull()
        }
        manager {
            service.delete(IdInWs.create(ws1, localId))
            assertThat(service.getPermsById(IdInWs.create(ws1, localId))).isNull()
            assertThat(service.getPermsMeta(IdInWs.create(ws1, localId))).isNull()
            for (workspace in listOf("", ws2)) {
                val def = definitions.getValue(workspace)
                assertThat(service.getPermsById(IdInWs.create(workspace, localId))).isEqualTo(def)
                assertThat(registry.getPermissionsForType(def.typeRef)).isEqualTo(def)
            }
        }
    }

    @Test
    fun structuredGlobalAliasAndArtifactDeletionUseGlobalStorageTest(): Unit = AuthContext.runAsSystem {
        val def = service.save(matrix("", "typed-global-alias"))
        val id = IdInWs.create("default", def.id)
        assertThat(service.getPermsById(id)).isEqualTo(def)
        assertThat(service.getRecordDataById(id)?.id).isEqualTo(service.getId(def))
        assertThat(service.getPermsMeta(id)).isNotNull()
        artifacts.deleteArtifact(def.id, "default")
        assertThat(service.getPermsById(service.getId(def))).isNull()
        assertThat(registry.getPermissionsForType(def.typeRef)).isNull()
    }

    @Test
    fun missingWorkspaceArtifactDeletionPreservesGlobalMatrixTest(): Unit = AuthContext.runAsSystem {
        val global = service.save(matrix("", "typed-deleted-workspace"))
        val id = IdInWs.create("perms-workspace-no-longer-exists", global.id)
        val orphan = TypePermsEntity()
        orphan.workspace = id.workspace
        orphan.extId = id.id
        orphan.typeRef = global.typeRef.toString()
        orphan.permissions = "{}"
        orphan.attributes = "{}"
        repository.saveAndFlush(orphan)
        assertThat(service.getRecordDataById(id)?.id).isEqualTo(id)
        assertThat(service.getPermsMeta(id)).isNotNull()
        artifacts.deleteArtifact(id.id, id.workspace)
        assertThat(service.getRecordDataById(id)).isNull()
        assertThat(service.getPermsMeta(id)).isNull()
        assertThat(service.getPermsById(service.getId(global))).isEqualTo(global)
        assertThat(registry.getPermissionsForType(global.typeRef)).isEqualTo(global)
    }

    @Test
    fun namespacedWorkspaceIdsRoundtripEvenWhenNamespaceIsAnotherWorkspaceSystemIdTest() = manager {
        val knownPrefix = workspaces.addWsPrefixToId("typed-namespace", ws2)
        for (localId in listOf("namespace:typed-contract", knownPrefix)) {
            val typeRef = type(ws1, localId)
            val id = IdInWs.create(ws1, localId)
            val saved = service.save(matrix(ws1, "", typeRef))
            assertThat(saved.id).isEqualTo(localId)
            assertThat(service.getId(saved)).isEqualTo(id)
            assertThat(service.getRecordDataById(id)?.id).isEqualTo(id)
            assertThat(service.save(saved)).isEqualTo(saved)
            val ref = EntityRef.create("perms", workspaces.convertToStrId(id))
            val exported = records.getAtt(ref, "?json").asObjectData()
            assertThat(exported.get("id").asText()).isEqualTo(localId)
            assertThat(exported.get("typeRef").asText()).isEqualTo("emodel/type@CURRENT_WS:$localId")
            records.mutate(ref, ObjectData.create().set("_self", exported))
            assertThat(service.getPermsById(id)?.typeRef).isEqualTo(typeRef)
            assertThat(registry.getPermissionsForType(typeRef)?.id).isEqualTo(localId)
            artifacts.deleteArtifact(localId, ws1)
            assertThat(service.getPermsById(id)).isNull()
            assertThat(registry.getPermissionsForType(typeRef)).isNull()
        }
    }

    @Test
    fun journalQueryFiltersWorkspaceAndCountTest() = manager {
        service.save(matrix(ws1, "query-first"))
        service.save(matrix(ws2, "query-second"))
        val query = RecordsQuery.create().withSourceId("perms").withLanguage("predicate")
            .withQuery(mapOf("t" to "eq", "att" to "id", "val" to "query-first"))
            .withWorkspaces(listOf(ws2)).build()
        val result = records.query(query, listOf("workspace?str"))
        assertThat(result.getRecords()).isEmpty()
        assertThat(result.getTotalCount()).isZero()
        val own = records.query(query.copy().withWorkspaces(listOf(ws1)).build(), listOf("workspace?str"))
        assertThat(own.getRecords()).hasSize(1)
        assertThat(own.getTotalCount()).isEqualTo(1)
    }
}
