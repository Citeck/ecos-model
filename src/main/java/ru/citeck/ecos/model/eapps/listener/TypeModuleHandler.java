package ru.citeck.ecos.model.eapps.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.handler.EcosModuleHandler;
import ru.citeck.ecos.apps.module.handler.ModuleMeta;
import ru.citeck.ecos.apps.module.handler.ModuleWithMeta;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypeModuleHandler implements EcosModuleHandler<TypeDto> {

    private final TypeService typeService;

    private boolean disableListener = false;

    @Override
    public void deployModule(@NotNull TypeDto module) {
        typeService.save(module);
    }

    public void doWithoutChangeListener(Runnable action) {
        disableListener = true;
        try {
            action.run();
        } finally {
            disableListener = false;
        }
    }

    @NotNull
    @Override
    public ModuleWithMeta<TypeDto> getModuleMeta(@NotNull TypeDto module) {

        List<RecordRef> dependencies = new ArrayList<>();

        if (RecordRef.isEmpty(module.getParent())) {
            dependencies.add(RecordRef.valueOf("emodel/type@base"));
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
    public void listenChanges(@NotNull Consumer<TypeDto> consumer) {
        typeService.addListener(m -> {
            if (!disableListener) {
                consumer.accept(m);
            }
        });
    }

    @Nullable
    @Override
    public ModuleWithMeta<TypeDto> prepareToDeploy(@NotNull TypeDto typeModule) {
        return getModuleMeta(typeModule);
    }
}
