package ru.citeck.ecos.model.domain.perms.eapp;

import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta;
import ru.citeck.ecos.apps.app.domain.handler.WsAwareArtifactHandler;
import ru.citeck.ecos.model.domain.perms.service.TypePermsService;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.model.lib.workspace.IdInWs;
import ru.citeck.ecos.model.lib.workspace.WorkspaceService;
import ru.citeck.ecos.model.lib.workspace.WorkspaceServiceExtensionsKt;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypePermsArtifactHandler implements WsAwareArtifactHandler<TypePermsDef> {

    private final TypePermsService typePermsService;
    private final WorkspaceService workspaceService;

    @Override
    public void deployArtifact(@NotNull TypePermsDef permissions, @NotNull String workspace) {
        log.info("Type permissions module received: {}", permissions.getId());
        Set<EntityRef> coDeployedRefs = new HashSet<>(ArtifactDeployMeta.getThreadMeta().getCoDeployedArtifacts());
        EntityRef typeRef = WorkspaceServiceExtensionsKt.bindRefToWorkspace(
            workspaceService, permissions.getTypeRef(), workspace, coDeployedRefs
        );
        TypePermsDef scopedPermissions = permissions.copy().withTypeRef(typeRef).build();
        typePermsService.save(scopedPermissions, workspace);
    }

    @Override
    public void deleteArtifact(@NotNull String artifactId, @NotNull String workspace) {
        typePermsService.delete(IdInWs.create(workspace, artifactId));
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "model/permissions";
    }

    @Override
    public void listenChanges(@NotNull BiConsumer<TypePermsDef, String> consumer) {
        typePermsService.addListener((before, after) -> {
            if (after == null) {
                return Unit.INSTANCE;
            }
            TypePermsDef permissions = after.getDefinition();
            String portableTypeId = workspaceService.replaceWsPrefixToCurrentWsPlaceholder(
                permissions.getTypeRef().getLocalId()
            );
            EntityRef portableTypeRef = permissions.getTypeRef().withLocalId(portableTypeId);
            TypePermsDef exported = permissions.copy().withTypeRef(portableTypeRef).build();
            consumer.accept(exported, typePermsService.getWorkspace(permissions));
            return Unit.INSTANCE;
        });
    }
}
