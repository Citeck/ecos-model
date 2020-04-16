package ru.citeck.ecos.model.type.eapps.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.service.TypeService;
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

            MLText newName = module.getName();
            MLText currentName = currentType.getName();

            if (isValidNewName(newName, module.getId()) && !isValidNewName(currentName, module.getId())) {
                currentType.setName(module.getName());
            }
        }
        currentType.setParent(module.getParent());


        TypeDto finalType = currentType;
        typeModuleHandler.doWithoutChangeListener(() -> typeService.save(finalType));
    }

    private boolean isValidNewName(MLText newName, String moduleId) {

        if (newName == null || moduleId == null) {
            return false;
        }
        return !newName.getAsMap().values().contains(moduleId);
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
