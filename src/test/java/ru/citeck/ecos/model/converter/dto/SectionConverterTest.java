package ru.citeck.ecos.model.converter.dto;

import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.section.converter.SectionConverter;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.repository.SectionRepository;
import ru.citeck.ecos.model.type.repository.TypeEntity;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(EcosSpringExtension.class)
public class SectionConverterTest {

    @MockBean
    private TypeRepository typeRepository;

    @MockBean
    private SectionRepository sectionRepository;

    private SectionConverter sectionConverter;

    private SectionDto sectionDto;
    private SectionEntity sectionEntity;

    private TypeEntity typeEntity;

    @BeforeEach
    void setUp() {
        sectionConverter = new SectionConverter(typeRepository, sectionRepository);

        typeEntity = new TypeEntity();
        typeEntity.setExtId("type");

        sectionDto = new SectionDto();
        sectionDto.setId("section");
        sectionDto.setName(new MLText("name"));
        sectionDto.setTenant("tenant");
        sectionDto.setDescription("desc");
        sectionDto.setTypes(Collections.singleton(TypeUtils.getTypeRef(("type"))));

        sectionEntity = new SectionEntity();
        sectionEntity.setExtId("section");
        sectionEntity.setId(123L);
        sectionEntity.setName("name");
        sectionEntity.setDescription("desc");
        sectionEntity.setTenant("tenant");
        sectionEntity.setTypes(Collections.singleton(typeEntity));
    }

    @Test
    void testDtoToEntity() {

        //  arrange
        when(typeRepository.findAllByExtIds(Collections.singleton("type"))).thenReturn(Collections.singleton(typeEntity));
        when(sectionRepository.findByExtId(sectionEntity.getExtId())).thenReturn(Optional.of(sectionEntity));

        //  act
        SectionEntity resultSectionEntity = sectionConverter.dtoToEntity(sectionDto);

        //  assert
        assertEquals(resultSectionEntity.getId().longValue(), 123L);
        assertEquals(resultSectionEntity.getExtId(), sectionDto.getId());
        assertEquals(Json.getMapper().read(resultSectionEntity.getName(), MLText.class), sectionDto.getName());
        assertEquals(resultSectionEntity.getTenant(), sectionDto.getTenant());
        assertEquals(resultSectionEntity.getDescription(), sectionDto.getDescription());
        assertEquals(resultSectionEntity.getTypes(), Collections.singleton(typeEntity));
    }

    @Test
    void testDtoToEntityWithoutTypesAndBlankExtId() {

        //  arrange
        sectionDto.setTypes(Collections.emptySet());
        sectionDto.setId(Strings.EMPTY);

        //  act
        SectionEntity resultSectionEntity = sectionConverter.dtoToEntity(sectionDto);

        //  assert
        assertEquals(Json.getMapper().read(resultSectionEntity.getName(), MLText.class), sectionDto.getName());
        assertEquals(resultSectionEntity.getTenant(), sectionDto.getTenant());
        assertEquals(resultSectionEntity.getDescription(), sectionDto.getDescription());

        // checking that extId it is UUID
        UUID.fromString(resultSectionEntity.getExtId());
    }

    @Test
    void testEntityToDto() {
        SectionDto resultSectionDto = sectionConverter.entityToDto(sectionEntity);
        assertEquals(sectionDto, resultSectionDto);
    }

    @Test
    void testEntityToDtoWithoutTypes() {

        //  arrange
        sectionEntity.setTypes(null);
        sectionDto.setTypes(null);

        //  act
        SectionDto resultSectionDto = sectionConverter.entityToDto(sectionEntity);

        //  assert
        assertEquals(sectionDto, resultSectionDto);
    }
}
