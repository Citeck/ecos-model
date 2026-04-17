package ru.citeck.ecos.model.type.eapps.handler;

import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.WsAwareArtifactHandler;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.model.lib.workspace.IdInWs;
import ru.citeck.ecos.model.lib.workspace.WorkspaceService;
import ru.citeck.ecos.model.type.service.TypesService;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypeArtifactHandler implements WsAwareArtifactHandler<TypeDef> {

    public static final String TYPE = "model/type";

    private final TypesService typeService;
    private final EcosWebAppApi ecosWebAppApi;
    private final WorkspaceService workspaceService;

    @Override
    public void deployArtifact(@NotNull TypeDef artifact, @NotNull String workspace) {
        AuthContext.runAsSystemJ(() -> {
            typeService.save(applyRefs(artifact, workspace, ref ->
                ref.withLocalId(workspaceService.replaceCurrentWsPlaceholderToWsPrefix(ref.getLocalId(), workspace))
            ));
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
                    TypeDef stripped = applyRefs(after, "", ref ->
                        ref.withLocalId(workspaceService.replaceWsPrefixToCurrentWsPlaceholder(ref.getLocalId()))
                    );
                    consumer.accept(stripped, workspace);
                }
            });
            return Unit.INSTANCE;
        });
    }

    private TypeDef applyRefs(TypeDef typeDef, String workspace, Function<EntityRef, EntityRef> transform) {
        TypeDef.Builder builder = typeDef.copy().withWorkspace(workspace);
        if (EntityRef.isNotEmpty(typeDef.getFormRef())) {
            builder.withFormRef(transform.apply(typeDef.getFormRef()));
        }
        if (EntityRef.isNotEmpty(typeDef.getJournalRef())) {
            builder.withJournalRef(transform.apply(typeDef.getJournalRef()));
        }
        if (EntityRef.isNotEmpty(typeDef.getNumTemplateRef())) {
            builder.withNumTemplateRef(transform.apply(typeDef.getNumTemplateRef()));
        }
        if (EntityRef.isNotEmpty(typeDef.getBoardRef())) {
            builder.withBoardRef(transform.apply(typeDef.getBoardRef()));
        }
        if (EntityRef.isNotEmpty(typeDef.getConfigFormRef())) {
            builder.withConfigFormRef(transform.apply(typeDef.getConfigFormRef()));
        }
        if (EntityRef.isNotEmpty(typeDef.getPostCreateActionRef())) {
            builder.withPostCreateActionRef(transform.apply(typeDef.getPostCreateActionRef()));
        }
        List<EntityRef> actions = typeDef.getActions();
        if (actions != null && !actions.isEmpty()) {
            builder.withActions(actions.stream()
                .map(ref -> EntityRef.isNotEmpty(ref) ? transform.apply(ref) : ref)
                .collect(Collectors.toList()));
        }
        return builder.build();
    }
}
