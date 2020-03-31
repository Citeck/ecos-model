package ru.citeck.ecos.model.eapps.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TkTypeModuleHandler implements EcosModuleHandler<TypeDto> {

    private final TypeService typeService;
    private final TypeModuleHandler typeModuleHandler;

    @Override
    public void deployModule(@NotNull TypeDto module) {

        log.info("Received TK type to deploy: " + module.getId());

        TypeDto currentType = typeService.getByExtIdOrNull(module.getId());
        if (currentType == null) {
            currentType = new TypeDto();
            currentType.setId(module.getId());
            currentType.setName(module.getName());
        } else {
            MLText name = currentType.getName();
            if (name == null || name.getAsMap().size() == 1) {
                currentType.setName(module.getName());
            }
        }
        currentType.setParent(module.getParent());


        TypeDto finalType = currentType;
        typeModuleHandler.doWithoutChangeListener(() -> typeService.save(finalType));
    }

    @NotNull
    @Override
    public ModuleWithMeta<TypeDto> getModuleMeta(@NotNull TypeDto module) {

        ModuleWithMeta<TypeDto> meta = typeModuleHandler.getModuleMeta(module);

        List<RecordRef> dependencies = meta.getMeta()
            .getDependencies()
            .stream()
            .map(d -> RecordRef.create(d.getAppName(), "tk_type", d.getId()))
            .collect(Collectors.toList());

        return new ModuleWithMeta<>(meta.getModule(), new ModuleMeta(meta.getMeta().getId(), dependencies));
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
