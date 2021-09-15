package ru.citeck.ecos.model.domain.permissions.eapp.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.model.domain.permissions.service.AttributesPermissionsService;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttributesPermissionModuleHandler implements EcosArtifactHandler<AttributesPermissionDto> {

    private final AttributesPermissionsService attributesPermissionsService;

    @Override
    public void deployArtifact(@NotNull AttributesPermissionDto permissions) {
        log.info("Form module received: " + permissions.getId() + " ");
        attributesPermissionsService.save(permissions);
    }

    @Override
    public void deleteArtifact(@NotNull String s) {
        attributesPermissionsService.delete(s);
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "model/attrs_permission";
    }

    @Override
    public void listenChanges(@NotNull Consumer<AttributesPermissionDto> consumer) {
        attributesPermissionsService.addListener(consumer);
    }
}
