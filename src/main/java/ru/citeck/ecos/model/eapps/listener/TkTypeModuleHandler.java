package ru.citeck.ecos.model.eapps.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TkTypeModuleHandler implements EcosModuleHandler<TypeDto> {

    private final TypeService typeService;
    private final TypeModuleHandler typeModuleHandler;

    @Override
    public void deployModule(@NotNull TypeDto module) {
        log.info("Received TK type to deploy: " + module.getId());
        TypeDto currentType = typeService.getByExtId(module.getId());
        currentType.setParent(module.getParent());
        currentType.setName(module.getName());
        typeService.save(currentType);
    }

    @NotNull
    @Override
    public ModuleWithMeta<TypeDto> getModuleMeta(@NotNull TypeDto module) {
        return typeModuleHandler.getModuleMeta(module);
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "model/tk_type";
    }

    @Override
    public void listenChanges(@NotNull Consumer<TypeDto> consumer) {
    }

    @Nullable
    @Override
    public ModuleWithMeta<TypeDto> prepareToDeploy(@NotNull TypeDto typeModule) {
        return getModuleMeta(typeModule);
    }
}
