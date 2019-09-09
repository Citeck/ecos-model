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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class EcosTypeServiceImplTest {

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
            "a_desc", "a_tenant", null, null, null);
        EcosTypeEntity ecosTypeEntity2 = new EcosTypeEntity("b", 2L, "b",
            "b_desc", "b_tenant", ecosTypeEntity, null, null);

        ecosTypeEntity.setChilds(new HashSet<>(Arrays.asList(ecosTypeEntity2)));

        List<EcosTypeEntity> entities = Arrays.asList(
            ecosTypeEntity,
            ecosTypeEntity2
        );

        given(typeRepository.findAll()).willReturn(entities);


        List<EcosTypeDto> dtos = ecosTypeService.getAll();


        Assert.assertEquals(2, dtos.size());
        Assert.assertEquals("a", dtos.get(0).getUuid());
        Assert.assertEquals("b", dtos.get(1).getUuid());
    }

    @Test(expected = NullPointerException.class)
    public void getAllWhenReturnNothing() {

        given(typeRepository.findAll()).willReturn(null);


        List<EcosTypeDto> dtos = ecosTypeService.getAll();
    }

    @Test
    public void getAllSelectedReturnTypes() {

        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", null, null, null);
        EcosTypeEntity ecosTypeEntity2 = new EcosTypeEntity("b", 2L, "b",
            "b_desc", "b_tenant", ecosTypeEntity, null, null);

        ecosTypeEntity.setChilds(new HashSet<>(Arrays.asList(ecosTypeEntity2)));

        Set<EcosTypeEntity> entities = new HashSet<>();
        entities.add(ecosTypeEntity2);
        given(typeRepository.findAllByUuid(Arrays.asList("b"))).willReturn(entities);


        List<EcosTypeDto> dtos = ecosTypeService.getAll(Arrays.asList("b"));


        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals("b", dtos.get(0).getUuid());
    }

    @Test(expected = NullPointerException.class)
    public void getAllSelectedNothing() {

        given(typeRepository.findAllByUuid(Arrays.asList("b"))).willReturn(null);


        List<EcosTypeDto> dtos = ecosTypeService.getAll(Arrays.asList("b"));
    }

    @Test
    public void getByIdReturnTypeDto() {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", null, null, null);
        EcosTypeEntity ecosTypeEntity2 = new EcosTypeEntity("b", 2L, "b",
            "b_desc", "b_tenant", ecosTypeEntity, null, null);

        ecosTypeEntity.setChilds(new HashSet<>(Arrays.asList(ecosTypeEntity2)));

        given(typeRepository.findByUuid("b")).willReturn(Optional.of(ecosTypeEntity2));


        Optional<EcosTypeDto> optionalDto = ecosTypeService.getByUuid("b");


        Assert.assertTrue(optionalDto.isPresent());
        Assert.assertEquals("b", optionalDto.get().getUuid());
        Assert.assertEquals("b", optionalDto.get().getName());
        Assert.assertEquals("b_desc", optionalDto.get().getDescription());
        Assert.assertEquals("b_tenant", optionalDto.get().getTenant());
        Assert.assertEquals("a", optionalDto.get().getParent().getId());
    }

    @Test
    public void getByIdReturnNothing() {

        given(typeRepository.findByUuid("b")).willReturn(Optional.empty());


        Optional<EcosTypeDto> optionalDto = ecosTypeService.getByUuid("b");


        Assert.assertFalse(optionalDto.isPresent());
    }

    @Test
    public void deleteSuccess() {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", null, null,null);

        given(typeRepository.findByUuid("a")).willReturn(Optional.of(ecosTypeEntity));


        ecosTypeService.delete("a");


        Mockito.verify(typeRepository, times(1)).deleteById(Mockito.anyLong());

    }

    @Test
    public void deleteNoDeletion() {

        given(typeRepository.findByUuid("a")).willReturn(Optional.empty());


        ecosTypeService.delete("a");


        Mockito.verify(typeRepository, times(0)).deleteById(Mockito.anyLong());
    }

    @Test
    public void updateSuccessNoParentNewEntity() {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "aname",
            "a_desc", "a_tenant", null, null, null);

        given(typeRepository.findByUuid("a")).willReturn(Optional.empty());


        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getUuid());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertNull(dto.getParent());
    }

    @Test
    public void updateSuccessWithParentNewEntity() {
        EcosTypeEntity parent = new EcosTypeEntity("b",2L,"b","b","b",null,null, null);
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "aname", "a_desc", "a_tenant", parent, null, null);

        given(typeRepository.findByUuid("a")).willReturn(Optional.empty());
        given(typeRepository.findByUuid("b")).willReturn(Optional.of(parent));


        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getUuid());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertEquals(entityToDto(parent), dto.getParent());
    }

    @Test
    public void updateSuccessWithNoUUIDNewEntity() {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity(null, 1L, "aname", "a_desc", "a_tenant", null, null, null);

        given(typeRepository.findByUuid(Mockito.anyString())).willReturn(Optional.empty());


        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertNotNull(dto.getUuid());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertNull(dto.getParent());
    }

    @Test(expected = ParentNotFoundException.class)
    public void updateException() {
        EcosTypeEntity parent = new EcosTypeEntity("b", 2L, "a",
            "a_desc", "a_tenant", null , null, null);
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity("a", 1L, "a",
            "a_desc", "a_tenant", parent, null, null);

        given(typeRepository.findByUuid("a")).willReturn(Optional.empty());
        given(typeRepository.findByUuid("b")).willReturn(Optional.empty());


        ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(0)).save(Mockito.any());
    }

    private EcosTypeDto entityToDto(EcosTypeEntity entity) {
        return new EcosTypeDto(
            entity.getUuid(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            RecordRef.create("ecosmodel", "type", entity.getParent().getUuid()),
            RecordRef.create("ecosmodel", "section", entity.getSection().getUuid()));
    }

}
