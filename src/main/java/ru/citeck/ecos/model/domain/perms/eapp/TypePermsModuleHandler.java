package ru.citeck.ecos.model.domain.perms.eapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.model.domain.perms.service.TypePermsService;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypePermsModuleHandler implements EcosArtifactHandler<TypePermsDef> {

    private final TypePermsService typePermsService;

    @Override
    public void deployArtifact(@NotNull TypePermsDef permissions) {
        log.info("Type permissions module received: " + permissions.getId() + " ");
        typePermsService.save(permissions);
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "model/permissions";
    }

    @Override
    public void listenChanges(@NotNull Consumer<TypePermsDef> consumer) {

    }
}
