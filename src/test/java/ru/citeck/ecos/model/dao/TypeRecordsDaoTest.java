package ru.citeck.ecos.model.dao;

import graphql.language.Field;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.impl.TypeServiceImpl;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.PredicateServiceImpl;
import ru.citeck.ecos.predicate.model.Predicate;
import ru.citeck.ecos.predicate.model.ValuePredicate;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.field.MetaFieldImpl;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.RecordElements;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TypeRecordsDaoTest {

    @MockBean
    private TypeServiceImpl typeService;

    @MockBean
    private PredicateServiceImpl predicateService;

    @MockBean
    private RecordsService recordsService;

    private TypeRecordsDao typeRecordsDao;

    private List<RecordRef> recordRefs;
    private RecordsQuery recordsQuery;
    private TypeDto typeDto;
    private MetaField metaField;
    private Predicate predicate;

    @BeforeEach
    void setUp() {
        typeRecordsDao = new TypeRecordsDao(typeService, predicateService, recordsService);
        typeRecordsDao.setRecordsServiceFactory(new RecordsServiceFactory());

        recordRefs = Collections.singletonList(
            RecordRef.create("type", "type")
        );

        recordsQuery = new RecordsQuery();
        recordsQuery.setQuery("query");
        recordsQuery.setLanguage(PredicateService.LANGUAGE_PREDICATE);

        TypeAssociationDto associationDto = new TypeAssociationDto();
        associationDto.setId("association");

        typeDto = new TypeDto();
        typeDto.setId("type");
        typeDto.setName("name");
        typeDto.setTenant("tenant");
        typeDto.setDescription("desc");
        typeDto.setParent(RecordRef.create("type","parent"));
        typeDto.setInheritActions(false);
        typeDto.setAssociations(Collections.singleton(associationDto));
        typeDto.setActions(Collections.singleton(ModuleRef.create("ui/action", "action")));

        metaField = new MetaFieldImpl(new Field(""));

        predicate = new ValuePredicate();

    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefs() {

        //  arrange
        when(typeService.getByExtId(typeDto.getId())).thenReturn(typeDto);

        //  act
        List<TypeRecordsDao.TypeRecord> resultTypeRecords = typeRecordsDao.getLocalRecordsMeta(recordRefs, Mockito.any());

        //  assert
        Assert.assertEquals(resultTypeRecords.size(), 1);
        TypeRecordsDao.TypeRecord resultTypeRecord = resultTypeRecords.get(0);
        Assert.assertEquals(resultTypeRecord.getId(), typeDto.getId());
        Assert.assertEquals(resultTypeRecord.getAttribute("name", metaField), typeDto.getName());
        Assert.assertEquals(resultTypeRecord.getAttribute("description", metaField), typeDto.getDescription());
        Assert.assertEquals(resultTypeRecord.getAttribute("tenant", metaField), typeDto.getTenant());
        Assert.assertEquals(resultTypeRecord.getAttribute("extId", metaField), typeDto.getId());
        Assert.assertEquals(resultTypeRecord.getAttribute("inheritActions", metaField), typeDto.isInheritActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("parent", metaField), typeDto.getParent());
        Assert.assertEquals(resultTypeRecord.getAttribute("actions", metaField), typeDto.getActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("associations", metaField), typeDto.getAssociations());
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefsWithEmptyID() {

        //  act
        List<TypeRecordsDao.TypeRecord> resultTypeRecords = typeRecordsDao.getLocalRecordsMeta(
            Collections.singletonList(RecordRef.create("type", "")), metaField);

        //  assert
        Mockito.verify(typeService, Mockito.times(0)).getByExtId(Mockito.anyString());
        Assert.assertEquals(resultTypeRecords.size(), 1);
        TypeRecordsDao.TypeRecord resultTypeRecord = resultTypeRecords.get(0);
        Assert.assertNull(resultTypeRecord.getId());
        Assert.assertNull(resultTypeRecord.getAttribute("name", metaField));
        Assert.assertNull(resultTypeRecord.getAttribute("description", metaField));
        Assert.assertNull(resultTypeRecord.getAttribute("tenant", metaField));
        Assert.assertNull(resultTypeRecord.getAttribute("extId", metaField));
        Assert.assertEquals(resultTypeRecord.getAttribute("inheritActions", metaField), false);
        Assert.assertNull(resultTypeRecord.getAttribute("parent", metaField));
        Assert.assertEquals(resultTypeRecord.getAttribute("actions", metaField), Collections.emptySet());
        Assert.assertEquals(resultTypeRecord.getAttribute("associations", metaField), Collections.emptySet());
    }

    @Test
    void testQueryLocalRecords() {

        //  arrange
        when(predicateService.readJson(recordsQuery.getQuery())).thenReturn(predicate);
        when(predicateService.filter(Mockito.any(RecordElements.class), Mockito.eq(predicate)))
            .thenReturn(Collections.singletonList(new RecordElement(recordsService, RecordRef.create("type", "type"))));
        when(typeService.getAll(Collections.singleton(typeDto.getId()))).thenReturn(Collections.singleton(typeDto));

        //  act
        RecordsQueryResult<TypeRecordsDao.TypeRecord> resultRecordsQueryResult = typeRecordsDao.queryLocalRecords(recordsQuery, metaField);

        //  assert
        Assert.assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        TypeRecordsDao.TypeRecord resultTypeRecord = resultRecordsQueryResult.getRecords().get(0);
        Assert.assertEquals(resultTypeRecord.getAttribute("name", metaField), "name");
        Assert.assertEquals(resultTypeRecord.getAttribute("description", metaField), "desc");
        Assert.assertEquals(resultTypeRecord.getAttribute("tenant", metaField), "tenant");
        Assert.assertEquals(resultTypeRecord.getAttribute("extId", metaField), typeDto.getId());
        Assert.assertEquals(resultTypeRecord.getAttribute("inheritActions", metaField), typeDto.isInheritActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("parent", metaField), typeDto.getParent());
        Assert.assertEquals(resultTypeRecord.getAttribute("actions", metaField), typeDto.getActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("associations", metaField), typeDto.getAssociations());
    }

    @Test
    void testQueryLocalRecordsLanguageIsNotPredicate() {

        //  arrange
        recordsQuery.setLanguage("");
        when(typeService.getAll()).thenReturn(Collections.singleton(typeDto));

        //  act
        RecordsQueryResult<TypeRecordsDao.TypeRecord> resultRecordsQueryResult = typeRecordsDao.queryLocalRecords(recordsQuery, metaField);

        //  assert
        Mockito.verify(predicateService, Mockito.times(0)).readJson(Mockito.anyString());
        Mockito.verify(predicateService, Mockito.times(0)).filter(Mockito.any(), Mockito.any());
        Mockito.verify(typeService, Mockito.times(0)).getAll(Mockito.anySet());
        Assert.assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        TypeRecordsDao.TypeRecord resultTypeRecord = resultRecordsQueryResult.getRecords().get(0);
        Assert.assertEquals(resultTypeRecord.getAttribute("name", metaField), "name");
        Assert.assertEquals(resultTypeRecord.getAttribute("description", metaField), "desc");
        Assert.assertEquals(resultTypeRecord.getAttribute("tenant", metaField), "tenant");
        Assert.assertEquals(resultTypeRecord.getAttribute("extId", metaField), typeDto.getId());
        Assert.assertEquals(resultTypeRecord.getAttribute("inheritActions", metaField), typeDto.isInheritActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("parent", metaField), typeDto.getParent());
        Assert.assertEquals(resultTypeRecord.getAttribute("actions", metaField), typeDto.getActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("associations", metaField), typeDto.getAssociations());
    }
}
