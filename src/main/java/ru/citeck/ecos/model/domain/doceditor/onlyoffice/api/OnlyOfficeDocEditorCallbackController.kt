package ru.citeck.ecos.model.domain.doceditor.onlyoffice.api

import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.request.RequestContext
import ru.citeck.ecos.webapp.api.content.EcosContentApi
import ru.citeck.ecos.webapp.lib.remote.callback.RemoteCallbackService
import java.net.URI
import javax.servlet.http.HttpServletResponse

@RestController
class OnlyOfficeDocEditorCallbackController(
    val recordsService: RecordsService,
    val callbackService: RemoteCallbackService,
    val contentApi: EcosContentApi
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val BASE_PATH = "/pub/doc-editor/onlyoffice/callback"
        const val POST_STATUS_PATH = "$BASE_PATH/status"
        const val GET_CONTENT_PATH = "$BASE_PATH/content"

        private const val PROP_STATUS = "status"
        private const val PROP_URL = "url"
    }

    private val restTemplate = RestTemplate()

    @GetMapping(GET_CONTENT_PATH)
    fun getContent(
        @RequestParam(required = true) jwt: String,
        response: HttpServletResponse
    ) {
        callbackService.doWithJwtData(jwt, OnlyOfficeDocEditorCallbackJwt::class.java) { jwtData ->

            val content = contentApi.getContent(jwtData.ref, jwtData.att)
                ?: error("Content is not found for ref ${jwtData.ref} and attribute ${jwtData.att}")

            response.setHeader(
                "Content-Disposition",
                "attachment; filename=${content.getName()}"
            )
            response.setHeader(HttpHeaders.CONTENT_TYPE, content.getMimeType().toString())

            response.outputStream.use { output ->
                content.readContent {
                    it.copyTo(output)
                    output.flush()
                }
            }
        }
    }

    @PostMapping(
        POST_STATUS_PATH,
        consumes = [MediaType.APPLICATION_JSON_UTF8_VALUE],
        produces = [MediaType.APPLICATION_JSON_UTF8_VALUE]
    )
    fun postStatus(
        @RequestParam(required = true) jwt: String,
        @RequestBody(required = true) body: String
    ): ByteArray {

        log.trace { "OnlyOffice status callback: $body" }

        return callbackService.doWithJwtData(jwt, OnlyOfficeDocEditorCallbackJwt::class.java) { jwtData ->
            if (jwtData.ref.isEmpty()) {
                error("ref is empty")
            }
            val bodyData = Json.mapper.readData(body)
            val status = bodyData[PROP_STATUS].asInt()
            if (status == 2) {
                val urlToDownload = bodyData[PROP_URL].asText()
                val currentContent = contentApi.getContent(jwtData.ref, jwtData.att)

                restTemplate.execute(
                    URI.create(urlToDownload),
                    HttpMethod.GET,
                    null
                ) { resp ->
                    RequestContext.doWithCtx {
                        val tempRef = contentApi.uploadTempFile()
                            .withName(currentContent?.getName())
                            .withMimeType(currentContent?.getMimeType())
                            .writeContent {
                                it.writeStream(resp.body)
                            }
                        val contentAtt = jwtData.att.ifBlank { RecordConstants.ATT_CONTENT }
                        recordsService.mutateAtt(jwtData.ref, contentAtt, tempRef)
                    }
                }
            }
            Json.mapper.toBytesNotNull(CallbackResponse())
        }
    }

    private class CallbackResponse(
        val error: Int = 0
    )
}
