package ru.citeck.ecos.model.app.api.rest

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.mime.MimeTypes
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.request.RequestContext
import ru.citeck.ecos.webapp.api.content.EcosContentApi

@RestController
@RequestMapping("/api/content-version")
class ContentVersionController(
    private val recordsService: RecordsService,
    private val contentApi: EcosContentApi
) {

    @PostMapping("/upload", produces = [MimeTypes.APP_JSON_UTF8_TEXT])
    fun handleFileUpload(
        @RequestParam("filedata", required = true) file: MultipartFile,
        @RequestParam("updateNodeRef", required = true) entityRef: String,
        @RequestParam("description") description: String?,
        @RequestParam("majorversion") majorversion: Boolean?
    ): String {
        val permissions = recordsService.getAtts(
            entityRef,
            mapOf(
                "hasWrite" to "permissions._has.Write?bool!true",
                "isContentProtected" to "${RecordConstants.ATT_EDGE}.${RecordConstants.ATT_CONTENT}.protected?bool!"
            )
        ).getAtts()
        val hasWrite = permissions["hasWrite"].asBoolean()
        val isContentProtected = permissions["isContentProtected"].asBoolean()

        if (!hasWrite || isContentProtected) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Permission Denied")
        }

        val tempFile = RequestContext.doWithCtx {
            contentApi.uploadTempFile()
                .withMimeType(file.contentType)
                .withName(file.originalFilename)
                .writeContent { writer ->
                    file.inputStream.use { writer.writeStream(it) }
                }
        }
        val versionDiff = if (majorversion != true) {
            "+0.1"
        } else {
            "+1.0"
        }

        recordsService.mutate(
            entityRef,
            mapOf(
                "version:version" to versionDiff,
                "version:comment" to (description ?: ""),
                "_content" to tempFile
            )
        )

        val displayName = recordsService.getAtt(entityRef, "?disp").asText()

        // Legacy response format
        return DataValue.createObj()
            .set("nodeRef", entityRef)
            .set("fileName", displayName)
            .set(
                "status",
                DataValue.createObj()
                    .set("code", 200)
                    .set("name", "OK")
                    .set("description", "File uploaded successfully")
            ).toString()
    }
}
