package ru.citeck.ecos.model.dao;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.record.mutable.EcosAssociationMutable;
import ru.citeck.ecos.model.service.impl.EcosAssociationServiceImpl;
import ru.citeck.ecos.predicate.PredicateServiceImpl;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsServiceImpl;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class EcosAssociationRecordsDaoTest {

    @Mock
    private EcosAssociationServiceImpl associationService;

    @Mock
    private PredicateServiceImpl predicateService;

    @Mock
    private RecordsServiceImpl recordsService;

    private EcosAssociationRecordsDao recordsDao;

    @Before
    public void setUp() throws Exception {
        recordsDao = new EcosAssociationRecordsDao(associationService, predicateService);
        recordsDao.setRecordsService(recordsService);
    }

    @Test
    public void getValuesToMutateReturnRequestedRecords() {

        String recordExtId = "a";
        String recordName = "aname";
        String recordTitle = "atitle";
        RecordRef recordType = RecordRef.create("type", "customType");

        RecordRef recordRef1 = RecordRef.create("association", recordExtId);
        List<RecordRef> refs = Collections.singletonList(recordRef1);   // input

        EcosAssociationDto dto = new EcosAssociationDto(recordExtId, recordName, recordTitle, recordType);
        Set<EcosAssociationDto> dtos = Collections.singleton(dto);
        Set<String> setExtIds = Collections.singleton(recordExtId);
        given(associationService.getAll(setExtIds)).willReturn(dtos);


        List<EcosAssociationMutable> resultList = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1, resultList.size());
        EcosAssociationMutable e = resultList.get(0);
        Assert.assertEquals(recordExtId, e.getExtId());
        Assert.assertEquals(recordName, e.getName());
        Assert.assertEquals(recordTitle, e.getTitle());
        Assert.assertEquals(recordType, e.getType());
    }

    @Test
    public void getValuesToMutateNotReturnRequestedRecords() {

        String nonexistentExtId = "b";

        RecordRef recordRef1 = RecordRef.create("association", nonexistentExtId);
        List<RecordRef> refs = Collections.singletonList(recordRef1);   // input

        Set<String> setExtIds = Collections.singleton(nonexistentExtId);
        given(associationService.getAll(setExtIds)).willReturn(Collections.emptySet());


        List<EcosAssociationMutable> resultList = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1, resultList.size());
        EcosAssociationMutable e = resultList.get(0);
        Assert.assertEquals(nonexistentExtId, e.getExtId());
        Assert.assertNull(e.getName());
        Assert.assertNull(e.getTitle());
        Assert.assertNull(e.getType());
    }

/*
    @Test
    public void getMetaValuesReturnRecords() {
        RecordRef type = RecordRef.create("type", "customExtId");
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "atitle", type);
        Set<EcosAssociationDto> dtos = Collections.singleton(dto);
        RecordsQuery query = new RecordsQuery();

        given(associationService.getAll()).willReturn(dtos);


        RecordsQueryResult<EcosAssociationRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("atitle", result.getRecords().get(0).getAttribute("title", foo));
        Assert.assertEquals(type, result.getRecords().get(0).getAttribute("type", foo));
    }

    @Test
    public void getMetaValuesReturnRecordsWithPredicate() {
        RecordRef type = RecordRef.create("type", "customExtId");
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "atitle", type);
        RecordsQuery query = new RecordsQuery();
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);

        given(associationService.getByExtId("extId")).willReturn(dto);

        RecordElement element = new RecordElement(null, RecordRef.create("", "type", "extId"));

        given(predicateService.filter(Mockito.any(), Mockito.any()))
            .willReturn(Collections.singletonList(element));


        RecordsQueryResult<EcosAssociationRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("atitle", result.getRecords().get(0).getAttribute("title", foo));
        Assert.assertEquals(type, result.getRecords().get(0).getAttribute("type", foo));
    }

    @Test
    public void getValuesToMutateReturnOldElements() {
        RecordRef type = RecordRef.create("type", "customExtId");
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "atitle", type);

        List<RecordRef> refs = Collections.singletonList(RecordRef.create("", "type", "extId"));

        given(associationService.getAll(Collections.singleton("extId"))).willReturn(Collections.singleton(dto));


        List<EcosAssociationMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getExtId(), mutables.get(0).getExtId());
        Assert.assertEquals(dto.getName(), mutables.get(0).getName());
        Assert.assertEquals(dto.getTitle(), mutables.get(0).getTitle());
    }

    @Test
    public void getValuesToMutateReturnNewElements() {
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "adesc","atenant", null, null);

        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "extId"));


        List<EcosAssociationMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getExtId(), mutables.get(0).getExtId());
    }

    @Test
    public void saveReturnSavedIds() {
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "desc", "", null, null);
        List<EcosAssociationMutable> mutables = Arrays.asList(new EcosAssociationMutable(dto));

        given(associationService.update(dto)).willReturn(dto);

        RecordsMutResult result1 = recordsDao.save(mutables);


        Mockito.verify(associationService, Mockito.times(1)).update(Mockito.any());
        Assert.assertEquals("extId", result1.getRecords().get(0).getId().getId());
    }

    @Test
    public void saveDontUpdate() {
        List<EcosAssociationMutable> mutables = Arrays.asList(new EcosAssociationMutable(new EcosAssociationDto(null, "a", "desc", "", null, null)));


        RecordsMutResult result1 = recordsDao.save(mutables);


        Mockito.verify(associationService, Mockito.times(0)).update(Mockito.any());
        Assert.assertEquals(0, result1.getRecords().size());
    }

    @Test
    public void deleteSuccess() {
        RecordsDeletion deletion = new RecordsDeletion();
        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "extId"));
        deletion.setRecords(refs);


        RecordsDelResult result = recordsDao.delete(deletion);


        Mockito.verify(associationService, Mockito.times(1)).delete(Mockito.anyString());
        Assert.assertEquals(1, result.getRecords().size());
    }*/


}
