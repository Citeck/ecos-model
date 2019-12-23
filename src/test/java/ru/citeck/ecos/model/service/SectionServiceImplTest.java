package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.dto.impl.SectionConverter;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.repository.SectionRepository;
import ru.citeck.ecos.model.service.impl.SectionServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
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
        sectionDto.setName("name");
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
        when(sectionConverter.targetToSource(sectionEntity)).thenReturn(sectionDto);

        //  act
        Set<SectionDto> resultSectionDtos = sectionService.getAll();

        //  assert
        Assert.assertEquals(resultSectionDtos.size(), 1);
        SectionDto resultSectionDto = resultSectionDtos.iterator().next();
        Assert.assertEquals(resultSectionDto.getName(), sectionEntity.getName());
        Assert.assertEquals(resultSectionDto.getTenant(), sectionEntity.getTenant());
        Assert.assertEquals(resultSectionDto.getDescription(), sectionEntity.getDescription());
        Assert.assertEquals(resultSectionDto.getId(), sectionEntity.getExtId());
        Assert.assertEquals(resultSectionDto.getTypes().size(), sectionEntity.getTypes().size());
        RecordRef resultTypeRef = resultSectionDto.getTypes().iterator().next();
        TypeEntity type = sectionEntity.getTypes().iterator().next();
        Assert.assertEquals(resultTypeRef.getId(), type.getExtId());
    }

    @Test
    void testGetAllWithExtIdsArgs() {

        //  arrange
        when(sectionRepository.findAllByExtIds(Collections.singleton(sectionExtId)))
            .thenReturn(Collections.singleton(sectionEntity));
        when(sectionConverter.targetToSource(sectionEntity)).thenReturn(sectionDto);

        //  act
        Set<SectionDto> resultSectionDtos = sectionService.getAll(Collections.singleton(sectionExtId));

        //  assert
        Assert.assertEquals(resultSectionDtos.size(), 1);
        SectionDto resultSectionDto = resultSectionDtos.iterator().next();
        Assert.assertEquals(resultSectionDto.getName(), sectionEntity.getName());
        Assert.assertEquals(resultSectionDto.getTenant(), sectionEntity.getTenant());
        Assert.assertEquals(resultSectionDto.getDescription(), sectionEntity.getDescription());
        Assert.assertEquals(resultSectionDto.getId(), sectionEntity.getExtId());
        Assert.assertEquals(resultSectionDto.getTypes().size(), sectionEntity.getTypes().size());
        RecordRef resultTypeRef = resultSectionDto.getTypes().iterator().next();
        TypeEntity type = sectionEntity.getTypes().iterator().next();
        Assert.assertEquals(resultTypeRef.getId(), type.getExtId());
    }

    @Test
    void testGetByExtId() {

        //  arrange
        when(sectionRepository.findByExtId(sectionExtId)).thenReturn(Optional.of(sectionEntity));
        when(sectionConverter.targetToSource(sectionEntity)).thenReturn(sectionDto);

        //  act
        SectionDto resultSectionDto = sectionService.getByExtId(sectionExtId);

        //  assert
        Assert.assertEquals(resultSectionDto.getName(), sectionEntity.getName());
        Assert.assertEquals(resultSectionDto.getTenant(), sectionEntity.getTenant());
        Assert.assertEquals(resultSectionDto.getDescription(), sectionEntity.getDescription());
        Assert.assertEquals(resultSectionDto.getId(), sectionEntity.getExtId());
        Assert.assertEquals(resultSectionDto.getTypes().size(), sectionEntity.getTypes().size());
        RecordRef resultTypeRef = resultSectionDto.getTypes().iterator().next();
        TypeEntity type = sectionEntity.getTypes().iterator().next();
        Assert.assertEquals(resultTypeRef.getId(), type.getExtId());
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
            Mockito.verify(sectionConverter, Mockito.times(0)).targetToSource(Mockito.any());
            Assert.assertEquals(iae.getMessage(), "Section doesnt exists: " + sectionExtId);
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
        when(sectionConverter.sourceToTarget(sectionDto)).thenReturn(sectionEntity);

        //  act
        sectionService.save(sectionDto);

        //  assert
        Mockito.verify(sectionRepository, Mockito.times(1)).save(sectionEntity);
        Mockito.verify(sectionConverter, Mockito.times(1)).targetToSource(sectionEntity);
    }
}
