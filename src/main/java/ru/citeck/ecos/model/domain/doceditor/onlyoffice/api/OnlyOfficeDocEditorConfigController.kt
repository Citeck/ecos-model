package ru.citeck.ecos.model.domain.doceditor.onlyoffice.api

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.digest.DigestUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.content.EcosContentApi
import ru.citeck.ecos.webapp.api.content.EcosContentData
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.api.mime.MimeType
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps
import ru.citeck.ecos.webapp.lib.discovery.WebAppDiscoveryService
import ru.citeck.ecos.webapp.lib.discovery.instance.PortType
import ru.citeck.ecos.webapp.lib.remote.callback.RemoteCallbackService
import java.net.URL
import java.time.Duration

@RestController
@RequestMapping("/api/doc-editor/onlyoffice/config")
class OnlyOfficeDocEditorConfigController(
    val discoveryService: WebAppDiscoveryService,
    val recordsService: RecordsService,
    val contentApi: EcosContentApi,
    val callbackService: RemoteCallbackService,
    val webAppProps: EcosWebAppProps
) {

    @GetMapping(
        produces = [MediaType.APPLICATION_JSON_UTF8_VALUE]
    )
    fun getConfig(
        @RequestParam(name = "ref", required = true) ref: String,
        @RequestParam(name = "att", required = false) att: String?
    ): ByteArray {

        val entityRef = ref.toEntityRef()
        val content = contentApi.getContent(entityRef) ?: error("Content is null for entity $ref")
        val extension = content.getMimeType().getExtension().ifBlank {
            content.getMimeType().asTikaMimeType().extension
        }
        val jwtData = OnlyOfficeDocEditorCallbackJwt(entityRef, att ?: "")
        Json.mapper.toBytesNotNull(getEditorConfig(jwtData))

        val docAtts = recordsService.getAtts(entityRef, DocumentAtts::class.java)

        val config = OnlyOfficeConfig(
            getDocumentInfo(jwtData, entityRef, docAtts, content, extension),
            getEditorConfig(jwtData),
            getDocType(extension)
        )
        return Json.mapper.toBytesNotNull(config)
    }

    private fun getEditorConfig(
        jwtData: OnlyOfficeDocEditorCallbackJwt,
    ): Editor {

        val callbackUrl = createCallbackHttpUrl(
            OnlyOfficeDocEditorCallbackController.POST_STATUS_PATH,
            Duration.ofHours(2),
            jwtData
        )
        return Editor(
            callbackUrl = callbackUrl,
            lang = I18nContext.getLocale().language,
            user = getUserInfo()
        )
    }

    private fun createCallbackHttpUrl(path: String, validity: Duration, jwtData: Any): URL {
        val jwtToken = callbackService.createJwtToken(jwtData, validity)
        val instance = discoveryService.getCurrentInstance()
        val port = instance.getPort(PortType.HTTPS, PortType.HTTP) ?: error("HTTP Port is not found")
        return URL(
            "${port.type.protocol}://" +
                "${instance.getHost()}:${port.value}" +
                "$path?jwt=$jwtToken"
        )
    }

    private fun getDocumentInfo(
        jwtData: OnlyOfficeDocEditorCallbackJwt,
        docRef: EntityRef,
        docAtts: DocumentAtts,
        content: EcosContentData,
        extension: String
    ): Document {

        val getContentUrl = createCallbackHttpUrl(
            OnlyOfficeDocEditorCallbackController.GET_CONTENT_PATH,
            Duration.ofSeconds(30),
            jwtData
        )
        val docKey = DigestUtils.getSha256(
            webAppProps.appName +
                webAppProps.appInstanceId +
                docRef.toString() +
                content.getSha256()
        ).hash

        return Document(
            fileType = extension,
            key = docKey,
            title = docAtts.displayName,
            url = getContentUrl
        )
    }

    private fun getUserInfo(): User {

        val userName = AuthContext.getCurrentUser()
        val userRef = EntityRef.create(AppName.EMODEL, "person", userName)
        val userAtts = recordsService.getAtts(userRef, UserAtts::class.java)

        return User(
            id = userName,
            firstname = userAtts.firstName,
            lastname = userAtts.lastName,
            name = userAtts.displayName
        )
    }

    private fun getDocType(extension: String): String? {
        return if ((
                ".doc.docx.docm.dot.dotx.dotm.odt.fodt.ott" +
                    ".rtf.txt.html.htm.mht.pdf.djvu.fb2.epub.xps"
                ).indexOf(extension) != -1
        ) {
            "text"
        } else if (".xls.xlsx.xlsm.xlt.xltx.xltm.ods.fods.ots.csv".indexOf(extension) != -1) {
            "spreadsheet"
        } else if (".pps.ppsx.ppsm.ppt.pptx.pptm.pot.potx.potm.odp.fodp.otp".indexOf(extension) != -1) {
            "presentation"
        } else {
            null
        }
    }

    private fun MimeType.asTikaMimeType(): org.apache.tika.mime.MimeType {
        return org.apache.tika.mime.MimeTypes.getDefaultMimeTypes().forName(this.toString())
    }

    private class DocumentAtts(
        @AttName("?disp!id")
        val displayName: String
    )

    private class UserAtts(
        @AttName("?disp!id")
        val displayName: String,
        @AttName("firstName!")
        val firstName: String,
        @AttName("lastName!")
        val lastName: String,
    )

    private class OnlyOfficeConfig(
        val document: Document,
        val editorConfig: Editor,
        val documentType: String?
    )

    private class Document(
        val fileType: String,
        val key: String,
        val permissions: DocPermissions = DocPermissions(),
        val title: String,
        val url: URL
    )

    private class DocPermissions(
        val edit: Boolean = true
    )

    private class Editor(
        val callbackUrl: URL,
        val lang: String,
        val mode: String = "edit",
        val user: User
    )

    private class User(
        val firstname: String,
        val id: String,
        val lastname: String,
        val name: String
    )
}
