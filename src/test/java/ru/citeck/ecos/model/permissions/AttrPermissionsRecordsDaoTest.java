package ru.citeck.ecos.model.permissions;

import graphql.language.Field;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.domain.permissions.dto.AttributeDto;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.dto.PermissionsDto;
import ru.citeck.ecos.model.domain.permissions.dto.RuleDto;
import ru.citeck.ecos.model.domain.permissions.api.records.AttributesPermissionRecordsDao;
import ru.citeck.ecos.model.domain.permissions.service.AttributesPermissionsService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.graphql.meta.value.field.MetaFieldImpl;
import ru.citeck.ecos.records2.predicate.PredicateServiceImpl;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.RecordElements;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class AttrPermissionsRecordsDaoTest {

    @MockBean
    private AttributesPermissionsService service;

    @MockBean
    private PredicateServiceImpl predicateService;

    @MockBean
    private RecordsService recordsService;

    private AttributesPermissionRecordsDao recordsDao;

    private List<RecordRef> recordRefs;
    private RecordsQuery recordsQuery;
    private AttributesPermissionWithMetaDto metaDto;
    private MetaField metaField;
    private Predicate predicate;

    @BeforeEach
    void setUp() {

        recordsDao = new AttributesPermissionRecordsDao(service, predicateService);
        recordsDao.setRecordsServiceFactory(new RecordsServiceFactory());

        recordRefs = Collections.singletonList(
            RecordRef.create("attrs_permission", "test_attrs_permission")
        );

        recordsQuery = new RecordsQuery();
        recordsQuery.setQuery("query");
        recordsQuery.setLanguage("query-lang");

        RuleDto rule = new RuleDto();
        rule.setAttributes(Collections.singletonList(
                new AttributeDto("cm:name", new PermissionsDto(true, true))));

        metaDto = new AttributesPermissionWithMetaDto();
        metaDto.setId("test_attrs_permission");
        metaDto.setTypeRef(RecordRef.create("type", "type"));
        metaDto.setRules(Collections.singletonList(rule));

        metaField = new MetaFieldImpl(new Field(""));

        predicate = new ValuePredicate();

    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefs() throws Exception {

        when(service.getById(metaDto.getId())).thenReturn(Optional.of(metaDto));
        when(service.getByIdOrNull(metaDto.getId())).thenReturn(metaDto);

        List<MetaValue> resultRecords = recordsDao.getLocalRecordsMeta(recordRefs, Mockito.any());

        Assert.assertEquals(resultRecords.size(), 1);
        MetaValue resultRecord = resultRecords.get(0);
        Assert.assertEquals(resultRecord.getId(), metaDto.getId());
        Assert.assertEquals(resultRecord.getAttribute("extId", metaField), metaDto.getId());
        Assert.assertEquals(resultRecord.getAttribute("typeRef", metaField), metaDto.getTypeRef());
        Assert.assertEquals(resultRecord.getAttribute("rules", metaField), metaDto.getRules());
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefsWithEmptyID() throws Exception {

        List<MetaValue> resultRecords = recordsDao.getLocalRecordsMeta(
                Collections.singletonList(RecordRef.create("attrs_permission", "")), metaField);

        Mockito.verify(service, Mockito.times(0)).getById(Mockito.anyString());

        Assert.assertEquals(resultRecords.size(), 1);
        MetaValue resultRecord = resultRecords.get(0);
        Assert.assertNull(resultRecord.getId());
        Assert.assertNull(resultRecord.getAttribute("extId", metaField));
        Assert.assertNull(resultRecord.getAttribute("typeRef", metaField));
        Assert.assertEquals(resultRecord.getAttribute("rules", metaField), Collections.EMPTY_LIST);
    }

    @Test
    void testQueryLocalRecords() {

        when(predicateService.filter(Mockito.any(RecordElements.class), Mockito.eq(predicate)))
                .thenReturn(Collections.singletonList(new RecordElement(recordsService,
                        RecordRef.create("attrs_permission", "test_attrs_permission"))));
        when(service.getAll(Collections.singleton(metaDto.getId()))).thenReturn(Collections.singleton(metaDto));
        when(service.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(metaDto));

        RecordsQueryResult<AttributesPermissionRecordsDao.AttributesPermissionRecord> resultRecordsQueryResult = recordsDao
                .queryLocalRecords(recordsQuery, metaField);

        Assert.assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        AttributesPermissionRecordsDao.AttributesPermissionRecord resultRecord = resultRecordsQueryResult.getRecords().get(0);
        Assert.assertEquals(resultRecord.getAttribute("extId", metaField), metaDto.getId());
        Assert.assertEquals(resultRecord.getAttribute("typeRef", metaField), metaDto.getTypeRef());
        Assert.assertEquals(resultRecord.getAttribute("rules", metaField), metaDto.getRules());
    }

    @Test
    void testQueryLocalRecordsLanguageIsNotPredicate() {

        recordsQuery.setLanguage("");
        when(service.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(metaDto));

        RecordsQueryResult<AttributesPermissionRecordsDao.AttributesPermissionRecord> resultRecordsQueryResult = recordsDao
                .queryLocalRecords(recordsQuery, metaField);

        //  assert
        Mockito.verify(predicateService, Mockito.times(0)).filter(Mockito.any(), Mockito.any());
        Mockito.verify(service, Mockito.times(0)).getAll(Mockito.anySet());
        Assert.assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        AttributesPermissionRecordsDao.AttributesPermissionRecord resultRecord = resultRecordsQueryResult.getRecords().get(0);

        Assert.assertEquals(resultRecord.getAttribute("extId", metaField), metaDto.getId());
        Assert.assertEquals(resultRecord.getAttribute("rules", metaField), metaDto.getRules());
        Assert.assertEquals(resultRecord.getAttribute("typeRef", metaField), metaDto.getTypeRef());
    }
}
