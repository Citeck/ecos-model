package ru.citeck.ecos.model.dao;

import graphql.language.Field;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.record.EcosSectionRecord;
import ru.citeck.ecos.model.record.mutable.EcosSectionMutable;
import ru.citeck.ecos.model.service.impl.EcosSectionServiceImpl;
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
public class EcosSectionRecordsDaoTest {

    @Mock
    private EcosSectionServiceImpl sectionService;

    @Mock
    private PredicateServiceImpl predicateService;

    private EcosSectionRecordsDao recordsDao;

    @BeforeEach
    public void setUp() throws Exception {
        recordsDao = new EcosSectionRecordsDao(sectionService, predicateService);
        RecordsServiceFactory factory = new RecordsServiceFactory();
        recordsDao.setRecordsServiceFactory(factory);
    }

    @Test
    public void getMetaValuesReturnRecords() {
        Set<RecordRef> types = Collections.singleton(RecordRef.create("type","typeId"));
        EcosSectionDto dto = new EcosSectionDto("extId", "a", "adesc","atenant", types);
        Set<EcosSectionDto> dtos = Collections.singleton(dto);
        RecordsQuery query = new RecordsQuery();

        given(sectionService.getAll()).willReturn(dtos);


        RecordsQueryResult<EcosSectionRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("adesc", result.getRecords().get(0).getAttribute("description", foo));
        Assert.assertEquals("atenant", result.getRecords().get(0).getAttribute("tenant", foo));
        Assert.assertEquals(types, result.getRecords().get(0).getAttribute("types", foo));
        Assert.assertNull(result.getRecords().get(0).getAttribute("parent", foo));
    }

    @Test
    public void getMetaValuesReturnRecordsWithPredicate() {
        Set<RecordRef> types = Collections.singleton(RecordRef.create("type","typeId"));
        EcosSectionDto dto = new EcosSectionDto("extId", "a", "adesc","atenant", types);
        RecordsQuery query = new RecordsQuery();
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);

        RecordElement element = new RecordElement(null, RecordRef.create("", "type", "extId"));

        given(predicateService.filter(Mockito.any(), Mockito.any())).willReturn(Arrays.asList(element));
        given(sectionService.getAll(Collections.singleton("extId"))).willReturn(Collections.singleton(dto));


        RecordsQueryResult<EcosSectionRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("adesc", result.getRecords().get(0).getAttribute("description", foo));
        Assert.assertEquals("atenant", result.getRecords().get(0).getAttribute("tenant", foo));
        Assert.assertEquals(types, result.getRecords().get(0).getAttribute("types", foo));
        Assert.assertNull(result.getRecords().get(0).getAttribute("parent", foo));
    }


    @Test
    public void getValuesToMutateReturnOldElements() {
        EcosSectionDto dto = new EcosSectionDto("extId", "a", "adesc","atenant", null);

        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "extId"));


        List<EcosSectionMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getId(), mutables.get(0).getId());
    }

    @Test
    public void getValuesToMutateReturnNewElements() {
        EcosSectionDto dto = new EcosSectionDto("extId", "a", "adesc","atenant", null);

        List<RecordRef> refs = Arrays.asList(RecordRef.create("type", "extId"));


        List<EcosSectionMutable> mutables = recordsDao.getValuesToMutate(refs);


        Assert.assertEquals(1L, mutables.size());
        Assert.assertEquals(dto.getId(), mutables.get(0).getId());
    }

    @Test
    public void saveReturnSavedIds() {
        EcosSectionDto dto = new EcosSectionDto("extId", "a", "desc", "", null);
        List<EcosSectionMutable> mutables = Arrays.asList(new EcosSectionMutable(dto));

        given(sectionService.update(dto)).willReturn(dto);

        RecordsMutResult result1 = recordsDao.save(mutables);


        Mockito.verify(sectionService, Mockito.times(1)).update(Mockito.any());
        Assert.assertEquals("extId", result1.getRecords().get(0).getId().getId());
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
        List<RecordRef> refs = Arrays.asList(RecordRef.create("", "type", "extId"));
        deletion.setRecords(refs);


        RecordsDelResult result = recordsDao.delete(deletion);


        Mockito.verify(sectionService, Mockito.times(1)).delete(Mockito.anyString());
        Assert.assertEquals(1, result.getRecords().size());
    }


}
