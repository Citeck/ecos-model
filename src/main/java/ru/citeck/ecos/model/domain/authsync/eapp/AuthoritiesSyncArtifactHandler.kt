package ru.citeck.ecos.model.domain.authsync.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncDef
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import java.util.function.Consumer

@Component
class AuthoritiesSyncArtifactHandler(
    private val recordsService: RecordsService
) : EcosArtifactHandler<AuthoritiesSyncDef> {

    private lateinit var listener: Consumer<AuthoritiesSyncDef>

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(RecordRef.create(AuthoritiesSyncService.SOURCE_ID, artifactId))
    }

    override fun deployArtifact(artifact: AuthoritiesSyncDef) {
        val recordRef = RecordRef.create(AuthoritiesSyncService.SOURCE_ID, "")
        AuthContext.runAsSystem {
            recordsService.mutate(recordRef, artifact)
        }
    }

    override fun getArtifactType(): String {
        return "model/authorities-sync"
    }

    override fun listenChanges(listener: Consumer<AuthoritiesSyncDef>) {
        this.listener = listener
    }

    fun syncWasChanged(sync: AuthoritiesSyncDef) {
        listener.accept(sync)
    }
}
