package ru.citeck.ecos.model.app.api.rest;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.mime.MimeTypes;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.webapp.api.constants.AppName;
import ru.citeck.ecos.webapp.api.content.EcosContentData;
import ru.citeck.ecos.webapp.api.mime.MimeType;
import ru.citeck.ecos.webapp.api.web.client.EcosWebClientApi;
import ru.citeck.ecos.webapp.lib.spring.context.content.EcosContentService;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class DocumentPrintRedirectController {

    private static final String ALFRESCO_REDIRECT_URI = "/gateway/alfresco/alfresco/s/citeck/print/printpdf?nodeRef=";
    private static final String TRANSFORM_WEBAPI_PATH = "/tfm/transform";

    private static final MimeType PDF_MIME_TYPE = MimeTypes.INSTANCE.getAPP_PDF();

    private final EcosContentService ecosContentService;
    private final EcosWebClientApi webClient;

    @GetMapping(value = "/api/content/printpdf")
    public ResponseEntity<ByteArrayResource> returnPrintPdf(@RequestParam RecordRef ref) {

        if (ref.getAppName().equals(AppName.ALFRESCO)) {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(ALFRESCO_REDIRECT_URI + ref + "&print=true"));
            return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
        }
        EcosContentData data = ecosContentService.getContent(ref);

        if (data == null) {
            throw new RuntimeException("Document with id " + ref.getLocalId() + " is not found");
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_PDF);

        String fileName = FilenameUtils.getBaseName(data.getName()) + "." + PDF_MIME_TYPE.getExtension();

        String contentDispositionHeaderValue = ContentDisposition.builder("inline")
            .filename(fileName, StandardCharsets.UTF_8)
            .build()
            .toString();
        responseHeaders.set("Content-Disposition", contentDispositionHeaderValue);

        ByteArrayResource resource = new ByteArrayResource(getContentAsPdf(data));

        return new ResponseEntity<>(resource, responseHeaders, HttpStatus.OK);
    }

    private byte[] getContentAsPdf(EcosContentData data) {

        if (PDF_MIME_TYPE.equals(data.getMimeType())) {
            return data.readContentAsBytes();
        }

        return webClient.newRequest()
            .targetApp(AppName.TRANSFORMATIONS)
            .path(TRANSFORM_WEBAPI_PATH)
            .header(
                "contentMeta",
                DataValue.createObj()
                    .set("mimeType", data.getMimeType().toString())
                    .set("name", data.getName())
                    .set("sha256", data.getSha256())
                    .set("size", data.getSize())
            )
            .header(
                "transformations",
                DataValue.createArr()
                    .add(
                        DataValue.createObj()
                            .set("type", "convert")
                            .set("config", DataValue.createObj().set("toMimeType", PDF_MIME_TYPE.toString()))
                    )
            ).bodyJ(bodyWriter ->
                data.readContentJ(stream -> {
                    bodyWriter.writeStream(stream);
                })
            )
            .executeSyncJ(body -> body.getBodyReader().readAsBytes());
    }
}
