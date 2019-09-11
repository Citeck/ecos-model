package ru.citeck.ecos.model.dao;

import graphql.language.Field;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.record.EcosTypeRecord;
import ru.citeck.ecos.model.record.mutable.EcosTypeMutable;
import ru.citeck.ecos.model.service.impl.EcosTypeServiceImpl;
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

import java.util.*;

import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class EcosTypeRecordsDaoTest {

    @Mock
    private EcosTypeServiceImpl typeService;

    @Mock
    private PredicateServiceImpl predicateService;

    @Mock
    private RecordsServiceImpl recordsService;

    private EcosTypeRecordsDao recordsDao;

    @Before
    public void setUp() throws Exception {
        recordsDao = new EcosTypeRecordsDao(typeService, predicateService);
        recordsDao.setRecordsService(recordsService);
    }

    @Test
    public void getMetaValuesReturnRecords() {
        EcosTypeDto dto = new EcosTypeDto("extId", "a", "adesc","atenant", null, null);
        Set<EcosTypeDto> dtos = Collections.singleton(dto);
        RecordsQuery query = new RecordsQuery();

        given(typeService.getAll()).willReturn(dtos);


        RecordsQueryResult<EcosTypeRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("adesc", result.getRecords().get(0).getAttribute("description", foo));
        Assert.assertEquals("atenant", result.getRecords().get(0).getAttribute("tenant", foo));
        Assert.assertNull(result.getRecords().get(0).getAttribute("parent", foo));
    }

    @Test
    public void getMetaValuesReturnRecordsWithPredicate() {
        EcosTypeDto dto = new EcosTypeDto("extId", "a", "adesc","atenant", null, null);
        RecordsQuery query = new RecordsQuery();
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);


        RecordElement element = new RecordElement(null, RecordRef.create("", "type", "extId"));

        given(predicateService.filter(Mockito.any(), Mockito.any())).willReturn(Arrays.asList(element));
        given(typeService.getAll(Collections.singleton("extId"))).willReturn(Collections.singleton(dto));


        RecordsQueryResult<EcosTypeRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("adesc", result.getRecords().get(0).getAttribute("description", foo));
        Assert.assertEquals("atenant", result.getRecords().get(0).getAttribute("tenant", foo));
        Assert.assertNull(result.getRecords().get(0).getAttribute("parent", foo));
    }

    @Test
    public void getValuesToMutateReturnOldElements() {
        EcosTypeDto dto = new EcosTypeDto("extId", "a", "adesc","atenant", null, null);

        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "extId"));


        List<EcosTypeMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getExtId(), mutables.get(0).getExtId());
    }

    @Test
    public void getValuesToMutateReturnNewElements() {
        EcosTypeDto dto = new EcosTypeDto("extId", "a", "adesc","atenant", null, null);

        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "extId"));


        List<EcosTypeMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getExtId(), mutables.get(0).getExtId());
    }

    @Test
    public void saveReturnSavedIds() {
        EcosTypeDto dto = new EcosTypeDto("extId", "a", "desc", "", null, null);
        List<EcosTypeMutable> mutables = Arrays.asList(new EcosTypeMutable(dto));

        given(typeService.update(dto)).willReturn(dto);

        RecordsMutResult result1 = recordsDao.save(mutables);


        Mockito.verify(typeService, Mockito.times(1)).update(Mockito.any());
        Assert.assertEquals("extId", result1.getRecords().get(0).getId().getId());
    }

    @Test
    public void saveDontUpdate() {
        List<EcosTypeMutable> mutables = Arrays.asList(new EcosTypeMutable(new EcosTypeDto(null, "a", "desc", "", null, null)));


        RecordsMutResult result1 = recordsDao.save(mutables);


        Mockito.verify(typeService, Mockito.times(0)).update(Mockito.any());
        Assert.assertEquals(0, result1.getRecords().size());
    }

    @Test
    public void deleteSuccess() {
        RecordsDeletion deletion = new RecordsDeletion();
        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "extId"));
        deletion.setRecords(refs);


        RecordsDelResult result = recordsDao.delete(deletion);


        Mockito.verify(typeService, Mockito.times(1)).delete(Mockito.anyString());
        Assert.assertEquals(1, result.getRecords().size());
    }


}
