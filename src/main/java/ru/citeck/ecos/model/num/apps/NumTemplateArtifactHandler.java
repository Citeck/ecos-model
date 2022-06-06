package ru.citeck.ecos.model.num.apps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.model.num.dto.NumTemplateDto;
import ru.citeck.ecos.model.num.service.NumTemplateService;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class NumTemplateArtifactHandler implements EcosArtifactHandler<NumTemplateDto> {

    private final NumTemplateService numTemplateService;

    @Override
    public void deployArtifact(@NotNull NumTemplateDto module) {
        numTemplateService.save(module);
    }

    @Override
    public void deleteArtifact(@NotNull String s) {
        numTemplateService.delete(s);
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "model/num-template";
    }

    @Override
    public void listenChanges(@NotNull Consumer<NumTemplateDto> consumer) {
        numTemplateService.addListener((before, after) -> {
            if (after != null) {
                consumer.accept(new NumTemplateDto(after.getEntity()));
            }
        });
    }
}
