package ru.citeck.ecos.model.section.eapps.handler;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.service.SectionService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Component
public class SectionModuleHandler implements EcosModuleHandler<SectionDto> {

    private final SectionService sectionService;

    @Override
    public void deployModule(@NotNull SectionDto sectionModule) {
        sectionService.save(sectionModule);
    }

    @NotNull
    @Override
    public ModuleWithMeta<SectionDto> getModuleMeta(@NotNull SectionDto sectionModule) {

        Set<RecordRef> types = sectionModule.getTypes();
        List<RecordRef> dependencies = new ArrayList<>();
        if (types != null) {
            dependencies.addAll(types);
        }

        return new ModuleWithMeta<>(sectionModule, new ModuleMeta(sectionModule.getId(), dependencies));
    }

    @Override
    public void listenChanges(@NotNull Consumer<SectionDto> consumer) {
        sectionService.addListener(consumer);
    }

    @Nullable
    @Override
    public ModuleWithMeta<SectionDto> prepareToDeploy(@NotNull SectionDto sectionModule) {
        return getModuleMeta(sectionModule);
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "model/section";
    }
}
