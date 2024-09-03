package ru.citeck.ecos.model.type.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import java.util.concurrent.Callable
import javax.sql.DataSource

@Component
@EcosPatch("MigrateRefsWithSourceIdPrefix", "2022-10-03T00:00:00Z")
class MigrateRefsWithSourceIdPrefix(
    private val dataSource: DataSource
) : Callable<MigrateRefsWithSourceIdPrefix.Result> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun call(): Result {
        val result = Result()
        val logMsg: (String) -> Unit = {
            log.info { it }
            result.messages.add(it)
        }
        val refs = getRefsWithPrefixSrcId()
        if (refs.isNotEmpty()) {
            logMsg("Update ${refs.size} refs with prefix 't-'")
            updateRefsWithPrefix(refs)
        } else {
            logMsg("Nothing to migrate")
        }
        return result
    }

    private fun updateRefsWithPrefix(refs: Set<String>) {
        dataSource.connection.use { conn ->
            for (ref in refs) {
                val newRef = ref.replaceFirst("emodel/t-", "emodel/")
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

    private fun getRefsWithPrefixSrcId(): Set<String> {
        val result = mutableSetOf<String>()
        dataSource.connection.use { conn ->
            conn.createStatement().use { statement ->
                val res = statement.executeQuery(
                    "SELECT * FROM \"ecos_data\".\"ecos_record_ref\" WHERE \"__ext_id\" LIKE 'emodel/t-%';"
                )
                while (res.next()) {
                    result.add(res.getString("__ext_id"))
                }
            }
        }
        return result
    }

    class Result(
        val messages: MutableList<String> = ArrayList()
    )
}
