package ru.citeck.ecos.model.dao;

import graphql.language.Field;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.record.EcosSectionRecord;
import ru.citeck.ecos.model.record.mutable.EcosSectionMutable;
import ru.citeck.ecos.model.service.impl.EcosSectionServiceImpl;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.PredicateServiceImpl;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsServiceImpl;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaFieldImpl;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class EcosSectionRecordsDaoTest {

    @Mock
    private EcosSectionServiceImpl sectionService;

    @Mock
    private PredicateServiceImpl predicateService;

    @Mock
    private RecordsServiceImpl recordsService;
    
    private EcosSectionRecordsDao recordsDao;

    @Before
    public void setUp() throws Exception {
        recordsDao = new EcosSectionRecordsDao(sectionService, predicateService);
        recordsDao.setRecordsService(recordsService);
    }

    @Test
    public void getMetaValuesReturnRecords() {
        EcosSectionDto dto = new EcosSectionDto("uuid", "a", "adesc","atenant", null);
        List<EcosSectionDto> dtos = Collections.singletonList(dto);
        RecordsQuery query = new RecordsQuery();

        given(sectionService.getAll()).willReturn(dtos);


        RecordsQueryResult<EcosSectionRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("uuid", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("adesc", result.getRecords().get(0).getAttribute("description", foo));
        Assert.assertEquals("atenant", result.getRecords().get(0).getAttribute("tenant", foo));
        Assert.assertNull(result.getRecords().get(0).getAttribute("parent", foo));
    }

    @Test
    public void getMetaValuesReturnRecordsWithPredicate() {
        EcosSectionDto dto = new EcosSectionDto("uuid", "a", "adesc","atenant", null);
        RecordsQuery query = new RecordsQuery();
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);

        given(sectionService.getByUuid("uuid")).willReturn(Optional.of(dto));

        RecordElement element = new RecordElement(null, RecordRef.create("", "type", "uuid"));

        given(predicateService.filter(Mockito.any(), Mockito.any())).willReturn(Arrays.asList(
            element
        ));


        RecordsQueryResult<EcosSectionRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("uuid", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("adesc", result.getRecords().get(0).getAttribute("description", foo));
        Assert.assertEquals("atenant", result.getRecords().get(0).getAttribute("tenant", foo));
        Assert.assertNull(result.getRecords().get(0).getAttribute("parent", foo));
    }


    @Test
    public void getValuesToMutateReturnOldElements() {
        EcosSectionDto dto = new EcosSectionDto("uuid", "a", "adesc","atenant", null);

        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "uuid"));


        List<EcosSectionMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getUuid(), mutables.get(0).getUuid());
    }

    @Test
    public void getValuesToMutateReturnNewElements() {
        EcosSectionDto dto = new EcosSectionDto("uuid", "a", "adesc","atenant", null);

        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "uuid"));


        List<EcosSectionMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getUuid(), mutables.get(0).getUuid());
    }

    @Test
    public void saveReturnSavedIds() {
        EcosSectionDto dto = new EcosSectionDto("uuid", "a", "desc", "", null);
        List<EcosSectionMutable> mutables = Arrays.asList(new EcosSectionMutable(dto));

        given(sectionService.update(dto)).willReturn(dto);

        RecordsMutResult result1 = recordsDao.save(mutables);


        Mockito.verify(sectionService, Mockito.times(1)).update(Mockito.any());
        Assert.assertEquals("uuid", result1.getRecords().get(0).getId().getId());
    }

    @Test
    public void saveDontUpdate() {
        List<EcosSectionMutable> mutables = Arrays.asList(new EcosSectionMutable(new EcosSectionDto(null, "a", "desc", "",null)));


        RecordsMutResult result1 = recordsDao.save(mutables);


        Mockito.verify(sectionService, Mockito.times(0)).update(Mockito.any());
        Assert.assertEquals(0, result1.getRecords().size());
    }

    @Test
    public void deleteSuccess() {
        RecordsDeletion deletion = new RecordsDeletion();
        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "uuid"));
        deletion.setRecords(refs);


        RecordsDelResult result = recordsDao.delete(deletion);


        Mockito.verify(sectionService, Mockito.times(1)).delete(Mockito.anyString());
        Assert.assertEquals(1, result.getRecords().size());
    }


}
