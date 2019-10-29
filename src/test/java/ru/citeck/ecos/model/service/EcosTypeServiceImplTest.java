package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.domain.ActionEntity;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;
import ru.citeck.ecos.model.domain.EcosSectionEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.EcosAssociationRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.model.service.converter.ActionConverter;
import ru.citeck.ecos.model.service.impl.EcosTypeServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
public class EcosTypeServiceImplTest {

    @Mock
    private EcosTypeRepository typeRepository;

    @Mock
    private EcosAssociationRepository associationRepository;

    private EcosTypeService ecosTypeService;

    private EcosTypeEntity ecosTypeEntity;
    private EcosTypeEntity ecosTypeEntity2;
    private EcosTypeEntity parent;
    private EcosAssociationEntity target;

    @BeforeEach
    public void init() {
        /*ecosTypeService = new EcosTypeServiceImpl(typeRepository, associationRepository);

        parent = new EcosTypeEntity();
        parent.setExtId("parentId");

        EcosTypeEntity child = new EcosTypeEntity();
        child.setExtId("childId");

        EcosSectionEntity section = new EcosSectionEntity();
        section.setExtId("sectionId");

        EcosAssociationEntity source = new EcosAssociationEntity();
        source.setExtId("sourceId");

        target = new EcosAssociationEntity();
        target.setExtId("targetId");

        EcosTypeEntity targetType = new EcosTypeEntity();
        targetType.setExtId("targetTypeId");
        target.setTarget(targetType);

        ecosTypeEntity = new EcosTypeEntity(
            "a", 1L, "a_name", "a_desc", "a_tenant", parent, Collections.singleton(child),
            Collections.singleton(section), Collections.singleton(source), Collections.singleton(target));
        ecosTypeEntity2 = new EcosTypeEntity("b", 2L, "b",
            "b_desc", "b_tenant", ecosTypeEntity, null, null, null, null);*/

    }


    @Test
    public void getAllReturnTypes() {

        List<EcosTypeEntity> entities = Collections.singletonList(ecosTypeEntity);

        given(typeRepository.findAll()).willReturn(entities);


        Set<EcosTypeDto> dtos = ecosTypeService.getAll();


        Assert.assertEquals(1, dtos.size());
        Assert.assertEquals(dtos.iterator().next().getId(), ecosTypeEntity.getExtId());
    }

    @Test
    public void getAllWhenReturnNothing() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            given(typeRepository.findAll()).willReturn(null);


            ecosTypeService.getAll();
        });
    }

    @Test
    public void getAllSelectedReturnTypes() {

        ecosTypeEntity.setChilds(new HashSet<>(Collections.singleton(ecosTypeEntity2)));

        Set<EcosTypeEntity> entities = new HashSet<>();
        entities.add(ecosTypeEntity);
        given(typeRepository.findAllByExtIds(Collections.singleton("a"))).willReturn(entities);


        Set<EcosTypeDto> dtos = ecosTypeService.getAll(Collections.singleton("a"));


        Assert.assertEquals(1, dtos.size());
        EcosTypeDto resultDto = dtos.iterator().next();
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


            ecosTypeService.getAll(Collections.singleton("b"));
        });
    }

    @Test
    public void getByIdReturnTypeDto() {

        ecosTypeEntity.setChilds(Collections.singleton(ecosTypeEntity));

        given(typeRepository.findByExtId("a")).willReturn(Optional.of(ecosTypeEntity));


        EcosTypeDto dto = ecosTypeService.getByExtId("a");


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


            ecosTypeService.getByExtId("b");
        });
    }

    @Test
    public void deleteSuccess() {

        ecosTypeEntity.setChilds(null);

        given(typeRepository.findByExtId("a")).willReturn(Optional.of(ecosTypeEntity));


        ecosTypeService.delete("a");


        Mockito.verify(typeRepository, times(1)).deleteById(Mockito.anyLong());

    }

    @Test
    public void deleteException() {
        Assertions.assertThrows(ForgottenChildsException.class, () -> {
            given(typeRepository.findByExtId("a")).willReturn(Optional.of(ecosTypeEntity));


            ecosTypeService.delete("a");


            Mockito.verify(typeRepository, times(0)).deleteById(Mockito.anyLong());
        });
    }

    @Test
    public void deleteNoDeletion() {

        given(typeRepository.findByExtId("a")).willReturn(Optional.empty());


        ecosTypeService.delete("a");


        Mockito.verify(typeRepository, times(0)).deleteById(Mockito.anyLong());
    }

    @Test
    public void updateSuccessNoParentNewEntity() {

        ecosTypeEntity.setParent(null);

        given(typeRepository.findByExtId("a")).willReturn(Optional.empty());


        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


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


        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


        Mockito.verify(typeRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("a_name", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
        Assert.assertEquals("parentId", dto.getParent().getId());
        RecordRef associationRef = dto.getAssociations().iterator().next();
        Assert.assertEquals("targetId", associationRef.getId());
    }

    @Test
    public void updateSuccessWithNoExtIdNewEntity() {

        given(typeRepository.findByExtId("parentId")).willReturn(Optional.of(parent));
        given(associationRepository.findByExtId("targetId")).willReturn(Optional.of(target));

        EcosTypeDto dto = ecosTypeService.update(entityToDto(ecosTypeEntity));


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


            ecosTypeService.update(entityToDto(ecosTypeEntity));


            Mockito.verify(typeRepository, times(0)).save(Mockito.any());
        });
    }

    private EcosTypeDto entityToDto(EcosTypeEntity entity) {
        RecordRef parent = null;
        if (entity.getParent() != null) {
            parent = RecordRef.create("type", entity.getParent().getExtId());
        }
        Set<RecordRef> associationsRefs = null;
        if (entity.getAssocsToOther() != null) {
            associationsRefs = entity.getAssocsToOther().stream()
                .map(assoc -> RecordRef.create("association", assoc.getExtId()))
                .collect(Collectors.toSet());
        }

        List<ActionDto> actions = entity.getActions()
            .stream()
            .map(ActionConverter::toDto)
            .collect(Collectors.toList());

        return new EcosTypeDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            parent,
            associationsRefs,
            actions,
            entity.isInheritActions());
    }

}
