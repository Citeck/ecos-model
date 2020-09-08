package ru.citeck.ecos.model.permissions.eapps.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.model.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.model.permissions.service.AttributesPermissionsService;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttributesPermissionModuleHandler implements EcosModuleHandler<AttributesPermissionDto> {

    private final AttributesPermissionsService attributesPermissionsService;

    @Override
    public void deployModule(@NotNull AttributesPermissionDto permissions) {
        log.info("Form module received: " + permissions.getId() + " ");
        attributesPermissionsService.save(permissions);
    }

    @NotNull
    @Override
    public ModuleWithMeta<AttributesPermissionDto> getModuleMeta(@NotNull AttributesPermissionDto module) {
        return new ModuleWithMeta<>(module, new ModuleMeta(module.getId()));
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "model/attrs_permission";
    }

    @Override
    public void listenChanges(@NotNull Consumer<AttributesPermissionDto> consumer) {
        attributesPermissionsService.addListener(consumer);
    }

    @Nullable
    @Override
    public ModuleWithMeta<AttributesPermissionDto> prepareToDeploy(@NotNull AttributesPermissionDto module) {
        return getModuleMeta(module);
    }
}
