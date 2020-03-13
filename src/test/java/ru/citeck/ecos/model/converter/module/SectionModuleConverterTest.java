package ru.citeck.ecos.model.converter.module;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.model.converter.module.impl.SectionModuleConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.eapps.listener.SectionModule;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
public class SectionModuleConverterTest {

    private SectionModuleConverter sectionModuleConverter;

    private SectionModule sectionModule;
    private SectionDto sectionDto;

    @BeforeEach
    void setUp() {
        sectionModuleConverter = new SectionModuleConverter();

        sectionModule = new SectionModule();
        sectionModule.setId("sectionId");
        sectionModule.setName("name");
        sectionModule.setDescription("desc");
        sectionModule.setTypes(Collections.singletonList(ModuleRef.create("TypeModule", "typeId")));

        sectionDto = new SectionDto();
        sectionDto.setId("sectionId");
        sectionDto.setName("name");
        sectionDto.setDescription("desc");
        sectionDto.setTenant("tenant");
        sectionDto.setTypes(Collections.singleton(RecordRef.create(TypeRecordsDao.ID, "typeId")));
    }

    @Test
    void testModuleToDto() {

        //  act
        SectionDto resultDto = sectionModuleConverter.moduleToDto(sectionModule);

        //  assert
        Assert.assertEquals(sectionModule.getId(), resultDto.getId());
        Assert.assertEquals(sectionModule.getName(), resultDto.getName());
        Assert.assertEquals(sectionModule.getDescription(), resultDto.getDescription());
        Assert.assertEquals(sectionModule.getTypes().size(), resultDto.getTypes().size());
        Assert.assertEquals(sectionModule.getTypes().iterator().next().getId(),
                            resultDto.getTypes().iterator().next().getId());
    }

    @Test
    void testModuleToDtoWithoutTypes() {

        //  arrange
        sectionModule.setTypes(null);

        //  act
        SectionDto resultDto = sectionModuleConverter.moduleToDto(sectionModule);

        //  assert
        Assert.assertEquals(sectionModule.getId(), resultDto.getId());
        Assert.assertEquals(sectionModule.getName(), resultDto.getName());
        Assert.assertEquals(sectionModule.getDescription(), resultDto.getDescription());
        Assert.assertEquals(0, resultDto.getTypes().size());
    }
}
