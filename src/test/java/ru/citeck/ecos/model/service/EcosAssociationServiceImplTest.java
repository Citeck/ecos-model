package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.repository.EcosAssociationRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.exception.TypeNotFoundException;
import ru.citeck.ecos.model.service.impl.EcosAssociationServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
public class EcosAssociationServiceImplTest {

    @Mock
    private EcosTypeRepository typeRepository;

    @Mock
    private EcosAssociationRepository associationRepository;

    private EcosAssociationServiceImpl ecosAssociationService;

    private EcosAssociationEntity ecosAssociationEntity;
    private EcosTypeEntity source;
    private EcosTypeEntity target;

    @BeforeEach
    public void init() {
        ecosAssociationService = new EcosAssociationServiceImpl(associationRepository, typeRepository);

        source = new EcosTypeEntity();
        source.setExtId("sourceId");

        target = new EcosTypeEntity();
        target.setExtId("targetId");

        ecosAssociationEntity = new EcosAssociationEntity(
            "a", 1L, "a_name", "a_title", source, target);
    }

    @Test
    public void getAllReturnTypes() {

        List<EcosAssociationEntity> entities = Collections.singletonList(ecosAssociationEntity);
        given(associationRepository.findAll()).willReturn(entities);


        Set<EcosAssociationDto> dtos = ecosAssociationService.getAll();


        Assert.assertEquals(1, dtos.size());
        EcosAssociationDto assocDto = dtos.iterator().next();
        Assert.assertEquals("a", assocDto.getId());
        Assert.assertEquals("a_name", assocDto.getName());
        Assert.assertEquals("a_title", assocDto.getTitle());
        Assert.assertEquals(source.getExtId(), assocDto.getSourceType().getId());
        Assert.assertEquals(target.getExtId(), assocDto.getTargetType().getId());
    }

    @Test
    public void getAllSelectedReturnAssocs() {
        Set<EcosAssociationEntity> entities = Collections.singleton(ecosAssociationEntity);

        given(associationRepository.findAllByExtIds(Collections.singleton("a"))).willReturn(entities);

        Set<EcosAssociationDto> dtos = ecosAssociationService.getAll(Collections.singleton("a"));


        Assert.assertEquals(1, dtos.size());
        EcosAssociationDto assocDto = dtos.iterator().next();
        Assert.assertEquals("a", assocDto.getId());
        Assert.assertEquals("a_name", assocDto.getName());
        Assert.assertEquals("a_title", assocDto.getTitle());
        Assert.assertEquals(source.getExtId(), assocDto.getSourceType().getId());
        Assert.assertEquals(target.getExtId(), assocDto.getTargetType().getId());
    }

    @Test
    public void getAllSelectedReturnNothing() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            given(associationRepository.findAllByExtIds(Collections.singleton("b"))).willReturn(null);

            ecosAssociationService.getAll(Collections.singleton("b"));
        });
    }

    @Test
    public void getByIdReturnResult() {

        given(associationRepository.findByExtId("a")).willReturn(Optional.of(ecosAssociationEntity));


        EcosAssociationDto dto = ecosAssociationService.getByExtId("a");


        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("a_name", dto.getName());
        Assert.assertEquals("a_title", dto.getTitle());
        Assert.assertEquals(source.getExtId(), dto.getSourceType().getId());
        Assert.assertEquals(target.getExtId(), dto.getTargetType().getId());
    }

    @Test
    public void getByIdReturnNothing() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            given(associationRepository.findByExtId("b")).willReturn(Optional.empty());


            ecosAssociationService.getByExtId("b");
        });
    }

    @Test
    public void deleteSuccess() {

        given(associationRepository.findByExtId("a")).willReturn(Optional.of(ecosAssociationEntity));


        ecosAssociationService.delete("a");


        Mockito.verify(associationRepository, times(1)).deleteById(Mockito.anyLong());

    }

    @Test
    public void deleteNoDeletion() {

        given(associationRepository.findByExtId("a")).willReturn(Optional.empty());


        ecosAssociationService.delete("a");


        Mockito.verify(associationRepository, times(0)).deleteById(Mockito.anyLong());
    }

    @Test
    public void updateSuccessWithExtId() {

        given(associationRepository.findByExtId("a")).willReturn(Optional.empty());
        given(typeRepository.findByExtId(source.getExtId())).willReturn(Optional.of(source));
        given(typeRepository.findByExtId(target.getExtId())).willReturn(Optional.of(target));


        EcosAssociationDto dto = ecosAssociationService.update(entityToDto(ecosAssociationEntity));


        Mockito.verify(associationRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("a_name", dto.getName());
        Assert.assertEquals("a_title", dto.getTitle());
        Assert.assertEquals(source.getExtId(), dto.getSourceType().getId());
        Assert.assertEquals(target.getExtId(), dto.getTargetType().getId());
    }

    @Test
    public void updateSuccessWithoutExtId() {

        ecosAssociationEntity.setExtId(null);

        given(typeRepository.findByExtId(source.getExtId())).willReturn(Optional.of(source));
        given(typeRepository.findByExtId(target.getExtId())).willReturn(Optional.of(target));


        EcosAssociationDto dto = ecosAssociationService.update(entityToDto(ecosAssociationEntity));


        Mockito.verify(associationRepository, times(1)).save(Mockito.any());
        UUID checkedUUID = UUID.fromString(dto.getId());
        Assert.assertEquals("a_name", dto.getName());
        Assert.assertEquals("a_title", dto.getTitle());
        Assert.assertEquals(source.getExtId(), dto.getSourceType().getId());
        Assert.assertEquals(target.getExtId(), dto.getTargetType().getId());
    }

    @Test
    public void updateNoSourceTypeException() {
        Assertions.assertThrows(TypeNotFoundException.class, () -> {

            given(associationRepository.findByExtId("b")).willReturn(Optional.empty());
            given(typeRepository.findByExtId(target.getExtId())).willReturn(Optional.of(target));


            ecosAssociationService.update(entityToDto(ecosAssociationEntity));


            Mockito.verify(typeRepository, times(0)).save(Mockito.any());
        });
    }

    @Test
    public void updateNoTargetTypeException() {
        Assertions.assertThrows(TypeNotFoundException.class, () -> {
            given(associationRepository.findByExtId("b")).willReturn(Optional.empty());
            given(typeRepository.findByExtId(source.getExtId())).willReturn(Optional.of(source));


            ecosAssociationService.update(entityToDto(ecosAssociationEntity));


            Mockito.verify(typeRepository, times(0)).save(Mockito.any());
        });
    }

    private EcosAssociationDto entityToDto(EcosAssociationEntity entity) {
        return new EcosAssociationDto(
            entity.getExtId(),
            entity.getName(),
            entity.getTitle(),
            RecordRef.create("type", entity.getSource().getExtId()),
            RecordRef.create("type", entity.getTarget().getExtId()));
    }

}
