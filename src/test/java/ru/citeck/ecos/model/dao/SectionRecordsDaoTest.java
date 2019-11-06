package ru.citeck.ecos.model.dao;

import graphql.language.Field;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.service.impl.SectionServiceImpl;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.PredicateServiceImpl;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.field.MetaFieldImpl;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
public class SectionRecordsDaoTest {

    @Mock
    private SectionServiceImpl sectionService;

    @Mock
    private PredicateServiceImpl predicateService;

    private SectionRecordsDao recordsDao;

    @BeforeEach
    public void setUp() throws Exception {
        recordsDao = new SectionRecordsDao(sectionService, predicateService);
        RecordsServiceFactory factory = new RecordsServiceFactory();
        recordsDao.setRecordsServiceFactory(factory);
    }

    @Test
    public void getMetaValuesReturnRecords() {
        Set<RecordRef> types = Collections.singleton(RecordRef.create(TypeRecordsDao.ID,"typeId"));
        SectionDto dto = new SectionDto("extId", "a", "adesc","atenant", types);
        Set<SectionDto> dtos = Collections.singleton(dto);
        RecordsQuery query = new RecordsQuery();

        given(sectionService.getAll()).willReturn(dtos);


        RecordsQueryResult<SectionRecordsDao.SectionRecord> result = recordsDao.getMetaValues(query);


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
        Set<RecordRef> types = Collections.singleton(RecordRef.create(TypeRecordsDao.ID,"typeId"));
        SectionDto dto = new SectionDto("extId", "a", "adesc","atenant", types);
        RecordsQuery query = new RecordsQuery();
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);

        RecordElement element = new RecordElement(null, RecordRef.create("", TypeRecordsDao.ID, "extId"));

        given(predicateService.filter(Mockito.any(), Mockito.any())).willReturn(Arrays.asList(element));
        given(sectionService.getAll(Collections.singleton("extId"))).willReturn(Collections.singleton(dto));


        RecordsQueryResult<SectionRecordsDao.SectionRecord> result = recordsDao.getMetaValues(query);


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

}
