package ru.citeck.ecos.model.type.eapps.handler;

import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.WsAwareArtifactHandler;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.model.lib.workspace.IdInWs;
import ru.citeck.ecos.model.type.service.TypesService;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

import java.util.function.BiConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypeArtifactHandler implements WsAwareArtifactHandler<TypeDef> {

    public static final String TYPE = "model/type";

    private final TypesService typeService;
    private final EcosWebAppApi ecosWebAppApi;

    @Override
    public void deployArtifact(@NotNull TypeDef artifact, @NotNull String workspace) {
        AuthContext.runAsSystemJ(() -> {
            typeService.save(artifact.copy().withWorkspace(workspace).build());
        });
    }

    @Override
    public void deleteArtifact(@NotNull String artifactId, @NotNull String workspace) {
        typeService.delete(IdInWs.create(workspace, artifactId));
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return TYPE;
    }

    @Override
    public void listenChanges(@NotNull BiConsumer<TypeDef, String> consumer) {
        // delay listener registration to prevent unnecessary commands initiated by DeployCoreTypesPatch
        ecosWebAppApi.doWhenAppReady(100f, () -> {
            typeService.addListener((before, after) -> {
                if (after != null) {
                    String workspace = after.getWorkspace();
                    TypeDef stripped = after.copy().withWorkspace("").build();
                    consumer.accept(stripped, workspace);
                }
            });
            return Unit.INSTANCE;
        });
    }
}
