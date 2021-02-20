package ru.citeck.ecos.model.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.association.service.AssociationService;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.type.converter.TypeConverter;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.model.type.service.impl.TypeServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    private TypeWithMetaDto typeDto;

    private String typeExtId;
    private RecordRef actionRef;

    private AssociationEntity parentAssocEntity;
    private AssociationEntity parentAssocEntity2;
    private AssociationEntity childAssocEntity;

    AssociationDto childAssocDto;
    AssociationDto parentAssocDto;
    AssociationDto parentAssocDto2;

    @BeforeEach
    void init() {

        CommandsService commandsService = new CommandsServiceFactory().getCommandsService();

        typeService = new TypeServiceImpl(
            typeRepository,
            typeConverter
        );

        typeExtId = "test-type";

        parentAssocEntity = new AssociationEntity();
        parentAssocEntity.setExtId("association");
        parentAssocEntity.setName("name0");
        parentAssocDto = new AssociationDto();
        parentAssocDto.setId("association");
        parentAssocDto.setName(new MLText("name0"));

        parentAssocEntity2 = new AssociationEntity();
        parentAssocEntity2.setExtId("association2");
        parentAssocEntity2.setName("name2");
        parentAssocDto2 = new AssociationDto();
        parentAssocDto2.setId("association2");
        parentAssocDto2.setName(new MLText("name2"));

        childAssocEntity = new AssociationEntity();
        childAssocEntity.setExtId("association");
        childAssocEntity.setName("name1");
        childAssocDto = new AssociationDto();
        childAssocDto.setId("association");
        childAssocDto.setName(new MLText("name1"));

        TypeEntity parent = new TypeEntity();
        parent.setExtId("parent");
        parent.setAssociations(new HashSet<>(Arrays.asList(parentAssocEntity, parentAssocEntity2)));

        TypeWithMetaDto parentDto = new TypeWithMetaDto();
        parentDto.setId("parent");
        parentDto.setAssociations(Arrays.asList(parentAssocDto, parentAssocDto2));

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
        typeEntity.setAssociations(Collections.singleton(childAssocEntity));
        typeEntity.setParent(parent);
        typeEntity.setChildren(Collections.singleton(child));
        typeEntity.setSections(Collections.singleton(sectionEntity));
        typeEntity.setAliases(Collections.singleton("alias"));
        typeEntity.setConfigForm("eform@config-form");
        typeEntity.setConfig("{\n" +
            "  \"color\": \"red\",\n" +
            "  \"icon\": \"urgent\"\n" +
            "}");

        actionRef = RecordRef.create("uiserv", "action", "action");

        typeDto = new TypeWithMetaDto();
        typeDto.setId(typeExtId);
        typeDto.setName(new MLText("name"));
        typeDto.setDescription(new MLText("desc"));
        typeDto.setInheritActions(false);
        typeDto.setActions(Collections.singletonList(actionRef));
        typeDto.setAssociations(Collections.singletonList(childAssocDto));
        typeDto.setParent(RecordRef.create("emodel", "type", "parent"));
        typeDto.setAliases(Collections.singletonList("alias"));
        typeDto.setConfig(Json.getMapper().read("{\n" +
            "  \"color\": \"red\",\n" +
            "  \"icon\": \"urgent\"\n" +
            "}", ObjectData.class));

        when(typeRepository.findByExtId(parent.getExtId())).thenReturn(Optional.of(parent));
        when(typeRepository.findByExtId(typeEntity.getExtId())).thenReturn(Optional.of(typeEntity));

        when(typeConverter.entityToDto(typeEntity)).thenReturn(typeDto);
        when(typeConverter.entityToDto(parent)).thenReturn(parentDto);
    }

    @Test
    void testAllAssocs() {

        List<AssociationDto> assocs = typeService.getFullAssocs(typeEntity.getExtId());
        assertEquals(2, assocs.size());

        for (AssociationDto dto : assocs) {
            if (dto.getId().equals("association")) {
                assertEquals("name1", dto.getName().getClosestValue(Locale.ENGLISH));
            } else if (dto.getId().equals("association2")) {
                assertEquals("name2", dto.getName().getClosestValue(Locale.ENGLISH));
            } else {
                throw new IllegalStateException("Unknown association: " + dto);
            }
        }
    }

    @Test
    void testGetAll() {

        //  arrange
        when(typeRepository.findAll()).thenReturn(Collections.singletonList(typeEntity));
        when(typeConverter.entityToDto(typeEntity)).thenReturn(typeDto);

        //  act
        Set<TypeWithMetaDto> resultTypeDtos = typeService.getAll();

        //  assert
        assertEquals(resultTypeDtos.size(), 1);
        TypeDto resultTypeDto = resultTypeDtos.iterator().next();
        assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        assertEquals(resultTypeDto.getName(), Json.getMapper().read(typeEntity.getName(), MLText.class));
        assertEquals(resultTypeDto.getDescription(), Json.getMapper().read(typeEntity.getDescription(), MLText.class));
        assertEquals(resultTypeDto.getAssociations(), Collections.singletonList(childAssocDto));
        assertEquals(resultTypeDto.getActions(), Collections.singletonList(actionRef));
        assertEquals(resultTypeDto.getParentRef(), RecordRef.create("emodel", "type", "parent"));
        assertEquals(resultTypeDto.getAliases(), Collections.singletonList("alias"));
    }

    @Test
    void testGetAllWithArgs() {

        //  arrange
        when(typeRepository.findAllByExtIds(Collections.singleton(typeExtId)))
            .thenReturn(Collections.singleton(typeEntity));
        when(typeConverter.entityToDto(typeEntity)).thenReturn(typeDto);

        //  act
        Set<TypeWithMetaDto> resultTypeDtos = typeService.getAll(Collections.singleton(typeExtId));

        //  assert
        assertEquals(resultTypeDtos.size(), 1);
        TypeDto resultTypeDto = resultTypeDtos.iterator().next();
        assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        assertEquals(resultTypeDto.getName(), Json.getMapper().read(typeEntity.getName(), MLText.class));
        assertEquals(resultTypeDto.getDescription(), Json.getMapper().read(typeEntity.getDescription(), MLText.class));
        assertEquals(resultTypeDto.getAssociations(), Collections.singletonList(childAssocDto));
        assertEquals(resultTypeDto.getActions(), Collections.singletonList(actionRef));
        assertEquals(resultTypeDto.getParentRef(), RecordRef.create("emodel", "type", "parent"));
        assertEquals(resultTypeDto.getAliases(), Collections.singletonList("alias"));
    }

    @Test
    void testGetByExtId() {

        //  arrange
        when(typeRepository.findByExtId(typeExtId)).thenReturn(Optional.of(typeEntity));
        when(typeConverter.entityToDto(typeEntity)).thenReturn(typeDto);

        //  act
        TypeDto resultTypeDto = typeService.getById(typeExtId);

        //  assert
        assertEquals(resultTypeDto.getId(), typeEntity.getExtId());
        assertEquals(resultTypeDto.getName(), Json.getMapper().read(typeEntity.getName(), MLText.class));
        assertEquals(resultTypeDto.getDescription(), Json.getMapper().read(typeEntity.getDescription(), MLText.class));
        assertEquals(resultTypeDto.getAssociations(), Collections.singletonList(childAssocDto));
        assertEquals(resultTypeDto.getActions(), Collections.singletonList(actionRef));
        assertEquals(resultTypeDto.getParentRef(), RecordRef.create("emodel", "type", "parent"));
        assertEquals(resultTypeDto.getAliases(), Collections.singletonList("alias"));
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
        when(typeRepository.findByExtId(typeExtId)).thenReturn(Optional.of(typeEntity));

        Exception exception = assertThrows(RuntimeException.class, () -> typeService.delete(typeExtId));

        Mockito.verify(typeRepository, Mockito.times(0)).deleteById(1L);
        assertEquals(exception.getMessage(), "Children types could be forgotten");
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
        when(typeConverter.entityToDto(typeEntity)).thenReturn(typeDto);
        when(typeRepository.save(typeEntity)).thenReturn(typeEntity);

        //  act
        typeService.save(typeDto);

        //  assert
        Mockito.verify(typeRepository, Mockito.times(1)).save(typeEntity);
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

    @Test
    void save_typeWithoutOwnerAndAliasTypes() {

        String typeId = "typeId";
        String alias = "alias";

        TypeWithMetaDto dto = new TypeWithMetaDto();
        dto.setId(typeId);
        dto.setAliases(Collections.singletonList(alias));

        TypeEntity entity = new TypeEntity();
        when(typeConverter.dtoToEntity(dto)).thenReturn(entity);

        when(typeRepository.findByContainsInAliases(typeId)).thenReturn(Optional.empty());
        when(typeRepository.findByExtId(alias)).thenReturn(Optional.empty());

        TypeEntity saved = new TypeEntity();
        when(typeRepository.save(entity)).thenReturn(saved);

        TypeWithMetaDto savedDto = new TypeWithMetaDto();
        savedDto.setId("test");
        when(typeConverter.entityToDto(saved)).thenReturn(savedDto);

        assertEquals(savedDto, typeService.save(dto));

        Mockito.verify(typeRepository, Mockito.times(1)).save(entity);
    }

    @Test
    void save_typeWithIdContainingInAliasesOfOtherType() {

        String typeId = "typeId";
        String ownerId = "ownerId";
        String alias = "ownerId";

        TypeWithMetaDto ownerDto = new TypeWithMetaDto();
        ownerDto.setId(typeId);
        ownerDto.setAliases(Collections.singletonList(alias));

        TypeEntity ownerEntity = new TypeEntity();
        ownerEntity.setExtId(ownerId);
        when(typeRepository.findByContainsInAliases(alias)).thenReturn(Optional.of(ownerEntity));

        when(typeConverter.dtoToEntity(ownerDto)).thenReturn(ownerEntity);
        when(typeConverter.entityToDto(ownerEntity)).thenReturn(ownerDto);

        TypeDto newType = new TypeDto();
        newType.setId(alias);

        assertEquals(ownerDto, typeService.save(newType));

        Mockito.verify(typeRepository, Mockito.times(0)).save(any());
        Mockito.verify(typeRepository, Mockito.times(0)).findByExtId(alias);
    }

    //TODO: rewrite test. Not relevant.
    /*@Test
    void save_typeWithAliasesHavingPersistedTypes() {

        String typeId = "typeId";

        String alias1 = "alias1";
        String alias2 = "alias2";
        String alias3 = "alias3";

        TypeDto newDto = new TypeDto();
        newDto.setId(typeId);
        newDto.setAliases(Arrays.asList(alias1, alias2));

        TypeEntity newEntity = new TypeEntity();
        newEntity.setExtId(typeId);
        newEntity.setAliases(new HashSet<>(newDto.getAliases()));

        when(typeConverter.dtoToEntity(newDto)).thenReturn(newEntity);
        when(typeConverter.entityToDto(newEntity)).thenReturn(newDto);
        when(typeRepository.save(newEntity)).thenReturn(newEntity);

        TypeEntity aliasedEntity1 = new TypeEntity();
        TypeEntity aliasedEntity2 = new TypeEntity();
        TypeEntity aliasedEntity2Child = new TypeEntity();
        Set<TypeEntity> aliasedEntity2Children = new HashSet<>();
        aliasedEntity2Children.add(aliasedEntity2Child);
        aliasedEntity2.setChildren(aliasedEntity2Children);

        when(typeRepository.existsByExtId(alias1)).thenReturn(true);
        when(typeRepository.existsByExtId(alias2)).thenReturn(true);
        when(typeRepository.existsByExtId(alias3)).thenReturn(false);

        when(typeRepository.findByExtId(alias1)).thenReturn(Optional.of(aliasedEntity1));
        when(typeRepository.findByExtId(alias2)).thenReturn(Optional.of(aliasedEntity2));

        TypeEntity updatedFirstEntity = new TypeEntity();
        when(typeConverter.dtoToEntity(argThat((dto) -> alias1.equals(dto.getId())))).thenReturn(updatedFirstEntity);

        TypeEntity savedEntity = new TypeEntity();
        when(typeRepository.save(updatedFirstEntity)).thenReturn(savedEntity);

        TypeDto savedDto = new TypeDto();
        when(typeConverter.entityToDto(savedEntity)).thenReturn(savedDto);

        assertEquals(savedDto, typeService.save(newDto));
        //assertEquals(Collections.singleton(aliasedEntity2Child), updatedFirstEntity.getChildren());
    }*/
}
