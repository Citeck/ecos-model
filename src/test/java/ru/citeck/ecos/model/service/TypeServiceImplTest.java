package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.eapps.listener.AssociationDto;
import ru.citeck.ecos.model.converter.dto.impl.TypeConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
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
    private RecordRef actionRef;
    private AssociationDto associationDto;

    @BeforeEach
    void init() {

        typeService = new TypeServiceImpl(
            typeRepository,
            associationService,
            typeConverter
        );

        typeExtId = "type";

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
        typeEntity.setActions("[\"uiserv/action@action\"]");
        typeEntity.setAssocsToOthers(Collections.singleton(associationEntity));
        typeEntity.setParent(parent);
        typeEntity.setChildren(Collections.singleton(child));
        typeEntity.setSections(Collections.singleton(sectionEntity));

        actionRef = RecordRef.create("uiserv", "action", "action");

        associationDto = new AssociationDto();
        associationDto.setId("association");

        typeDto = new TypeDto();
        typeDto.setId(typeExtId);
        typeDto.setName(new MLText("name"));
        typeDto.setDescription(new MLText("desc"));
        typeDto.setInheritActions(false);
        typeDto.setActions(Collections.singletonList(actionRef));
        typeDto.setAssociations(Collections.singletonList(associationDto));
        typeDto.setParent(RecordRef.create("type", "parent"));
    }

    @Test
    void testGetAll() {

        //  arrange
        when(typeRepository.findAll()).thenReturn(Collections.singletonList(typeEntity));
        when(typeConverter.entityToDto(typeEntity)).thenReturn(typeDto);

        //  act
        Set<TypeDto> resultTypeDtos = typeService.getAll();

        //  assert
        Assert.assertEquals(resultTypeDtos.size(), 1);
        TypeDto resultTypeDto = resultTypeDtos.iterator().next();
        Assert.assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultTypeDto.getName(), Json.getMapper().read(typeEntity.getName(), MLText.class));
        Assert.assertEquals(resultTypeDto.getDescription(), Json.getMapper().read(typeEntity.getDescription(), MLText.class));
        Assert.assertEquals(resultTypeDto.getAssociations(), Collections.singletonList(associationDto));
        Assert.assertEquals(resultTypeDto.getActions(), Collections.singletonList(actionRef));
        Assert.assertEquals(resultTypeDto.getParent(), RecordRef.create("type", "parent"));
    }

    @Test
    void testGetAllWithArgs() {

        //  arrange
        when(typeRepository.findAllByExtIds(Collections.singleton(typeExtId)))
            .thenReturn(Collections.singleton(typeEntity));
        when(typeConverter.entityToDto(typeEntity)).thenReturn(typeDto);

        //  act
        Set<TypeDto> resultTypeDtos = typeService.getAll(Collections.singleton(typeExtId));

        //  assert
        Assert.assertEquals(resultTypeDtos.size(), 1);
        TypeDto resultTypeDto = resultTypeDtos.iterator().next();
        Assert.assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultTypeDto.getName(), Json.getMapper().read(typeEntity.getName(), MLText.class));
        Assert.assertEquals(resultTypeDto.getDescription(), Json.getMapper().read(typeEntity.getDescription(), MLText.class));
        Assert.assertEquals(resultTypeDto.getAssociations(), Collections.singletonList(associationDto));
        Assert.assertEquals(resultTypeDto.getActions(), Collections.singletonList(actionRef));
        Assert.assertEquals(resultTypeDto.getParent(), RecordRef.create("type", "parent"));
    }

    @Test
    void testGetByExtId() {

        //  arrange
        when(typeRepository.findByExtId(typeExtId)).thenReturn(Optional.of(typeEntity));
        when(typeConverter.entityToDto(typeEntity)).thenReturn(typeDto);

        //  act
        TypeDto resultTypeDto = typeService.getByExtId(typeExtId);

        //  assert
        Assert.assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultTypeDto.getName(), Json.getMapper().read(typeEntity.getName(), MLText.class));
        Assert.assertEquals(resultTypeDto.getDescription(), Json.getMapper().read(typeEntity.getDescription(), MLText.class));
        Assert.assertEquals(resultTypeDto.getAssociations(), Collections.singletonList(associationDto));
        Assert.assertEquals(resultTypeDto.getActions(), Collections.singletonList(actionRef));
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
            Mockito.verify(typeConverter, Mockito.times(0)).entityToDto(Mockito.any());
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
        when(typeConverter.dtoToEntity(typeDto)).thenReturn(typeEntity);
        when(typeRepository.save(typeEntity)).thenReturn(typeEntity);

        //  act
        typeService.save(typeDto);

        //  assert
        Mockito.verify(typeRepository, Mockito.times(1)).save(typeEntity);
        Mockito.verify(typeConverter, Mockito.times(1)).entityToDto(typeEntity);
    }

    @Test
    void testUpdateWithoutAssocsToOther() {

        //  arrange
//        typeEntity.setAssocsToOthers(null);
//        when(typeConverter.dtoToEntity(typeDto)).thenReturn(typeEntity);
//
//        //  act
//        typeService.save(typeDto);
//
//        //  assert
//        Mockito.verify(typeRepository, Mockito.times(1)).save(typeEntity);
//        Mockito.verify(typeConverter, Mockito.times(1)).entityToDto(typeEntity);
//        Mockito.verify(associationService, Mockito.times(0)).saveAll(Mockito.anySet());
    }
}
