package ru.citeck.ecos.model.app

import com.google.common.primitives.Longs
import mu.KotlinLogging
import org.apache.commons.codec.binary.Base32
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
import ru.citeck.ecos.model.type.service.utils.EcosModelTypeUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.util.*
import java.util.zip.CRC32
import javax.sql.DataSource

@RestController
@Secured(AuthRole.ADMIN)
@RequestMapping("/api/ecosdata")
class EcosDataController(
    val recordsService: RecordsService,
    val typesRegistry: EcosTypesRegistry,
    val dataSource: DataSource
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

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

    @GetMapping(
        "/run-refs-migration",
        produces = [MediaType.APPLICATION_JSON_UTF8_VALUE]
    )
    fun runRefsMigration(): List<String> {

        val messages = mutableListOf<String>()

        typesRegistry.getAllValues().values.map {
            it.entity
        }.filter {
            it.sourceType == EcosModelTypeUtils.SOURCE_TYPE_EMODEL
        }.forEach {
            val legacyId = generateLegacyEmodelSourceId(it.id)
            val newSrcId = it.sourceId
            val refsToMigrate = getRefsWithLegacySrcId(legacyId)
            if (refsToMigrate.isNotEmpty()) {
                val msg = "Migrate from " + legacyId + " to " + newSrcId + ". Refs count: " + refsToMigrate.size
                log.info { msg }
                messages.add(msg)
                updateRefs(refsToMigrate, newSrcId)
            }
        }
        return messages
    }

    private fun updateRefs(refs: Set<String>, newSrcId: String) {
        dataSource.connection.use { conn ->
            for (ref in refs) {
                val newRef = AppName.EMODEL + "/" + newSrcId + "@" + ref.substring(ref.indexOf('@') + 1)
                conn.createStatement().use { statement ->
                    statement.executeUpdate(
                        "UPDATE \"ecos_data\".\"ecos_record_ref\" " +
                        "SET \"__ext_id\"='$newRef' WHERE \"__ext_id\"='$ref'"
                    )
                }
            }
            conn.commit()
        }
    }

    private fun getRefsWithLegacySrcId(sourceId: String): Set<String> {
        val result = mutableSetOf<String>()
        dataSource.connection.use { conn ->
            conn.createStatement().use { statement ->
                val res = statement.executeQuery(
                    "SELECT * FROM \"ecos_data\".\"ecos_record_ref\" WHERE \"__ext_id\" LIKE 'emodel/$sourceId@%';"
                )
                while (res.next()) {
                    result.add(res.getString("__ext_id"))
                }
            }
        }
        return result
    }

    private fun generateLegacyEmodelSourceId(typeId: String): String {

        val crc = CRC32()
        crc.update(typeId.toByteArray())
        val base32 = Base32()
        var result = base32.encodeToString(Longs.toByteArray(crc.value))
            .lowercase()
            .substringBefore('=')

        var idxOfLastLeadingA = -1
        while (idxOfLastLeadingA < result.length - 1 && result[idxOfLastLeadingA + 1] == 'a') {
            idxOfLastLeadingA++
        }

        if (idxOfLastLeadingA > 0) {
            result = result.substring(idxOfLastLeadingA)
        }

        return "t-$result"
    }
}
