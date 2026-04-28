package ru.citeck.ecos.model.num

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.num.repo.NumTemplatesRepo
import ru.citeck.ecos.model.num.dto.NumTemplateDto
import ru.citeck.ecos.model.num.service.NumTemplateService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NumTemplateWorkspaceRegistryTest {

    @Autowired
    private lateinit var numTemplateService: NumTemplateService

    @Autowired
    private lateinit var numTemplatesRepo: NumTemplatesRepo

    @Autowired
    private lateinit var workspaceService: EmodelWorkspaceService

    @Autowired
    private lateinit var recordsService: RecordsService

    private var workspaceId = ""
    private var workspaceSysId = ""

    @BeforeAll
    fun setUp() {
        workspaceId = workspaceService.deployWorkspace(
            Workspace.create()
                .withId("test-num-ws")
                .withName(MLText("test-num-ws"))
                .build()
        )
        workspaceSysId = workspaceService.getSystemId(workspaceId)
    }

    @AfterAll
    fun tearDown() {
        recordsService.delete(WorkspaceDesc.getRef(workspaceId))
    }

    @Test
    fun `workspace-scoped num-template is found by wsSysId-prefixed key`() {
        val dto = NumTemplateDto()
        dto.id = "ws-counter"
        dto.workspace = workspaceId
        dto.name = "ws-counter"
        dto.counterKey = "counter-\${field}"

        numTemplateService.save(dto)

        val templateRef = EntityRef.create(EcosModelApp.NAME, "num-template", "$workspaceSysId:ws-counter")
        val result = numTemplatesRepo.getNumTemplate(templateRef)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo("ws-counter")
        assertThat(result.counterKey).isEqualTo("counter-\${field}")
    }

    @Test
    fun `global num-template is found by plain id`() {
        val dto = NumTemplateDto()
        dto.id = "global-counter"
        dto.name = "global-counter"
        dto.counterKey = "counter-\${field}"

        numTemplateService.save(dto)

        val templateRef = EntityRef.create(EcosModelApp.NAME, "num-template", "global-counter")
        val result = numTemplatesRepo.getNumTemplate(templateRef)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo("global-counter")
    }

    @Test
    fun `updated workspace-scoped num-template is found with correct key`() {
        val dto = NumTemplateDto()
        dto.id = "ws-counter-update"
        dto.workspace = workspaceId
        dto.name = "ws-counter-update"
        dto.counterKey = "counter-\${field}"

        numTemplateService.save(dto)

        dto.counterKey = "counter-\${field}-v2"
        numTemplateService.save(dto)

        val templateRef = EntityRef.create(EcosModelApp.NAME, "num-template", "$workspaceSysId:ws-counter-update")
        val result = numTemplatesRepo.getNumTemplate(templateRef)

        assertThat(result).isNotNull
        assertThat(result!!.counterKey).isEqualTo("counter-\${field}-v2")
    }
}
