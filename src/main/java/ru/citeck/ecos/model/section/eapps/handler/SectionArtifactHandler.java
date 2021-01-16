package ru.citeck.ecos.model.section.eapps.handler;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.service.SectionService;

import java.util.function.Consumer;

@RequiredArgsConstructor
@Component
public class SectionArtifactHandler implements EcosArtifactHandler<SectionDto> {

    private final SectionService sectionService;

    @Override
    public void deployArtifact(@NotNull SectionDto sectionModule) {
        sectionService.save(sectionModule);
    }

    @Override
    public void listenChanges(@NotNull Consumer<SectionDto> consumer) {
        sectionService.addListener(consumer);
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "model/section";
    }
}
