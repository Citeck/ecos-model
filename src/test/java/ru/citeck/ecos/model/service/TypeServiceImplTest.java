package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.impl.ActionConverter;
import ru.citeck.ecos.model.converter.impl.TypeConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.AssociationRepository;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.model.service.impl.TypeServiceImpl;

import java.util.*;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
public class TypeServiceImplTest {

    @Mock
    private TypeRepository typeRepository;

    @Mock
    private AssociationRepository associationRepository;

    @Mock
    private ActionConverter actionConverter;

    private TypeConverter converter;

    private TypeService typeService;

    private TypeEntity typeEntity;
    private TypeEntity typeEntity2;
    private TypeEntity parent;
    private AssociationEntity target;

    @BeforeEach
    public void init() {
        converter = new TypeConverter(typeRepository, actionConverter);
        typeService = new TypeServiceImpl(typeRepository, associationRepository, converter);

        parent = new TypeEntity();
        parent.setExtId("parentId");

        TypeEntity child = new TypeEntity();
        child.setExtId("childId");

        SectionEntity section = new SectionEntity();
        section.setExtId("sectionId");

        AssociationEntity source = new AssociationEntity();
        source.setExtId("sourceId");

        target = new AssociationEntity();
        target.setExtId("targetId");

        TypeEntity targetType = new TypeEntity();
        targetType.setExtId("targetTypeId");
        target.setTarget(targetType);

        typeEntity = new TypeEntity(
            "a", 1L, "a_name", "a_desc", "a_tenant", false, parent, Collections.singleton(child),
            Collections.singleton(section), Collections.singleton(source), Collections.singleton(target), null);
        typeEntity2 = new TypeEntity("b", 2L, "b",
            "b_desc", "b_tenant", false, typeEntity, null, null, null, null, null);

    }


    @Test
    public void getAllReturnTypes() {

        List<TypeEntity> entities = Collections.singletonList(typeEntity);

        given(typeRepository.findAll()).willReturn(entities);


        Set<TypeDto> dtos = typeService.getAll();


        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(dtos.iterator().next().getId(), typeEntity.getExtId());
    }

    @Test
    public void getAllWhenReturnNothing() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            given(typeRepository.findAll()).willReturn(null);


            typeService.getAll();
        });
    }

    @Test
    public void getAllSelectedReturnTypes() {

        typeEntity.setChilds(new HashSet<>(Collections.singleton(typeEntity2)));

        Set<TypeEntity> entities = new HashSet<>();
        entities.add(typeEntity);
        given(typeRepository.findAllByExtIds(Collections.singleton("a"))).willReturn(entities);


        Set<TypeDto> dtos = typeService.getAll(Collections.singleton("a"));


        Assert.assertEquals(1, dtos.size());
        TypeDto resultDto = dtos.iterator().next();
        Assert.assertEquals("a", resultDto.getId());
        Assert.assertEquals("a_name", resultDto.getName());
        Assert.assertEquals("a_desc", resultDto.getDescription());
        Assert.assertEquals("a_tenant", resultDto.getTenant());
        Assert.assertEquals("parentId", resultDto.getParent().getId());
        Assert.assertEquals("targetId", resultDto.getAssociations().iterator().next().getId());
    }

    @Test
    public void getAllSelectedNothing() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            given(typeRepository.findAllByExtIds(Collections.singleton("b"))).willReturn(null);


            typeService.getAll(Collections.singleton("b"));
        });
    }

    @Test
    public void getByIdReturnTypeDto() {

        typeEntity.setChilds(Collections.singleton(typeEntity));

        given(typeRepository.findByExtId("a")).willReturn(Optional.of(typeEntity));


        TypeDto dto = typeService.getByExtId("a");


        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("a_name", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertEquals("parentId", dto.getParent().getId());
        Assert.assertEquals("targetId", dto.getAssociations().iterator().next().getId());
    }

    @Test
    public void getByIdReturnNothing() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            given(typeRepository.findByExtId("b")).willReturn(Optional.empty());


            typeService.getByExtId("b");
        });
    }

    @Test
    public void deleteSuccess() {

        typeEntity.setChilds(null);

        given(typeRepository.findByExtId("a")).willReturn(Optional.of(typeEntity));


        typeService.delete("a");


        Mockito.verify(typeRepository, times(1)).deleteById(Mockito.anyLong());

    }

    @Test
    public void deleteException() {
        Assertions.assertThrows(ForgottenChildsException.class, () -> {
            given(typeRepository.findByExtId("a")).willReturn(Optional.of(typeEntity));


            typeService.delete("a");


            Mockito.verify(typeRepository, times(0)).deleteById(Mockito.anyLong());
        });
    }

    @Test
    public void deleteNoDeletion() {

        given(typeRepository.findByExtId("a")).willReturn(Optional.empty());


        typeService.delete("a");


        Mockito.verify(typeRepository, times(0)).deleteById(Mockito.anyLong());
    }

    @Test
    public void updateSuccessNoParentNewEntity() {

        typeEntity.setParent(null);

        given(typeRepository.findByExtId("a")).willReturn(Optional.empty());


        TypeDto dto = typeService.update(converter.targetToSource(typeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("a_name", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertNull(dto.getParent());
    }

    @Test
    public void updateSuccessWithParentNewEntity() {

        given(typeRepository.findByExtId("a")).willReturn(Optional.empty());
        given(typeRepository.findByExtId("parentId")).willReturn(Optional.of(parent));
        given(associationRepository.findByExtId("targetId")).willReturn(Optional.of(target));


        TypeDto dto = typeService.update(converter.targetToSource(typeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("a_name", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertEquals("parentId", dto.getParent().getId());
        AssociationDto assocDto = dto.getAssociations().iterator().next();
        Assert.assertEquals("targetId", assocDto.getTargetType().getId());
    }

    @Test
    public void updateSuccessWithNoExtIdNewEntity() {

        given(typeRepository.findByExtId("parentId")).willReturn(Optional.of(parent));
        given(associationRepository.findByExtId("targetId")).willReturn(Optional.of(target));

        TypeDto dto = typeService.update(converter.targetToSource(typeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertNotNull(dto.getId());
        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("a_name", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertEquals("parentId", dto.getParent().getId());
        Assert.assertEquals("targetId", dto.getAssociations().iterator().next().getId());
    }

    @Test
    public void updateException() {
        Assertions.assertThrows(ParentNotFoundException.class, () -> {
            given(typeRepository.findByExtId("b")).willReturn(Optional.empty());


            typeService.update(converter.targetToSource(typeEntity));


            Mockito.verify(typeRepository, times(0)).save(Mockito.any());
        });
    }

}
