package ru.citeck.ecos.model.eapps.listener;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.model.converter.module.ModuleConverter;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.service.SectionService;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Component
public class SectionModuleHandler implements EcosModuleHandler<SectionModule> {

    private final SectionService sectionService;
    private final ModuleConverter<SectionModule, SectionDto> sectionModuleConverter;

    @Override
    public void deployModule(@NotNull SectionModule sectionModule) {
        sectionService.save(sectionModuleConverter.moduleToDto(sectionModule));
    }

    @NotNull
    @Override
    public ModuleWithMeta<SectionModule> getModuleMeta(@NotNull SectionModule sectionModule) {

        List<ModuleRef> types = sectionModule.getTypes();
        if (types == null) {
            types = Collections.emptyList();
        }

        return new ModuleWithMeta<>(sectionModule, new ModuleMeta(sectionModule.getId(), types));
    }

    @Override
    public void listenChanges(@NotNull Consumer<SectionModule> consumer) {
    }

    @Nullable
    @Override
    public ModuleWithMeta<SectionModule> prepareToDeploy(@NotNull SectionModule sectionModule) {
        return getModuleMeta(sectionModule);
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "model/section";
    }
}
