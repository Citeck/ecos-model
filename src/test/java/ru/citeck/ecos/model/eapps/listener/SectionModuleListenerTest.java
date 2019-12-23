package ru.citeck.ecos.model.eapps.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.type.model.section.SectionModule;
import ru.citeck.ecos.model.converter.module.impl.SectionModuleConverter;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.service.impl.SectionServiceImpl;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class SectionModuleListenerTest {

    @MockBean
    private SectionServiceImpl sectionService;

    @MockBean
    private SectionModuleConverter sectionModuleConverter;

    private SectionModuleListener sectionModuleListener;

    @BeforeEach
    void init() {
        sectionModuleListener = new SectionModuleListener(sectionService, sectionModuleConverter);
    }

    @Test
    void onModulePublish() {


        SectionModule sectionModule = new SectionModule();
        sectionModule.setId("sectionId");

        SectionDto sectionDto = new SectionDto();
        sectionDto.setId("sectionDtoId");

        when(sectionModuleConverter.moduleToDto(sectionModule)).thenReturn(sectionDto);
        when(sectionService.save(sectionDto)).thenReturn(sectionDto);

        //  act
        sectionModuleListener.onModulePublished(sectionModule);

        //  assert
        Mockito.verify(sectionModuleConverter, times(1)).moduleToDto(sectionModule);
        Mockito.verify(sectionService, times(1)).save(sectionDto);
    }

    @Test
    void onModuleDelete() {

        //  act
        sectionModuleListener.onModuleDeleted("testId");

        //  assert
        Mockito.verify(sectionService, times(1)).delete("testId");
    }
}
