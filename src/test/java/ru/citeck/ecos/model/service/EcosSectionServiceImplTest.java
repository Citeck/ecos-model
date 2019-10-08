package ru.citeck.ecos.model.service;


import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.domain.EcosSectionEntity;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.repository.EcosSectionRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.impl.EcosSectionServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
public class EcosSectionServiceImplTest {

    @Mock
    private EcosSectionRepository sectionRepository;

    @Mock
    private EcosTypeRepository typeRepository;

    private EcosSectionService ecosSectionService;

    @BeforeEach
    public void init() {
        ecosSectionService = new EcosSectionServiceImpl(sectionRepository, typeRepository);
    }


    @Test
    public void getAllReturnTypes() {

        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("a", 1L, null, "a",
            "a_desc", "a_tenant");
        EcosSectionEntity ecosSectionEntity2 = new EcosSectionEntity("b", 2L, null, "b",
            "b_desc", "b_tenant");

        List<EcosSectionEntity> entities = Arrays.asList(
            ecosSectionEntity,
            ecosSectionEntity2
        );

        given(sectionRepository.findAll()).willReturn(entities);


        Set<EcosSectionDto> dtos = ecosSectionService.getAll();


        Assert.assertEquals(2, dtos.size());
    }

    @Test
    public void getAllWhenReturnNothing() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            given(sectionRepository.findAll()).willReturn(null);


            ecosSectionService.getAll();
        });
    }

    @Test
    public void getAllSelectedReturnTypes() {

        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("b", 2L, null, "b",
            "b_desc", "b_tenant");

        Set<EcosSectionEntity> entities = Collections.singleton(ecosSectionEntity);

        given(sectionRepository.findAllByExtIds(Collections.singleton("b"))).willReturn(entities);


        Set<EcosSectionDto> dtos = ecosSectionService.getAll(Collections.singleton("b"));


        Assert.assertEquals(1, dtos.size());
    }

    @Test
    public void getAllSelectedNothing() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            given(sectionRepository.findAllByExtIds(Collections.singleton("b"))).willReturn(null);


            ecosSectionService.getAll(Collections.singleton("b"));
        });
    }

    @Test
    public void getByIdReturnTypeDto() {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("a", 1L, null, "a",
            "a_desc", "a_tenant");
        EcosSectionEntity ecosSectionEntity2 = new EcosSectionEntity("b", 2L, null, "b",
            "b_desc", "b_tenant");

        given(sectionRepository.findByExtId("b")).willReturn(Optional.of(ecosSectionEntity2));


        EcosSectionDto dto = ecosSectionService.getByExtId("b");


        Assert.assertEquals("b", dto.getId());
        Assert.assertEquals("b", dto.getName());
        Assert.assertEquals("b_desc", dto.getDescription());
        Assert.assertEquals("b_tenant", dto.getTenant());
    }

    @Test
    public void getByIdReturnNothing() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ecosSectionService.getByExtId("b");
        });
    }

    @Test
    public void deleteSuccess() {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("a",1L, null, "a",
            "a_desc", "a_tenant");

        given(sectionRepository.findByExtId("a")).willReturn(Optional.of(ecosSectionEntity));


        ecosSectionService.delete("a");


        Mockito.verify(sectionRepository, Mockito.times(1)).deleteById(Mockito.anyLong());

    }

    @Test
    public void deleteNoDeletion() {
        given(sectionRepository.findByExtId("a")).willReturn(Optional.empty());


        ecosSectionService.delete("a");


        Mockito.verify(sectionRepository, Mockito.times(0)).deleteById(Mockito.anyLong());
    }

    @Test
    public void updateSuccessNewEntity() {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("a", 1L, null, "aname",
            "a_desc", "a_tenant");

        given(sectionRepository.findByExtId("a")).willReturn(Optional.empty());


        EcosSectionDto dto = ecosSectionService.update(entityToDto(ecosSectionEntity));


        Mockito.verify(sectionRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
    }

    @Test
    public void updateSuccessWithNoUUIDNewEntity() {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity(null, 1L, null, "aname", "a_desc", "a_tenant");


        EcosSectionDto dto = ecosSectionService.update(entityToDto(ecosSectionEntity));


        Mockito.verify(sectionRepository, times(1)).save(Mockito.any());
        Assert.assertNotNull(dto.getId());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
    }

    private EcosSectionDto entityToDto(EcosSectionEntity entity) {
        Set<RecordRef> typesRefs = null;
        if (entity.getTypes() != null) {
            typesRefs = entity.getTypes().stream()
                .map(e -> RecordRef.create("type", e.getExtId()))
                .collect(Collectors.toSet());
        }
        return new EcosSectionDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            typesRefs);
    }
}
