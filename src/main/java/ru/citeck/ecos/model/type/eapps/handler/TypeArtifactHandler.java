package ru.citeck.ecos.model.type.eapps.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.model.type.dto.TypeDef;
import ru.citeck.ecos.model.type.service.TypeService;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TypeArtifactHandler implements EcosArtifactHandler<TypeDef> {

    private final TypeService typeService;

    @Override
    public void deployArtifact(@NotNull TypeDef artifact) {
        typeService.save(artifact);
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "model/type";
    }

    @Override
    public void listenChanges(@NotNull Consumer<TypeDef> consumer) {
        typeService.addListener(consumer);
    }
}
