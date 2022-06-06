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
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@RestController
@RequestMapping("/api/ecosdata")
class RecordImageController(
    val recordsService: RecordsService
) {

    @GetMapping("/image", produces = [MediaType.IMAGE_JPEG_VALUE])
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

        val sourceId = ref.sourceId + "-repo"
        val dbRecords = recordsService.getRecordsDao(sourceId, DbRecordsDao::class.java)!!
        var image = dbRecords.readContent(ref.id, att) { _, stream ->
            ImageIO.read(stream)
        }

        if (width != null && width > 0) {
            image = Scalr.resize(image, Scalr.Mode.FIT_TO_WIDTH, width)
        }

        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpeg", out)

        return HttpEntity<ByteArray>(out.toByteArray(), headers)
    }
}
