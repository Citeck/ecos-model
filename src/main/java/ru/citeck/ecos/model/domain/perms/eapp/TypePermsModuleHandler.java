package ru.citeck.ecos.model.domain.perms.eapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.model.domain.perms.service.TypePermsService;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypePermsModuleHandler implements EcosModuleHandler<TypePermsDef> {

    private final TypePermsService typePermsService;

    @Override
    public void deployModule(@NotNull TypePermsDef permissions) {
        log.info("Type permissions module received: " + permissions.getId() + " ");
        typePermsService.save(permissions);
    }

    @NotNull
    @Override
    public ModuleWithMeta<TypePermsDef> getModuleMeta(@NotNull TypePermsDef module) {
        return new ModuleWithMeta<>(module, new ModuleMeta(module.getId()));
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "model/permissions";
    }

    @Override
    public void listenChanges(@NotNull Consumer<TypePermsDef> consumer) {

    }

    @Nullable
    @Override
    public ModuleWithMeta<TypePermsDef> prepareToDeploy(@NotNull TypePermsDef module) {
        return getModuleMeta(module);
    }
}
