package ru.citeck.ecos.model.converter;

import org.apache.logging.log4j.util.Strings;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.converter.impl.TypeConverter;
import ru.citeck.ecos.model.domain.ActionEntity;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TypeConverterTest {

    @MockBean
    private TypeRepository typeRepository;

    @MockBean
    private DtoConverter<ActionDto, ActionEntity> actionConverter;

    @MockBean
    private DtoConverter<AssociationDto, AssociationEntity> associationConverter;

    private TypeConverter typeConverter;

    private TypeEntity typeEntity;
    private TypeDto typeDto;

    private TypeEntity child;
    private TypeEntity parent;

    private ActionEntity actionEntity;
    private ActionDto actionDto;

    private SectionEntity sectionEntity;

    private AssociationEntity associationEntity;
    private AssociationDto associationDto;

    @BeforeEach
    void setUp() {
        typeConverter = new TypeConverter(typeRepository, actionConverter, associationConverter);

        child = new TypeEntity();
        child.setExtId("child");

        parent = new TypeEntity();
        parent.setExtId("parent");

        actionEntity = new ActionEntity();
        actionEntity.setExtId("action");

        actionDto = new ActionDto();
        actionDto.setId("action");

        sectionEntity = new SectionEntity();
        sectionEntity.setExtId("section");

        typeEntity = new TypeEntity();
        typeEntity.setExtId("type");
        typeEntity.setId(123L);
        typeEntity.setName("name");
        typeEntity.setTenant("tenant");
        typeEntity.setDescription("desc");
        typeEntity.setChilds(Collections.singleton(child));
        typeEntity.setParent(parent);
        typeEntity.addAction(actionEntity);
        typeEntity.setSections(Collections.singleton(sectionEntity));

        associationEntity = new AssociationEntity();
        associationEntity.setExtId("association");
        associationEntity.setSource(typeEntity);
        associationEntity.setTarget(parent);
        associationEntity.setSource(typeEntity);

        typeEntity.setAssocsToOther(Collections.singleton(associationEntity));


        associationDto = new AssociationDto();
        associationDto.setId("association");
        associationDto.setSourceType(RecordRef.create("type", "type"));
        associationDto.setTargetType(RecordRef.create("type", "parent"));

        typeDto = new TypeDto();
        typeDto.setId("type");
        typeDto.setName("name");
        typeDto.setTenant("tenant");
        typeDto.setDescription("desc");
        typeDto.setActions(Collections.singleton(actionDto));
        typeDto.setAssociations(Collections.singleton(associationDto));
        typeDto.setInheritActions(true);
        typeDto.setParent(RecordRef.create("type", parent.getExtId()));
    }

    @Test
    void testEntityToDto() {

        //  arrange
        when(actionConverter.entityToDto(actionEntity)).thenReturn(actionDto);
        when(associationConverter.entityToDto(associationEntity)).thenReturn(associationDto);

        //  act
        TypeDto resultDto = typeConverter.targetToSource(typeEntity);

        //  assert
        Assert.assertEquals(resultDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultDto.getName(), typeEntity.getName());
        Assert.assertEquals(resultDto.getDescription(), typeEntity.getDescription());
        Assert.assertEquals(resultDto.getTenant(), typeEntity.getTenant());
        Assert.assertEquals(resultDto.getParent(), RecordRef.create("type", parent.getExtId()));
        Assert.assertEquals(resultDto.getActions(), Collections.singleton(actionDto));
        Assert.assertEquals(resultDto.getAssociations(), Collections.singleton(associationDto));
    }

    @Test
    void testEntityToDtoWithoutParentAndAssociationsAndActions() {

        //  arrange
        typeEntity.setParent(null);
        typeEntity.setAssocsToOther(null);
        typeEntity.setActions(null);

        //  act
        TypeDto resultDto = typeConverter.targetToSource(typeEntity);

        //  assert
        Assert.assertEquals(resultDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultDto.getName(), typeEntity.getName());
        Assert.assertEquals(resultDto.getDescription(), typeEntity.getDescription());
        Assert.assertEquals(resultDto.getTenant(), typeEntity.getTenant());
        Mockito.verify(actionConverter, Mockito.times(0)).entityToDto(Mockito.any());
    }

    @Test
    void testDtoToEntity() {

        //  arrange
        when(actionConverter.dtoToEntity(actionDto)).thenReturn(actionEntity);
        when(associationConverter.dtoToEntity(associationDto)).thenReturn(associationEntity);
        when(typeRepository.findByExtId(parent.getExtId())).thenReturn(Optional.of(parent));
        when(typeRepository.findByExtId(typeEntity.getExtId())).thenReturn(Optional.of(typeEntity));

        //  act
        TypeEntity resultEntity = typeConverter.sourceToTarget(typeDto);

        //  assert
        Assert.assertEquals(resultEntity.getExtId(), typeDto.getId());
        Assert.assertEquals(resultEntity.getId().longValue(), 123L);
        Assert.assertEquals(resultEntity.getName(), typeDto.getName());
        Assert.assertEquals(resultEntity.getDescription(), typeDto.getDescription());
        Assert.assertEquals(resultEntity.getTenant(), typeDto.getTenant());
        AssociationEntity associationEntityLocal = resultEntity.getAssocsToOther().iterator().next();
        Assert.assertEquals(associationEntityLocal.getExtId(), associationEntity.getExtId());
        Assert.assertEquals(associationEntityLocal.getSource().getExtId(), associationEntity.getSource().getExtId());
        Assert.assertEquals(associationEntityLocal.getTarget().getExtId(), associationEntity.getTarget().getExtId());
        Assert.assertEquals(resultEntity.getActions(), Collections.singletonList(actionEntity));
        Assert.assertEquals(resultEntity.getChilds(), Collections.emptySet());
        Assert.assertEquals(resultEntity.getParent(), parent);
        Assert.assertEquals(resultEntity.getAssocsToThis(), Collections.emptySet());
        Assert.assertEquals(resultEntity.getSections(), Collections.emptySet());
    }

    @Test
    void testDtoToEntityWithoutParentAndAssociationsAndExtIdAndActions() {

        //  arrange
        typeDto.setActions(null);
        typeDto.setParent(null);
        typeDto.setId(Strings.EMPTY);
        typeDto.setAssociations(null);

        //  act
        TypeEntity resultEntity = typeConverter.sourceToTarget(typeDto);

        //  assert
        Assert.assertEquals(resultEntity.getName(), typeDto.getName());
        Assert.assertEquals(resultEntity.getDescription(), typeDto.getDescription());
        Assert.assertEquals(resultEntity.getTenant(), typeDto.getTenant());
        Assert.assertEquals(resultEntity.getChilds(), Collections.emptySet());
        Assert.assertEquals(resultEntity.getAssocsToThis(), Collections.emptySet());
        Assert.assertEquals(resultEntity.getSections(), Collections.emptySet());
        Mockito.verify(typeRepository, Mockito.times(0)).findByExtId(Mockito.any());
        Mockito.verify(actionConverter, Mockito.times(0)).dtoToEntity(Mockito.any());
    }
}
