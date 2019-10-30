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
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.service.impl.EcosTypeServiceImpl;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.PredicateServiceImpl;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaFieldImpl;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@SuppressWarnings("unchecked")
public class EcosTypeRecordsDaoTest {

    @Mock
    private EcosTypeServiceImpl typeService;

    @Mock
    private PredicateServiceImpl predicateService;

    @Mock
    private RecordsService recordsService;

    private EcosTypeRecordsDao recordsDao;

    @BeforeEach
    public void setUp() throws Exception {
        recordsDao = new EcosTypeRecordsDao(typeService, predicateService, recordsService);
        RecordsServiceFactory factory = new RecordsServiceFactory();
        recordsDao.setRecordsServiceFactory(factory);
    }

    @Test
    public void getMetaValuesReturnRecords() {

        EcosTypeDto dto = new EcosTypeDto("extId", "a", "adesc","atenant",
            RecordRef.create(EcosTypeRecordsDao.ID, "parentId"),
            Collections.singleton(new EcosAssociationDto("assocId", "name", "title",
                RecordRef.create(EcosTypeRecordsDao.ID, "sourceId"),RecordRef.create(EcosTypeRecordsDao.ID, "targetId"))), Collections.EMPTY_SET, false);
        Set<EcosTypeDto> dtos = Collections.singleton(dto);
        RecordsQuery query = new RecordsQuery();

        given(typeService.getAll()).willReturn(dtos);


        RecordsQueryResult<EcosTypeRecordsDao.EcosTypeRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1L, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("adesc", result.getRecords().get(0).getAttribute("description", foo));
        Assert.assertEquals("atenant", result.getRecords().get(0).getAttribute("tenant", foo));
        Assert.assertEquals("parentId", ((RecordRef)result.getRecords().get(0).getAttribute("parent", foo)).getId());
        Set<RecordRef> resultAssocs = (Set<RecordRef>)result.getRecords().get(0).getAttribute("associations", foo);
        Assert.assertEquals("assocId", (resultAssocs.iterator().next()).getId());
    }

    @Test
    public void getMetaValuesReturnRecordsWithPredicate() {
        EcosTypeDto dto = new EcosTypeDto("extId", "a", "adesc","atenant",
            RecordRef.create(EcosTypeRecordsDao.ID, "parentId"),
            Collections.singleton(new EcosAssociationDto("assocId", "name", "title",
                RecordRef.create(EcosTypeRecordsDao.ID, "sourceId"),RecordRef.create(EcosTypeRecordsDao.ID, "targetId"))), Collections.EMPTY_SET, false);
        RecordsQuery query = new RecordsQuery();
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);


        RecordElement element = new RecordElement(null, RecordRef.create("", EcosTypeRecordsDao.ID, "extId"));

        given(predicateService.filter(Mockito.any(), Mockito.any())).willReturn(Arrays.asList(element));
        given(typeService.getAll(Collections.singleton("extId"))).willReturn(Collections.singleton(dto));


        RecordsQueryResult<EcosTypeRecordsDao.EcosTypeRecord> result = recordsDao.getMetaValues(query);


        MetaField foo = new MetaFieldImpl(new Field(""));
        Assert.assertEquals(1, result.getTotalCount());
        Assert.assertFalse(result.getHasMore());
        Assert.assertEquals("extId", result.getRecords().get(0).getId());
        Assert.assertEquals("a", result.getRecords().get(0).getAttribute("name", foo));
        Assert.assertEquals("adesc", result.getRecords().get(0).getAttribute("description", foo));
        Assert.assertEquals("atenant", result.getRecords().get(0).getAttribute("tenant", foo));
        Assert.assertEquals("parentId", ((RecordRef)result.getRecords().get(0).getAttribute("parent", foo)).getId());
        Set<RecordRef> resultAssocs = (Set<RecordRef>)result.getRecords().get(0).getAttribute("associations", foo);
        Assert.assertEquals("assocId", (resultAssocs.iterator().next()).getId());
    }

}
