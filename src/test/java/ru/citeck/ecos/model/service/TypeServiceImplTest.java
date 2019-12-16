package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.model.converter.impl.TypeConverter;
import ru.citeck.ecos.model.domain.TypeActionEntity;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.model.service.impl.TypeServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TypeServiceImplTest {

    @Mock
    private TypeRepository typeRepository;

    @Mock
    private AssociationService associationService;

    @Mock
    private TypeConverter typeConverter;

    private TypeServiceImpl typeService;

    private TypeEntity typeEntity;

    private TypeDto typeDto;

    private String typeExtId;
    private ModuleRef actionRef;
    private AssociationDto associationDto;

    @BeforeEach
    void init() {

        typeService = new TypeServiceImpl(typeRepository, associationService, typeConverter);

        typeExtId = "type";

        TypeActionEntity actionEntity = new TypeActionEntity();
        actionEntity.setActionId("action");

        AssociationEntity associationEntity = new AssociationEntity();
        associationEntity.setExtId("association");

        TypeEntity parent = new TypeEntity();
        parent.setExtId("parent");

        TypeEntity child = new TypeEntity();
        child.setExtId("child");

        SectionEntity sectionEntity = new SectionEntity();
        sectionEntity.setExtId("section");

        typeEntity = new TypeEntity();
        typeEntity.setExtId(typeExtId);
        typeEntity.setId(1L);
        typeEntity.setName("name");
        typeEntity.setTenant("tenant");
        typeEntity.setDescription("desc");
        typeEntity.setInheritActions(false);
        typeEntity.addAction(actionEntity);
        typeEntity.setAssocsToOther(Collections.singleton(associationEntity));
        typeEntity.setParent(parent);
        typeEntity.setChildren(Collections.singleton(child));
        typeEntity.setSections(Collections.singleton(sectionEntity));

        actionRef = ModuleRef.create("ui/action", "action");

        associationDto = new AssociationDto();
        associationDto.setId("association");

        typeDto = new TypeDto();
        typeDto.setId(typeExtId);
        typeDto.setName("name");
        typeDto.setTenant("tenant");
        typeDto.setDescription("desc");
        typeDto.setInheritActions(false);
        typeDto.setActions(Collections.singleton(actionRef));
        typeDto.setAssociations(Collections.singleton(associationDto));
        typeDto.setParent(RecordRef.create("type", "parent"));
    }

    @Test
    void testGetAll() {

        //  arrange
        when(typeRepository.findAll()).thenReturn(Collections.singletonList(typeEntity));
        when(typeConverter.targetToSource(typeEntity)).thenReturn(typeDto);

        //  act
        Set<TypeDto> resultTypeDtos = typeService.getAll();

        //  assert
        Assert.assertEquals(resultTypeDtos.size(), 1);
        TypeDto resultTypeDto = resultTypeDtos.iterator().next();
        Assert.assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultTypeDto.getName(), typeEntity.getName());
        Assert.assertEquals(resultTypeDto.getDescription(), typeEntity.getDescription());
        Assert.assertEquals(resultTypeDto.getTenant(), typeEntity.getTenant());
        Assert.assertEquals(resultTypeDto.getAssociations(), Collections.singleton(associationDto));
        Assert.assertEquals(resultTypeDto.getActions(), Collections.singleton(actionRef));
        Assert.assertEquals(resultTypeDto.getParent(), RecordRef.create("type", "parent"));
    }

    @Test
    void testGetAllWithArgs() {

        //  arrange
        when(typeRepository.findAllByExtIds(Collections.singleton(typeExtId)))
            .thenReturn(Collections.singleton(typeEntity));
        when(typeConverter.targetToSource(typeEntity)).thenReturn(typeDto);

        //  act
        Set<TypeDto> resultTypeDtos = typeService.getAll(Collections.singleton(typeExtId));

        //  assert
        Assert.assertEquals(resultTypeDtos.size(), 1);
        TypeDto resultTypeDto = resultTypeDtos.iterator().next();
        Assert.assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultTypeDto.getName(), typeEntity.getName());
        Assert.assertEquals(resultTypeDto.getDescription(), typeEntity.getDescription());
        Assert.assertEquals(resultTypeDto.getTenant(), typeEntity.getTenant());
        Assert.assertEquals(resultTypeDto.getAssociations(), Collections.singleton(associationDto));
        Assert.assertEquals(resultTypeDto.getActions(), Collections.singleton(actionRef));
        Assert.assertEquals(resultTypeDto.getParent(), RecordRef.create("type", "parent"));
    }

    @Test
    void testGetByExtId() {

        //  arrange
        when(typeRepository.findByExtId(typeExtId)).thenReturn(Optional.of(typeEntity));
        when(typeConverter.targetToSource(typeEntity)).thenReturn(typeDto);

        //  act
        TypeDto resultTypeDto = typeService.getByExtId(typeExtId);

        //  assert
        Assert.assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultTypeDto.getName(), typeEntity.getName());
        Assert.assertEquals(resultTypeDto.getDescription(), typeEntity.getDescription());
        Assert.assertEquals(resultTypeDto.getTenant(), typeEntity.getTenant());
        Assert.assertEquals(resultTypeDto.getAssociations(), Collections.singleton(associationDto));
        Assert.assertEquals(resultTypeDto.getActions(), Collections.singleton(actionRef));
        Assert.assertEquals(resultTypeDto.getParent(), RecordRef.create("type", "parent"));
    }

    @Test
    void testGetByExtIdThrowsException() {

        //  arrange
        when(typeRepository.findByExtId(typeExtId)).thenReturn(Optional.empty());

        //  act
        try {
            typeService.getByExtId(typeExtId);
        } catch (IllegalArgumentException iae) {
            //  assert
            Mockito.verify(typeConverter, Mockito.times(0)).targetToSource(Mockito.any());
            Assert.assertEquals(iae.getMessage(), "Type doesnt exists: " + typeExtId);
        }
    }

    @Test
    void testDelete() {

        //  arrange
        typeEntity.setChildren(Collections.emptySet());
        when(typeRepository.findByExtId(typeExtId)).thenReturn(Optional.of(typeEntity));

        //  act
        typeService.delete(typeExtId);

        //  assert
        Mockito.verify(typeRepository, Mockito.times(1)).deleteById(1L);
    }

    @Test
    void testDeleteThrowsException() {

        //  arrange
        when(typeRepository.findByExtId(typeExtId)).thenReturn(Optional.of(typeEntity));

        //  act
        try {
            typeService.delete(typeExtId);
        } catch (ForgottenChildsException fce) {
            //  assert
            Mockito.verify(typeRepository, Mockito.times(0)).deleteById(1L);
            Assert.assertEquals(fce.getMessage(), "Children types could be forgotten");
        }
    }

    @Test
    void testDeleteTypeNotFound() {

        //  arrange
        when(typeRepository.findByExtId(typeExtId)).thenReturn(Optional.empty());

        //  act
        typeService.delete(typeExtId);

        //  assert
        Mockito.verify(typeRepository, Mockito.times(0)).deleteById(1L);
    }

    @Test
    void testUpdate() {

        //  arrange
        when(typeConverter.sourceToTarget(typeDto)).thenReturn(typeEntity);

        //  act
        typeService.update(typeDto);

        //  assert
        Mockito.verify(typeRepository, Mockito.times(1)).save(typeEntity);
        Mockito.verify(typeConverter, Mockito.times(1)).targetToSource(typeEntity);
    }

    @Test
    void testUpdateWithoutAssocsToOther() {

        //  arrange
        typeEntity.setAssocsToOther(null);
        when(typeConverter.sourceToTarget(typeDto)).thenReturn(typeEntity);

        //  act
        typeService.update(typeDto);

        //  assert
        Mockito.verify(typeRepository, Mockito.times(1)).save(typeEntity);
        Mockito.verify(typeConverter, Mockito.times(1)).targetToSource(typeEntity);
        Mockito.verify(associationService, Mockito.times(0)).saveAll(Mockito.anySet());
    }
}
