package ru.citeck.ecos.model.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.section.converter.SectionConverter;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.type.repository.TypeEntity;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.repository.SectionRepository;
import ru.citeck.ecos.model.section.service.impl.SectionServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(EcosSpringExtension.class)
@SpringBootTest(classes = EcosModelApp.class)
public class SectionServiceImplTest {

    @MockBean
    private SectionRepository sectionRepository;

    @MockBean
    private SectionConverter sectionConverter;

    private SectionServiceImpl sectionService;

    private SectionDto sectionDto;
    private SectionEntity sectionEntity;
    private String sectionExtId;

    @BeforeEach
    public void init() {
        sectionService = new SectionServiceImpl(sectionRepository, sectionConverter);

        sectionExtId = "section";

        Set<RecordRef> typesRefs = Collections.singleton(RecordRef.create("type", "type"));

        sectionDto = new SectionDto();
        sectionDto.setId(sectionExtId);
        sectionDto.setName(new MLText("name"));
        sectionDto.setDescription("desc");
        sectionDto.setTenant("tenant");
        sectionDto.setTypes(typesRefs);

        TypeEntity typeEntity = new TypeEntity();
        typeEntity.setExtId("type");

        Set<TypeEntity> types = Collections.singleton(typeEntity);

        sectionEntity = new SectionEntity();
        sectionEntity.setId(1L);
        sectionEntity.setExtId(sectionExtId);
        sectionEntity.setName("name");
        sectionEntity.setDescription("desc");
        sectionEntity.setTenant("tenant");
        sectionEntity.setTypes(types);
    }

    @Test
    void testGetAll() {

        //  arrange
        when(sectionRepository.findAll()).thenReturn(Collections.singletonList(sectionEntity));
        when(sectionConverter.entityToDto(sectionEntity)).thenReturn(sectionDto);

        //  act
        Set<SectionDto> resultSectionDtos = sectionService.getAll();

        //  assert
        assertEquals(resultSectionDtos.size(), 1);
        SectionDto resultSectionDto = resultSectionDtos.iterator().next();
        assertEquals(resultSectionDto.getName(), Json.getMapper().read(sectionEntity.getName(), MLText.class));
        assertEquals(resultSectionDto.getTenant(), sectionEntity.getTenant());
        assertEquals(resultSectionDto.getDescription(), sectionEntity.getDescription());
        assertEquals(resultSectionDto.getId(), sectionEntity.getExtId());
        assertEquals(resultSectionDto.getTypes().size(), sectionEntity.getTypes().size());
        RecordRef resultTypeRef = resultSectionDto.getTypes().iterator().next();
        TypeEntity type = sectionEntity.getTypes().iterator().next();
        assertEquals(resultTypeRef.getId(), type.getExtId());
    }

    @Test
    void testGetAllWithExtIdsArgs() {

        //  arrange
        when(sectionRepository.findAllByExtIds(Collections.singleton(sectionExtId)))
            .thenReturn(Collections.singleton(sectionEntity));
        when(sectionConverter.entityToDto(sectionEntity)).thenReturn(sectionDto);

        //  act
        Set<SectionDto> resultSectionDtos = sectionService.getAll(Collections.singleton(sectionExtId));

        //  assert
        assertEquals(resultSectionDtos.size(), 1);
        SectionDto resultSectionDto = resultSectionDtos.iterator().next();
        assertEquals(resultSectionDto.getName(), Json.getMapper().read(sectionEntity.getName(), MLText.class));
        assertEquals(resultSectionDto.getTenant(), sectionEntity.getTenant());
        assertEquals(resultSectionDto.getDescription(), sectionEntity.getDescription());
        assertEquals(resultSectionDto.getId(), sectionEntity.getExtId());
        assertEquals(resultSectionDto.getTypes().size(), sectionEntity.getTypes().size());
        RecordRef resultTypeRef = resultSectionDto.getTypes().iterator().next();
        TypeEntity type = sectionEntity.getTypes().iterator().next();
        assertEquals(resultTypeRef.getId(), type.getExtId());
    }

    @Test
    void testGetByExtId() {

        //  arrange
        when(sectionRepository.findByExtId(sectionExtId)).thenReturn(Optional.of(sectionEntity));
        when(sectionConverter.entityToDto(sectionEntity)).thenReturn(sectionDto);

        //  act
        SectionDto resultSectionDto = sectionService.getByExtId(sectionExtId);

        //  assert
        assertEquals(resultSectionDto.getName(), Json.getMapper().read(sectionEntity.getName(), MLText.class));
        assertEquals(resultSectionDto.getTenant(), sectionEntity.getTenant());
        assertEquals(resultSectionDto.getDescription(), sectionEntity.getDescription());
        assertEquals(resultSectionDto.getId(), sectionEntity.getExtId());
        assertEquals(resultSectionDto.getTypes().size(), sectionEntity.getTypes().size());
        RecordRef resultTypeRef = resultSectionDto.getTypes().iterator().next();
        TypeEntity type = sectionEntity.getTypes().iterator().next();
        assertEquals(resultTypeRef.getId(), type.getExtId());
    }

    @Test
    void testGetByExtIdThrowsException() {

        //  arrange
        when(sectionRepository.findByExtId(sectionExtId)).thenReturn(Optional.empty());

        //  act
        try {
            sectionService.getByExtId(sectionExtId);
        } catch (IllegalArgumentException iae) {

            //  assert
            Mockito.verify(sectionConverter, Mockito.times(0)).entityToDto(Mockito.any());
            assertEquals(iae.getMessage(), "Section doesnt exists: " + sectionExtId);
        }
    }

    @Test
    void testDelete() {

        //  arrange
        when(sectionRepository.findByExtId(sectionExtId)).thenReturn(Optional.of(sectionEntity));

        //  act
        sectionService.delete(sectionExtId);

        //  assert
        Mockito.verify(sectionRepository, Mockito.times(1)).deleteById(1L);
    }

    @Test
    void testUpdate() {

        //  arrange
        when(sectionConverter.dtoToEntity(sectionDto)).thenReturn(sectionEntity);

        //  act
        sectionService.save(sectionDto);

        //  assert
        Mockito.verify(sectionRepository, Mockito.times(1)).save(sectionEntity);
        Mockito.verify(sectionConverter, Mockito.times(1)).entityToDto(sectionEntity);
    }
}
