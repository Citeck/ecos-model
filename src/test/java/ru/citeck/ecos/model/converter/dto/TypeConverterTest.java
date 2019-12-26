package ru.citeck.ecos.model.converter.dto;

import org.apache.logging.log4j.util.Strings;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.model.converter.dto.impl.TypeConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.domain.TypeActionEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.AssociationRepository;
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
    private AssociationRepository associationRepository;

    @MockBean
    private DtoConverter<TypeAssociationDto, AssociationEntity> associationConverter;

    private TypeConverter typeConverter;

    private TypeEntity typeEntity;
    private TypeDto typeDto;

    private TypeEntity parent;

    private TypeActionEntity actionEntity;
    private ModuleRef actionRef;

    private AssociationEntity associationEntity;
    private TypeAssociationDto associationDto;

    @BeforeEach
    void setUp() {
        typeConverter = new TypeConverter(typeRepository, associationRepository, associationConverter);

        TypeEntity child = new TypeEntity();
        child.setExtId("child");

        parent = new TypeEntity();
        parent.setExtId("parent");

        actionRef = ModuleRef.create("ui/action", "action");

        actionEntity = new TypeActionEntity();
        actionEntity.setActionId(actionRef.toString());

        SectionEntity sectionEntity = new SectionEntity();
        sectionEntity.setExtId("section");

        typeEntity = new TypeEntity();
        typeEntity.setExtId("type");
        typeEntity.setId(123L);
        typeEntity.setName("name");
        typeEntity.setTenant("tenant");
        typeEntity.setDescription("desc");
        typeEntity.setChildren(Collections.singleton(child));
        typeEntity.setParent(parent);
        typeEntity.addAction(actionEntity);
        typeEntity.setSections(Collections.singleton(sectionEntity));

        associationEntity = new AssociationEntity();
        associationEntity.setExtId("association");
        associationEntity.setTarget(parent);

        typeEntity.setAssocsToOthers(Collections.singleton(associationEntity));

        associationDto = new TypeAssociationDto();
        associationDto.setId("association");
        associationDto.setTargetType(RecordRef.create("type", "parent"));

        typeDto = new TypeDto();
        typeDto.setId("type");
        typeDto.setName("name");
        typeDto.setTenant("tenant");
        typeDto.setDescription("desc");
        typeDto.setActions(Collections.singleton(actionRef));
        typeDto.setAssociations(Collections.singleton(associationDto));
        typeDto.setInheritActions(true);
        typeDto.setParent(RecordRef.create("type", parent.getExtId()));
    }

    @Test
    void testEntityToDto() {

        //  arrange
        when(associationConverter.entityToDto(associationEntity)).thenReturn(associationDto);

        //  act
        TypeDto resultDto = typeConverter.targetToSource(typeEntity);

        //  assert
        Assert.assertEquals(resultDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultDto.getName(), typeEntity.getName());
        Assert.assertEquals(resultDto.getDescription(), typeEntity.getDescription());
        Assert.assertEquals(resultDto.getTenant(), typeEntity.getTenant());
        Assert.assertEquals(resultDto.getParent(), RecordRef.create("emodel", "type", parent.getExtId()));
        Assert.assertEquals(resultDto.getActions(), Collections.singleton(actionRef));
        Assert.assertEquals(resultDto.getAssociations(), Collections.singleton(associationDto));
    }

    @Test
    void testEntityToDtoWithoutParentAndAssociationsAndActions() {

        //  arrange
        typeEntity.setParent(null);
        typeEntity.setAssocsToOthers(Collections.emptySet());
        typeEntity.removeAction(actionEntity);

        //  act
        TypeDto resultDto = typeConverter.targetToSource(typeEntity);

        //  assert
        Assert.assertEquals(resultDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultDto.getName(), typeEntity.getName());
        Assert.assertEquals(resultDto.getDescription(), typeEntity.getDescription());
        Assert.assertEquals(resultDto.getTenant(), typeEntity.getTenant());
    }

    @Test
    void testDtoToEntity() {

        //  arrange
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
        AssociationEntity associationEntityLocal = resultEntity.getAssocsToOthers().iterator().next();
        Assert.assertEquals(associationEntityLocal.getExtId(), associationEntity.getExtId());
        Assert.assertEquals(
            associationEntityLocal.getTarget().getExtId(),
            associationEntity.getTarget().getExtId());
        Assert.assertEquals(resultEntity.getActions(), Collections.singletonList(actionEntity));
        Assert.assertEquals(resultEntity.getChildren(), Collections.emptySet());
        Assert.assertEquals(resultEntity.getParent(), parent);
        Assert.assertEquals(resultEntity.getSections(), Collections.emptySet());
    }

    @Test
    void testDtoToEntityWithoutParentAndAssociationsAndExtIdAndActions() {

        //  arrange
        typeDto.setActions(Collections.emptySet());
        typeDto.setParent(null);
        typeDto.setId(Strings.EMPTY);
        typeDto.setAssociations(Collections.emptySet());

        //  act
        TypeEntity resultEntity = typeConverter.sourceToTarget(typeDto);

        //  assert
        Assert.assertEquals(resultEntity.getName(), typeDto.getName());
        Assert.assertEquals(resultEntity.getDescription(), typeDto.getDescription());
        Assert.assertEquals(resultEntity.getTenant(), typeDto.getTenant());
        Assert.assertEquals(resultEntity.getChildren(), Collections.emptySet());
        Assert.assertEquals(resultEntity.getSections(), Collections.emptySet());
    }
}
