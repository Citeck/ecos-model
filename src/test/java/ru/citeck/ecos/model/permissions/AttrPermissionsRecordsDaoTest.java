package ru.citeck.ecos.model.permissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension;
import ru.citeck.ecos.model.domain.permissions.dto.AttributeDto;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.dto.PermissionsDto;
import ru.citeck.ecos.model.domain.permissions.dto.RuleDto;
import ru.citeck.ecos.model.domain.permissions.api.records.AttributesPermissionRecordsDao;
import ru.citeck.ecos.model.domain.permissions.service.AttributesPermissionsService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(EcosSpringExtension.class)
@SpringBootTest(classes = EcosModelApp.class)
public class AttrPermissionsRecordsDaoTest {

    @MockBean
    private AttributesPermissionsService service;
    @Autowired
    private PredicateService predicateService;

    private AttributesPermissionRecordsDao recordsDao;

    private List<RecordRef> recordRefs;
    private RecordsQuery recordsQuery;
    private AttributesPermissionWithMetaDto metaDto;
    private MetaField metaField;

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
                new AttributeDto("t:testProp", new PermissionsDto(false, false))));

        metaDto = new AttributesPermissionWithMetaDto();
        metaDto.setId("test_attrs_permission");
        metaDto.setTypeRef(RecordRef.create("type", "type"));
        metaDto.setRules(Collections.singletonList(rule));
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefs() throws Exception {

        when(service.getById(metaDto.getId())).thenReturn(Optional.of(metaDto));
        when(service.getByIdOrNull(metaDto.getId())).thenReturn(metaDto);

        List<MetaValue> resultRecords = recordsDao.getLocalRecordsMeta(recordRefs, Mockito.any());

        assertEquals(resultRecords.size(), 1);

        MetaValue resultRecord = resultRecords.get(0);

        assertEquals(metaDto.getId(), resultRecord.getId());
        assertEquals(metaDto.getId(), resultRecord.getAttribute("extId", metaField));
        assertEquals(metaDto.getRules(), resultRecord.getAttribute("rules", metaField));
        assertEquals(metaDto.getTypeRef(), resultRecord.getAttribute("typeRef", metaField));
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefsWithEmptyID() throws Exception {

        List<MetaValue> resultRecords = recordsDao.getLocalRecordsMeta(
                Collections.singletonList(RecordRef.create("attrs_permission", "")), metaField);

        Mockito.verify(service, Mockito.times(0)).getById(Mockito.anyString());

        assertEquals(resultRecords.size(), 1);

        MetaValue resultRecord = resultRecords.get(0);

        assertNull(resultRecord.getId());
        assertNull(resultRecord.getAttribute("extId", metaField));
        assertNull(resultRecord.getAttribute("typeRef", metaField));
        assertEquals(resultRecord.getAttribute("rules", metaField), Collections.EMPTY_LIST);
    }

    @Test
    void testQueryLocalRecords() {

        when(service.getAll(Collections.singleton(metaDto.getId()))).thenReturn(Collections.singleton(metaDto));
        when(service.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(metaDto));

        RecordsQueryResult<AttributesPermissionRecordsDao.AttributesPermissionRecord> resultRecordsQueryResult = recordsDao
                .queryLocalRecords(recordsQuery, metaField);

        assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        AttributesPermissionRecordsDao.AttributesPermissionRecord resultRecord = resultRecordsQueryResult.getRecords().get(0);

        assertEquals(metaDto.getId(), resultRecord.getAttribute("extId", metaField));
        assertEquals(metaDto.getRules(), resultRecord.getAttribute("rules", metaField));
        assertEquals(metaDto.getTypeRef(), resultRecord.getAttribute("typeRef", metaField));
    }

    @Test
    void testQueryLocalRecordsLanguageIsNotPredicate() {

        recordsQuery.setLanguage("");

        when(service.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(metaDto));

        RecordsQueryResult<AttributesPermissionRecordsDao.AttributesPermissionRecord> resultRecordsQueryResult = recordsDao
                .queryLocalRecords(recordsQuery, metaField);

        Mockito.verify(service, Mockito.times(0)).getAll(Mockito.anySet());

        assertEquals(resultRecordsQueryResult.getTotalCount(), 1);

        AttributesPermissionRecordsDao.AttributesPermissionRecord resultRecord = resultRecordsQueryResult.getRecords().get(0);

        assertEquals(metaDto.getId(), resultRecord.getAttribute("extId", metaField));
        assertEquals(metaDto.getRules(), resultRecord.getAttribute("rules", metaField));
        assertEquals(metaDto.getTypeRef(), resultRecord.getAttribute("typeRef", metaField));
    }
}
