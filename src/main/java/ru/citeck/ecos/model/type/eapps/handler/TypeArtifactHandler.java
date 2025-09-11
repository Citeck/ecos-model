package ru.citeck.ecos.model.type.eapps.handler;

import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.model.lib.workspace.WorkspaceService;
import ru.citeck.ecos.model.type.service.TypeId;
import ru.citeck.ecos.model.type.service.TypesService;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypeArtifactHandler implements EcosArtifactHandler<TypeDef> {

    public static final String TYPE = "model/type";

    private final TypesService typeService;
    private final EcosWebAppApi ecosWebAppApi;

    @Override
    public void deployArtifact(@NotNull TypeDef artifact) {
        AuthContext.runAsSystemJ(() -> {
            typeService.save(artifact);
        });
    }

    @Override
    public void deleteArtifact(@NotNull String s) {
        typeService.delete(TypeId.create(s));
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return TYPE;
    }

    @Override
    public void listenChanges(@NotNull Consumer<TypeDef> consumer) {
        // delay listener registration to prevent unnecessary commands initiated by DeployCoreTypesPatch
        ecosWebAppApi.doWhenAppReady(100f, () -> {
            typeService.addListener((before, after) -> {
                if (after != null && after.getWorkspace().isEmpty()) {
                    consumer.accept(after);
                }
            });
            return Unit.INSTANCE;
        });
    }
}
