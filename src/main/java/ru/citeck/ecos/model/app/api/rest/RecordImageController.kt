package ru.citeck.ecos.model.app.api.rest

import org.imgscalr.Scalr
import org.springframework.http.CacheControl
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.lib.spring.context.content.EcosContentService
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@RestController
@RequestMapping("/api/ecosdata")
class RecordImageController(
    val recordsService: RecordsService,
    val contentService: EcosContentService
) {

    @GetMapping("/image", produces = [MediaType.IMAGE_PNG_VALUE])
    fun getImage(
        @RequestParam(required = true) ref: RecordRef,
        @RequestParam(required = true) att: String,
        @RequestParam(required = false) width: Int?
    ): HttpEntity<ByteArray> {

        val headers = HttpHeaders()
        headers.setCacheControl(
            CacheControl.maxAge(4, TimeUnit.HOURS)
                .mustRevalidate()
                .cachePublic()
        )

        var image = contentService.getContent(ref, att)?.readContent { ImageIO.read(it) }

        if (width != null && width > 0) {
            image = Scalr.resize(image, Scalr.Mode.FIT_TO_WIDTH, width)
        }

        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)

        return HttpEntity<ByteArray>(out.toByteArray(), headers)
    }
}
