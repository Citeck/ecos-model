package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.EcosSectionRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.model.service.impl.EcosTypeServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class EcosAssociationServiceImplTest {

    @Mock
    private EcosTypeRepository typeRepository;

    @Mock
    private EcosSectionRepository sectionRepository;

    private EcosTypeService ecosTypeService;

    @Before
    public void init() {
        ecosTypeService = new EcosTypeServiceImpl(typeRepository, sectionRepository);
    }

    @Test
    public void getAllReturnTypes() {

        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", null, null, null, null);
        EcosTypeEntity ecosTypeEntity2 = new EcosTypeEntity("b", 2L, "b",
            "b_desc", "b_tenant", ecosTypeEntity, null, null, null);

        ecosTypeEntity.setChilds(new HashSet<>(Arrays.asList(ecosTypeEntity2)));

        List<EcosTypeEntity> entities = Arrays.asList(
            ecosTypeEntity,
            ecosTypeEntity2
        );

        given(typeRepository.findAll()).willReturn(entities);


        Set<EcosTypeDto> dtos = ecosTypeService.getAll();


        Assert.assertEquals(2, dtos.size());
    }

    @Test(expected = NullPointerException.class)
    public void getAllWhenReturnNothing() {

        given(typeRepository.findAll()).willReturn(null);


        ecosTypeService.getAll();
    }

    @Test
    public void getAllSelectedReturnTypes() {

        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", null, null, null, null);
        EcosTypeEntity ecosTypeEntity2 = new EcosTypeEntity("b", 2L, "b",
            "b_desc", "b_tenant", ecosTypeEntity, null, null, null);

        ecosTypeEntity.setChilds(new HashSet<>(Collections.singleton(ecosTypeEntity2)));

        Set<EcosTypeEntity> entities = new HashSet<>();
        entities.add(ecosTypeEntity2);
        given(typeRepository.findAllByExtIds(Collections.singleton("b"))).willReturn(entities);


        Set<EcosTypeDto> dtos = ecosTypeService.getAll(Collections.singleton("b"));


        Assert.assertEquals(1, dtos.size());
    }

    @Test(expected = NullPointerException.class)
    public void getAllSelectedNothing() {

        given(typeRepository.findAllByExtIds(Collections.singleton("b"))).willReturn(null);


        ecosTypeService.getAll(Collections.singleton("b"));
    }

    @Test
    public void getByIdReturnTypeDto() {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", null, null, null, null);
        EcosTypeEntity ecosTypeEntity2 = new EcosTypeEntity("b", 2L, "b",
            "b_desc", "b_tenant", ecosTypeEntity, null, null, null);

        ecosTypeEntity.setChilds(new HashSet<>(Arrays.asList(ecosTypeEntity2)));

        given(typeRepository.findByExtId("b")).willReturn(Optional.of(ecosTypeEntity2));


        EcosTypeDto dto = ecosTypeService.getByExtId("b");


        Assert.assertEquals("b", dto.getExtId());
        Assert.assertEquals("b", dto.getName());
        Assert.assertEquals("b_desc", dto.getDescription());
        Assert.assertEquals("b_tenant", dto.getTenant());
        Assert.assertEquals("a", dto.getParent().getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getByIdReturnNothing() {

        given(typeRepository.findByExtId("b")).willReturn(Optional.empty());


        ecosTypeService.getByExtId("b");
    }

    @Test
    public void deleteSuccess() {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", null, null,null, null);

        given(typeRepository.findByExtId("a")).willReturn(Optional.of(ecosTypeEntity));


        ecosTypeService.delete("a");


        Mockito.verify(typeRepository, times(1)).deleteById(Mockito.anyLong());

    }

    @Test
    public void deleteNoDeletion() {

        given(typeRepository.findByExtId("a")).willReturn(Optional.empty());


        ecosTypeService.delete("a");


        Mockito.verify(typeRepository, times(0)).deleteById(Mockito.anyLong());
    }

    @Test
    public void updateSuccessNoParentNewEntity() {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "aname",
            "a_desc", "a_tenant", null, null, null, null);

        given(typeRepository.findByExtId("a")).willReturn(Optional.empty());


        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getExtId());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertNull(dto.getParent());
    }

    @Test
    public void updateSuccessWithParentNewEntity() {
        EcosTypeEntity parent = new EcosTypeEntity("b",2L,"b","b","b",
            null, null, null, null);
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "aname", "a_desc", "a_tenant", parent,
            null, null, null);

        given(typeRepository.findByExtId("a")).willReturn(Optional.empty());
        given(typeRepository.findByExtId("b")).willReturn(Optional.of(parent));


        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getExtId());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertEquals(parent.getExtId(), dto.getParent().getId());
    }

    @Test
    public void updateSuccessWithNoUUIDNewEntity() {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity(null, 1L, "aname",
            "a_desc", "a_tenant", null, null, null, null);


        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertNotNull(dto.getExtId());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertNull(dto.getParent());
    }

    @Test(expected = ParentNotFoundException.class)
    public void updateException() {
        EcosTypeEntity parent = new EcosTypeEntity("b", 2L, "a",
            "a_desc", "a_tenant", null , null, null, null);
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", parent, null, null, null);

        given(typeRepository.findByExtId("b")).willReturn(Optional.empty());


        ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(0)).save(Mockito.any());
    }

    private EcosTypeDto entityToDto(EcosTypeEntity entity) {
        RecordRef parent = null;
        if (entity.getParent() != null) {
            parent = RecordRef.create("type", entity.getParent().getExtId());
        }
        Set<RecordRef> sections = null;
        if (entity.getSections() != null) {
            sections = entity.getSections().stream()
                .map(s -> RecordRef.create("section", s.getExtId()))
                .collect(Collectors.toSet());
        }
        return new EcosTypeDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            parent,
            sections);
    }

}
