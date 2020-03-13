package ru.citeck.ecos.model.eapps.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.model.converter.module.ModuleConverter;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypeModuleHandler implements EcosModuleHandler<TypeModule> {

    private final TypeService typeService;
    private final ModuleConverter<TypeModule, TypeDto> typeModuleConverter;

    @Override
    public void deployModule(@NotNull TypeModule module) {
        TypeDto dto = typeModuleConverter.moduleToDto(module);
        typeService.save(dto);
    }

    @NotNull
    @Override
    public ModuleWithMeta<TypeModule> getModuleMeta(@NotNull TypeModule module) {

        List<ModuleRef> dependencies = new ArrayList<>();

        if (module.getParent() == null) {
            dependencies.add(ModuleRef.valueOf("model/type$base"));
        } else {
            dependencies.add(module.getParent());
        }

        List<AssociationDto> assocs = module.getAssociations();
        if (assocs != null) {
            for (AssociationDto assoc : assocs) {
                dependencies.add(assoc.getTarget());
            }
        }

        return new ModuleWithMeta<>(module, new ModuleMeta(module.getId(), dependencies));
    }

    @NotNull
    @Override
    public String getModuleType() {
        return "model/type";
    }

    @Override
    public void listenChanges(@NotNull Consumer<TypeModule> consumer) {
        typeService.addListener(typeDto -> log.info("type changed: " + typeDto));
    }

    private <T> List<T> toList(Collection<T> collection) {
        if (collection == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(collection);
    }

    private ModuleRef toFormRef(String recordRef) {
        if (StringUtils.isBlank(recordRef)) {
            return null;
        }
        int idx = recordRef.indexOf('$');
        if (idx >= 0) {
            recordRef = recordRef.substring(idx + 1);
        }
        return ModuleRef.create("ui/form", recordRef);
    }

    private List<ModuleRef> toModuleRefs(List<RecordRef> recordRefs) {
        if (recordRefs == null) {
            return Collections.emptyList();
        }
        return recordRefs.stream().map(this::toModuleRef).collect(Collectors.toList());
    }

    private ModuleRef toModuleRef(RecordRef recordRef) {
        if (recordRef == null) {
            return null;
        }
        switch (recordRef.getSourceId()) {
            case "type":
                return ModuleRef.create("model/type", recordRef.getId());
            case "eform":
                return ModuleRef.create("ui/form", recordRef.getId());
            case "action":
                return ModuleRef.create("ui/action", recordRef.getId());
        }
        log.warn("Unknown ref: '" + recordRef + "'");
        return null;
    }

    @Nullable
    @Override
    public ModuleWithMeta<TypeModule> prepareToDeploy(@NotNull TypeModule typeModule) {
        return getModuleMeta(typeModule);
    }
}
