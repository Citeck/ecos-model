package ru.citeck.ecos.model.dao;

import graphql.language.Field;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.record.EcosAssociationRecord;
import ru.citeck.ecos.model.record.mutable.EcosAssociationMutable;
import ru.citeck.ecos.model.service.impl.EcosAssociationServiceImpl;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.PredicateServiceImpl;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsServiceFactory;
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
import java.util.Set;

import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
public class EcosAssociationRecordsDaoTest {

    @Mock
    private EcosAssociationServiceImpl associationService;

    @Mock
    private PredicateServiceImpl predicateService;

    private EcosAssociationRecordsDao recordsDao;

    @BeforeEach
    public void setUp() throws Exception {
        recordsDao = new EcosAssociationRecordsDao(associationService, predicateService);
        RecordsServiceFactory recordsServiceFactory = new RecordsServiceFactory();
        recordsDao.setRecordsServiceFactory(recordsServiceFactory);
    }

    @Test
    public void getValuesToMutateReturnRequestedRecords() {

        String recordExtId = "a";
        String recordName = "aname";
        String recordTitle = "atitle";
        RecordRef target = RecordRef.create("type", "target");
        RecordRef source = RecordRef.create("type", "source");

        RecordRef recordRef1 = RecordRef.create("association", recordExtId);
        List<RecordRef> refs = Collections.singletonList(recordRef1);

        EcosAssociationDto dto = new EcosAssociationDto(recordExtId, recordName, recordTitle, source, target);
        Set<EcosAssociationDto> dtos = Collections.singleton(dto);
        Set<String> setExtIds = Collections.singleton(recordExtId);
        given(associationService.getAll(setExtIds)).willReturn(dtos);


        List<EcosAssociationMutable> resultList = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1, resultList.size());
        EcosAssociationMutable e = resultList.get(0);
        Assert.assertEquals(recordExtId, e.getId());
        Assert.assertEquals(recordName, e.getName());
        Assert.assertEquals(recordTitle, e.getTitle());
        Assert.assertEquals(source, e.getSourceType());
        Assert.assertEquals(target, e.getTargetType());
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
        Assert.assertEquals(nonexistentExtId, e.getId());
        Assert.assertNull(e.getName());
        Assert.assertNull(e.getTitle());
    }

    @Test
    public void getMetaValuesReturnRecords() {
        RecordRef source = RecordRef.create("type", "sourceId");
        RecordRef target = RecordRef.create("type", "targetId");
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "atitle", source, target);
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
        Assert.assertEquals(target, result.getRecords().get(0).getAttribute("target", foo));
        Assert.assertEquals(source, result.getRecords().get(0).getAttribute("source", foo));
    }

    @Test
    public void getMetaValuesReturnRecordsWithPredicate() {
        RecordRef source = RecordRef.create("type", "sourceId");
        RecordRef target = RecordRef.create("type", "targetId");
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "atitle", source, target);
        RecordsQuery query = new RecordsQuery();
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);

        RecordElement element = new RecordElement(null, RecordRef.create("", "type", "extId"));

        given(predicateService.filter(Mockito.any(), Mockito.any())).willReturn(Collections.singletonList(element));
        given(associationService.getAll(Collections.singleton("extId"))).willReturn(Collections.singleton(dto));


        RecordsQueryResult<EcosAssociationRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("atitle", result.getRecords().get(0).getAttribute("title", foo));
        Assert.assertEquals(target, result.getRecords().get(0).getAttribute("target", foo));
        Assert.assertEquals(source, result.getRecords().get(0).getAttribute("source", foo));
    }

    @Test
    public void getValuesToMutateReturnOldElements() {
        RecordRef source = RecordRef.create("type", "sourceId");
        RecordRef target = RecordRef.create("type", "targetId");
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "atitle", source, target);

        List<RecordRef> refs = Collections.singletonList(RecordRef.create("", "type", "extId"));

        given(associationService.getAll(Collections.singleton("extId"))).willReturn(Collections.singleton(dto));


        List<EcosAssociationMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getId(), mutables.get(0).getId());
        Assert.assertEquals(dto.getName(), mutables.get(0).getName());
        Assert.assertEquals(dto.getTitle(), mutables.get(0).getTitle());
        Assert.assertEquals(target, mutables.get(0).getTargetType());
        Assert.assertEquals(source, mutables.get(0).getSourceType());
    }

    @Test
    public void getValuesToMutateReturnNewElements() {
        RecordRef source = RecordRef.create("type", "sourceId");
        RecordRef target = RecordRef.create("type", "targetId");
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "atitle", source, target);

        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "extId"));


        List<EcosAssociationMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getId(), mutables.get(0).getId());
    }

    @Test
    public void saveReturnSavedIds() {
        RecordRef source = RecordRef.create("type", "sourceId");
        RecordRef target = RecordRef.create("type", "targetId");
        EcosAssociationDto dto = new EcosAssociationDto("extId", "a", "atitle", source, target);
        List<EcosAssociationMutable> mutables = Arrays.asList(new EcosAssociationMutable(dto));

        given(associationService.update(dto)).willReturn(dto);

        RecordsMutResult result1 = recordsDao.save(mutables);


        Mockito.verify(associationService, Mockito.times(1)).update(Mockito.any());
        Assert.assertEquals("extId", result1.getRecords().get(0).getId().getId());
    }

    @Test
    public void saveDontUpdate() {
        RecordRef source = RecordRef.create("type", "sourceId");
        RecordRef target = RecordRef.create("type", "targetId");
        EcosAssociationDto dto = new EcosAssociationDto(null, "a", "atitle", source, target);
        List<EcosAssociationMutable> mutables = Collections.singletonList(new EcosAssociationMutable(dto));


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
    }


}
