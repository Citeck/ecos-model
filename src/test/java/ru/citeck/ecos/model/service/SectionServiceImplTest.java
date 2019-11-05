package ru.citeck.ecos.model.service;


import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.impl.SectionConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.repository.SectionRepository;
import ru.citeck.ecos.model.service.impl.SectionServiceImpl;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
public class SectionServiceImplTest {

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private SectionConverter converter;

    private SectionService sectionService;

    @BeforeEach
    public void init() {
        sectionService = new SectionServiceImpl(sectionRepository, converter);
    }


    @Test
    public void getAllReturnTypes() {

        SectionEntity sectionEntity = new SectionEntity("a", 1L, null, "a",
            "a_desc", "a_tenant");
        SectionEntity sectionEntity2 = new SectionEntity("b", 2L, null, "b",
            "b_desc", "b_tenant");

        List<SectionEntity> entities = Arrays.asList(
            sectionEntity,
            sectionEntity2
        );

        given(sectionRepository.findAll()).willReturn(entities);


        Set<SectionDto> dtos = sectionService.getAll();


        Assert.assertEquals(2, dtos.size());
    }

    @Test
    public void getAllWhenReturnNothing() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            given(sectionRepository.findAll()).willReturn(null);


            sectionService.getAll();
        });
    }

    @Test
    public void getAllSelectedReturnTypes() {

        SectionEntity sectionEntity = new SectionEntity("b", 2L, null, "b",
            "b_desc", "b_tenant");

        Set<SectionEntity> entities = Collections.singleton(sectionEntity);

        given(sectionRepository.findAllByExtIds(Collections.singleton("b"))).willReturn(entities);


        Set<SectionDto> dtos = sectionService.getAll(Collections.singleton("b"));


        Assert.assertEquals(1, dtos.size());
    }

    @Test
    public void getAllSelectedNothing() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            given(sectionRepository.findAllByExtIds(Collections.singleton("b"))).willReturn(null);


            sectionService.getAll(Collections.singleton("b"));
        });
    }

    @Test
    public void getByIdReturnTypeDto() {
        SectionEntity sectionEntity = new SectionEntity("a", 1L, null, "a",
            "a_desc", "a_tenant");
        SectionEntity sectionEntity2 = new SectionEntity("b", 2L, null, "b",
            "b_desc", "b_tenant");

        given(sectionRepository.findByExtId("b")).willReturn(Optional.of(sectionEntity2));


        SectionDto dto = sectionService.getByExtId("b");


        Assert.assertEquals("b", dto.getId());
        Assert.assertEquals("b", dto.getName());
        Assert.assertEquals("b_desc", dto.getDescription());
        Assert.assertEquals("b_tenant", dto.getTenant());
    }

    @Test
    public void getByIdReturnNothing() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            sectionService.getByExtId("b");
        });
    }

    @Test
    public void deleteSuccess() {
        SectionEntity sectionEntity = new SectionEntity("a",1L, null, "a",
            "a_desc", "a_tenant");

        given(sectionRepository.findByExtId("a")).willReturn(Optional.of(sectionEntity));


        sectionService.delete("a");


        Mockito.verify(sectionRepository, Mockito.times(1)).deleteById(Mockito.anyLong());

    }

    @Test
    public void deleteNoDeletion() {
        given(sectionRepository.findByExtId("a")).willReturn(Optional.empty());


        sectionService.delete("a");


        Mockito.verify(sectionRepository, Mockito.times(0)).deleteById(Mockito.anyLong());
    }

    @Test
    public void updateSuccessNewEntity() {
        SectionEntity sectionEntity = new SectionEntity("a", 1L, null, "aname",
            "a_desc", "a_tenant");

        given(sectionRepository.findByExtId("a")).willReturn(Optional.empty());


        SectionDto dto = sectionService.update(entityToDto(sectionEntity));


        Mockito.verify(sectionRepository, times(1)).save(Mockito.any());
        Assert.assertEquals("a", dto.getId());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
    }

    @Test
    public void updateSuccessWithNoUUIDNewEntity() {
        SectionEntity sectionEntity = new SectionEntity(null, 1L, null, "aname", "a_desc", "a_tenant");


        SectionDto dto = sectionService.update(entityToDto(sectionEntity));


        Mockito.verify(sectionRepository, times(1)).save(Mockito.any());
        Assert.assertNotNull(dto.getId());
        Assert.assertEquals("aname", dto.getName());
        Assert.assertEquals("a_desc", dto.getDescription());
        Assert.assertEquals("a_tenant", dto.getTenant());
    }

    private SectionDto entityToDto(SectionEntity entity) {
        Set<RecordRef> typesRefs = null;
        if (entity.getTypes() != null) {
            typesRefs = entity.getTypes().stream()
                .map(e -> RecordRef.create(TypeRecordsDao.ID, e.getExtId()))
                .collect(Collectors.toSet());
        }
        return new SectionDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            typesRefs);
    }
}
