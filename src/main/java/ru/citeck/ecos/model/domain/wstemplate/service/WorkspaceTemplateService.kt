package ru.citeck.ecos.model.domain.wstemplate.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.wstemplate.desc.WorkspaceTemplateDesc
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.web.client.EcosWebClientApi
import ru.citeck.ecos.webapp.lib.web.webapi.client.EcosWebClient
import java.nio.file.Path
import java.util.*

@Service
class WorkspaceTemplateService(
    private val webClient: EcosWebClient,
    private val recordsService: RecordsService
) {
    companion object {
        private const val GET_WS_ARTIFACTS_PATH = "/workspace/get-ws-artifacts-for-template"
        private const val GET_REQUIRED_WS_ARTIFACTS_PATH = "/workspace/get-required-ws-artifacts-from-template"
        private const val DEPLOY_WS_ARTIFACTS = "/workspace/deploy-ws-artifacts-from-template"

        private const val HEADER_WORKSPACE = "workspace"
        private const val HEADER_TEMPLATE = "template"

        private val APPS_WITH_ARTIFACTS_FOR_TEMPLATE = setOf(AppName.UISERV)

        private val log = KotlinLogging.logger {}
    }

    fun getWorkspaceArtifactsForTemplate(workspace: String): EcosMemDir {

        val artifactsDir = EcosMemDir()
        if (workspace.isEmpty()) {
            return artifactsDir
        }

        val queryBody = DataValue.createObj().set("workspace", workspace)
        for (app in APPS_WITH_ARTIFACTS_FOR_TEMPLATE) {
            try {
                val artifactsDirBytes = AuthContext.runAsSystem {
                    webClient.newRequest()
                        .targetApp(app)
                        .path(GET_WS_ARTIFACTS_PATH)
                        .version(checkRequiredPath(app, GET_WS_ARTIFACTS_PATH))
                        .body { it.writeDto(queryBody) }
                        .executeSync { it.getBodyReader().readAsBytes() }
                }
                artifactsDir.copyFilesFrom(ZipUtils.extractZip(artifactsDirBytes))
            } catch (e: Throwable) {
                log.error(e) { "Request Failed: $app$GET_WS_ARTIFACTS_PATH $queryBody" }
                throw RuntimeException(
                    "Application '$app' is currently unavailable. " +
                        "Please contact your administrator."
                )
            }
        }

        return artifactsDir
    }

    fun deployArtifactsForWorkspace(workspace: String, template: EntityRef) {

        if (template.isEmpty()) {
            return
        }

        log.info { "Deploy artifacts for workspace $workspace from template ${template.getLocalId()}" }
        val artifactsDir = loadTemplateArtifactsDir(template)

        for (app in APPS_WITH_ARTIFACTS_FOR_TEMPLATE) {

            val requiredArtifactPaths = webClient.newRequest()
                .targetApp(app)
                .path(GET_REQUIRED_WS_ARTIFACTS_PATH)
                .version(checkRequiredPath(app, GET_REQUIRED_WS_ARTIFACTS_PATH))
                .body { it.writeDto(DataValue.createObj()) }
                .executeSync { it.getBodyReader().readDto(GetRequiredWsArtifactsResp::class.java) }.paths

            log.debug { "Required paths for $app: $requiredArtifactPaths" }

            val artifactsToDeploy = EcosMemDir()
            val filesToDeployPaths = ArrayList<Path>()

            requiredArtifactPaths.forEach { path ->
                artifactsDir.findFiles(path).forEach { file ->
                    filesToDeployPaths.add(file.getPath())
                    val filePath = file.getPath().toString()
                    artifactsToDeploy.getFile(filePath)?.delete()
                    artifactsToDeploy.createFile(filePath) { output ->
                        file.read { it.copyTo(output) }
                    }
                }
            }

            if (filesToDeployPaths.isNotEmpty()) {

                log.debug {
                    "Deploy artifacts to app $app from " +
                        "template ${template.getLocalId()}: \n" + filesToDeployPaths.joinToString("\n")
                }

                webClient.newRequest()
                    .targetApp(app)
                    .path(DEPLOY_WS_ARTIFACTS)
                    .version(checkRequiredPath(app, DEPLOY_WS_ARTIFACTS))
                    .header(HEADER_WORKSPACE, workspace)
                    .header(HEADER_TEMPLATE, template.getLocalId())
                    .body { ZipUtils.writeZip(artifactsToDeploy, it.getOutputStream()) }
                    .executeSync { it.getBodyReader().readAsBytes() }
            } else {
                log.debug { "artifactsToDeploy dir is empty for template ${template.getLocalId()} and app $app" }
            }
        }
    }
    private fun loadTemplateArtifactsDir(template: EntityRef): EcosMemDir {
        val templateAtts = recordsService.getAtts(template, TemplateAtts::class.java)
        if (templateAtts.notExists) {
            error("Template is not exists: $template")
        }
        if (templateAtts.artifacts.isNullOrBlank()) {
            return EcosMemDir()
        }
        return ZipUtils.extractZip(Base64.getDecoder().decode(templateAtts.artifacts))
    }

    private fun checkRequiredPath(app: String, path: String): Int {
        val version = webClient.getApiVersion(app, GET_WS_ARTIFACTS_PATH, 0)
        if (version == EcosWebClientApi.AV_VERSION_NOT_SUPPORTED) {
            error("Path version is not supported. App: $app path: $path")
        }
        if (version == EcosWebClientApi.AV_PATH_NOT_SUPPORTED) {
            error("Path is not supported. App: $app path: $path")
        }
        if (version == EcosWebClientApi.AV_APP_NOT_AVAILABLE) {
            error("Required app is not available: $app path: $path")
        }
        return version
    }

    class GetRequiredWsArtifactsResp(
        val paths: List<String>
    )

    private class TemplateAtts(
        @param:AttName(RecordConstants.ATT_NOT_EXISTS + ScalarType.BOOL_SCHEMA + "!")
        val notExists: Boolean,
        @param:AttName(WorkspaceTemplateDesc.ATT_ARTIFACTS + ScalarType.STR_SCHEMA)
        val artifacts: String?
    )
}
