package ru.citeck.ecos.model.converter.dto;

import org.apache.logging.log4j.util.Strings;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.section.converter.SectionConverter;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.repository.SectionRepository;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
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
        sectionDto.setName("name");
        sectionDto.setTenant("tenant");
        sectionDto.setDescription("desc");
        sectionDto.setTypes(Collections.singleton(RecordRef.create("type", "type")));

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
        Assert.assertEquals(resultSectionEntity.getId().longValue(), 123L);
        Assert.assertEquals(resultSectionEntity.getExtId(), sectionDto.getId());
        Assert.assertEquals(resultSectionEntity.getName(), sectionDto.getName());
        Assert.assertEquals(resultSectionEntity.getTenant(), sectionDto.getTenant());
        Assert.assertEquals(resultSectionEntity.getDescription(), sectionDto.getDescription());
        Assert.assertEquals(resultSectionEntity.getTypes(), Collections.singleton(typeEntity));
    }

    @Test
    void testDtoToEntityWithoutTypesAndBlankExtId() {

        //  arrange
        sectionDto.setTypes(Collections.emptySet());
        sectionDto.setId(Strings.EMPTY);

        //  act
        SectionEntity resultSectionEntity = sectionConverter.dtoToEntity(sectionDto);

        //  assert
        Assert.assertEquals(resultSectionEntity.getName(), sectionDto.getName());
        Assert.assertEquals(resultSectionEntity.getTenant(), sectionDto.getTenant());
        Assert.assertEquals(resultSectionEntity.getDescription(), sectionDto.getDescription());

        // checking that extId it is UUID
        UUID.fromString(resultSectionEntity.getExtId());
    }

    @Test
    void testEntityToDto() {
        SectionDto resultSectionDto = sectionConverter.entityToDto(sectionEntity);
        Assert.assertEquals(sectionDto, resultSectionDto);
    }

    @Test
    void testEntityToDtoWithoutTypes() {

        //  arrange
        sectionEntity.setTypes(null);
        sectionDto.setTypes(null);

        //  act
        SectionDto resultSectionDto = sectionConverter.entityToDto(sectionEntity);

        //  assert
        Assert.assertEquals(sectionDto, resultSectionDto);
    }
}
