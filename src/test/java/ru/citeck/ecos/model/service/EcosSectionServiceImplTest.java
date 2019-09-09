package ru.citeck.ecos.model.service;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import ru.citeck.ecos.model.domain.EcosSectionEntity;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.repository.EcosSectionRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.impl.EcosSectionServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class EcosSectionServiceImplTest {

    @Mock
    private EcosSectionRepository sectionRepository;

    @Mock
    private EcosTypeRepository typeRepository;

    private EcosSectionService ecosSectionService;

    @Before
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


        List<EcosSectionDto> dtos = ecosSectionService.getAll();


        Assert.assertEquals(2, dtos.size());
        Assert.assertEquals("a", dtos.get(0).getUuid());
        Assert.assertEquals("b", dtos.get(1).getUuid());
    }

    @Test(expected = NullPointerException.class)
    public void getAllWhenReturnNothing() {

        given(sectionRepository.findAll()).willReturn(null);


        List<EcosSectionDto> dtos = ecosSectionService.getAll();
    }

    @Test
    public void getAllSelectedReturnTypes() {

        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("a",1L, null, "a",
            "a_desc", "a_tenant");
        EcosSectionEntity ecosSectionEntity2 = new EcosSectionEntity("b", 2L, null, "b",
            "b_desc", "b_tenant");

        List<EcosSectionEntity> entities = Arrays.asList(
            ecosSectionEntity2
        );

        given(sectionRepository.findAllByUuid(Arrays.asList("b"))).willReturn(entities);


        List<EcosSectionDto> dtos = ecosSectionService.getAll(Arrays.asList("b"));


        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals("b", dtos.get(0).getUuid());
    }

    @Test(expected = NullPointerException.class)
    public void getAllSelectedNothing() {

        given(sectionRepository.findAllByUuid(Arrays.asList("b"))).willReturn(null);


        List<EcosSectionDto> dtos = ecosSectionService.getAll(Arrays.asList("b"));
    }

    @Test
    public void getByIdReturnTypeDto() {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("a", 1L, null, "a",
            "a_desc", "a_tenant");
        EcosSectionEntity ecosSectionEntity2 = new EcosSectionEntity("b", 2L, null, "b",
            "b_desc", "b_tenant");

        given(sectionRepository.findByUuid("b")).willReturn(Optional.of(ecosSectionEntity2));


        Optional<EcosSectionDto> optionalDto = ecosSectionService.getByUuid("b");


        Assert.assertTrue(optionalDto.isPresent());
        Assert.assertEquals("b", optionalDto.get().getUuid());
        Assert.assertEquals("b", optionalDto.get().getName());
        Assert.assertEquals("b_desc", optionalDto.get().getDescription());
        Assert.assertEquals("b_tenant", optionalDto.get().getTenant());
    }

    @Test
    public void getByIdReturnNothing() {


        Optional<EcosSectionDto> optionalDto = ecosSectionService.getByUuid("b");


        Assert.assertFalse(optionalDto.isPresent());
    }

    @Test
    public void deleteSuccess() {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("a",1L, null, "a",
            "a_desc", "a_tenant");

        given(sectionRepository.findByUuid("a")).willReturn(Optional.of(ecosSectionEntity));


        ecosSectionService.delete("a");


        Mockito.verify(sectionRepository, Mockito.times(1)).deleteById(Mockito.anyLong());

    }

    @Test
    public void deleteNoDeletion() {
        given(sectionRepository.findByUuid("a")).willReturn(Optional.empty());


        ecosSectionService.delete("a");


        Mockito.verify(sectionRepository, Mockito.times(0)).deleteById(Mockito.anyLong());
    }

    @Test
    public void updateSuccessNewEntity() {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity("a", 1L, null, "aname",
            "a_desc", "a_tenant");

        given(sectionRepository.findByUuid("a")).willReturn(Optional.empty());


        EcosSectionDto dto = ecosSectionService.update(entityToDto(ecosSectionEntity));


        Mockito.verify(sectionRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getUuid());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
    }

    @Test
    public void updateSuccessWithNoUUIDNewEntity() {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity(null, 1L, null, "aname", "a_desc", "a_tenant");

        given(sectionRepository.findByUuid(Mockito.anyString())).willReturn(Optional.empty());


        EcosSectionDto dto = ecosSectionService.update(entityToDto(ecosSectionEntity));


        Mockito.verify(sectionRepository, times(1)).save(Mockito.any());
        Assert.assertNotNull(dto.getUuid());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
    }

    private EcosSectionDto entityToDto(EcosSectionEntity entity) {
        Set<RecordRef> typesRefs = entity.getTypes().stream()
            .map(e -> RecordRef.create("ecosmodel", "type", e.getUuid()))
            .collect(Collectors.toSet());
        return new EcosSectionDto(
            entity.getUuid(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            typesRefs);
    }
}
