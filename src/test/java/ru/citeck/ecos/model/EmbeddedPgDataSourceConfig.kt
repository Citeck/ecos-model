package ru.citeck.ecos.model

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import lombok.SneakyThrows
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.utils.use
import javax.sql.DataSource

@Configuration
class EmbeddedPgDataSourceConfig {

    companion object {
        const val DB_NAME = "emodel"
        const val USER_NAME = "emodel"

        private val log = KotlinLogging.logger {}
    }

    @Bean
    @SneakyThrows
    fun dataSource(): DataSource? {
        val pg = EmbeddedPostgres.start()
        val database = pg.postgresDatabase
        database.connection.use { conn ->
            conn.prepareStatement(
                "CREATE DATABASE " + DB_NAME + ";" +
                    "CREATE USER " + USER_NAME + " WITH ENCRYPTED PASSWORD '';" +
                    "GRANT ALL ON DATABASE " + DB_NAME + " TO " + USER_NAME + ";"
            ).use { stmt ->
                stmt.executeUpdate()
            }
            conn.prepareStatement("SELECT version()").use { stmt ->
                stmt.executeQuery().use { rs ->
                    rs.next()
                    log.info("Setup embedded postgresql database: " + rs.getString(1))
                }
            }
        }
        val config = HikariConfig()
        config.dataSource = pg.getDatabase(USER_NAME, DB_NAME)
        config.isAutoCommit = false
        config.jdbcUrl = pg.getJdbcUrl(USER_NAME, DB_NAME)
        return HikariDataSource(config)
    }
}
