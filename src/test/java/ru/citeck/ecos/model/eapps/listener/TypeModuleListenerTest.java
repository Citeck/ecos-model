package ru.citeck.ecos.model.eapps.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.converter.module.impl.TypeModuleConverter;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.impl.TypeServiceImpl;

import java.util.Collections;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TypeModuleListenerTest {

    @MockBean
    private TypeServiceImpl typeService;

    @MockBean
    private TypeModuleConverter typeModuleConverter;

    private TypeModuleHandler typeModuleListener;

    @BeforeEach
    void init() {
        typeModuleListener = new TypeModuleHandler(typeService, typeModuleConverter);
    }

    @Test
    void onModulePublish() {

        //  arrange
        TypeModule typeModule = new TypeModule();
        typeModule.setId("testId");
        typeModule.setName(new MLText("name"));

        AssociationDto associationDto = new AssociationDto();
        associationDto.setId("test-assoc");
        associationDto.setName(new MLText("assoc-name"));
        associationDto.setTarget(ModuleRef.create("model/type", "targetId"));
        typeModule.setAssociations(Collections.singletonList(associationDto));

        TypeDto typeDto = new TypeDto();
        typeDto.setId("testId");

        when(typeModuleConverter.moduleToDto(typeModule)).thenReturn(typeDto);
        when(typeService.save(typeDto)).thenReturn(typeDto);

        //  act
        typeModuleListener.deployModule(typeModule);

        //  assert
        Mockito.verify(typeModuleConverter, times(1)).moduleToDto(typeModule);
        Mockito.verify(typeService, times(1)).save(typeDto);
    }
}
