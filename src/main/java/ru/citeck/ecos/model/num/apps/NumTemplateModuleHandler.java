package ru.citeck.ecos.model.num.apps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.model.num.dto.NumTemplateDto;
import ru.citeck.ecos.model.num.service.NumTemplateService;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class NumTemplateModuleHandler implements EcosModuleHandler<NumTemplateDto> {

    private final NumTemplateService numTemplateService;

    @Override
    public void deployModule(@NotNull NumTemplateDto module) {
        numTemplateService.save(module);
    }

    @NotNull
    @Override
    public ModuleWithMeta<NumTemplateDto> getModuleMeta(@NotNull NumTemplateDto module) {
        return new ModuleWithMeta<>(module, new ModuleMeta(module.getId()));
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "model/num-template";
    }

    @Override
    public void listenChanges(@NotNull Consumer<NumTemplateDto> consumer) {
        numTemplateService.addListener(consumer);
    }

    @Nullable
    @Override
    public ModuleWithMeta<NumTemplateDto> prepareToDeploy(@NotNull NumTemplateDto typeModule) {
        return getModuleMeta(typeModule);
    }
}
