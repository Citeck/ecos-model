package ru.citeck.ecos.model.app

import org.springframework.http.MediaType
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.data.sql.records.migration.AssocsDbMigration
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records3.RecordsService
import java.util.*

@RestController
@Secured(AuthRole.ADMIN)
@RequestMapping("/api/ecosdata")
class EcosDataController(
    val recordsService: RecordsService
) {

    @GetMapping(
        "/run-assocs-migration",
        produces = [MediaType.APPLICATION_JSON_UTF8_VALUE]
    )
    fun runAssocsMigration(): Map<String, String> {

        val commentsDao = recordsService.getRecordsDao("comment-repo", DbRecordsDao::class.java)
            ?: error("CommentsDao doesn't found")

        commentsDao.runMigrationByType(
            AssocsDbMigration.TYPE,
            TypeUtils.getTypeRef("ecos-comment"),
            false,
            ObjectData.create()
        )
        return Collections.singletonMap("result", "OK")
    }
}
