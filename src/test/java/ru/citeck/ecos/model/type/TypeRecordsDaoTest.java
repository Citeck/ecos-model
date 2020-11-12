package ru.citeck.ecos.model.type;

import graphql.language.Field;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.service.impl.TypeServiceImpl;
import ru.citeck.ecos.model.type.records.dao.TypeRecordsDao;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
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
    private TypeWithMetaDto typeDto;
    private MetaField metaField;
    private Predicate predicate;

    @BeforeEach
    void setUp() {

        typeRecordsDao = new TypeRecordsDao(typeService);
        typeRecordsDao.setRecordsServiceFactory(new RecordsServiceFactory());

        recordRefs = Collections.singletonList(
            RecordRef.create("type", "type")
        );

        recordsQuery = new RecordsQuery();
        recordsQuery.setQuery("query");
        recordsQuery.setLanguage("query-lang");

        AssociationDto associationDto = new AssociationDto();
        associationDto.setId("association");

        typeDto = new TypeWithMetaDto();
        typeDto.setId("type");
        typeDto.setName(new MLText("name"));
        typeDto.setDescription(new MLText("desc"));
        typeDto.setParent(RecordRef.create("type", "parent"));
        typeDto.setInheritActions(false);
        typeDto.setAssociations(Collections.singletonList(associationDto));
        typeDto.setActions(Collections.singletonList(RecordRef.create("uiserv", "action", "action")));

        metaField = new MetaFieldImpl(new Field(""));

        predicate = new ValuePredicate();
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefs() throws Exception {

        //  arrange
        when(typeService.getByExtId(typeDto.getId())).thenReturn(typeDto);
        when(typeService.getOrCreateByExtId(typeDto.getId())).thenReturn(typeDto);
        when(typeService.getByExtIdOrNull(typeDto.getId())).thenReturn(typeDto);

        //  act
        List<MetaValue> resultTypeRecords = typeRecordsDao.getLocalRecordsMeta(recordRefs, Mockito.any());

        //  assert
        Assert.assertEquals(resultTypeRecords.size(), 1);
        MetaValue resultTypeRecord = resultTypeRecords.get(0);
        Assert.assertEquals(resultTypeRecord.getId(), typeDto.getId());
        Assert.assertEquals(resultTypeRecord.getAttribute("name", metaField), typeDto.getName());
        Assert.assertEquals(resultTypeRecord.getAttribute("description", metaField), typeDto.getDescription());
        Assert.assertEquals(resultTypeRecord.getAttribute("extId", metaField), typeDto.getId());
        Assert.assertEquals(resultTypeRecord.getAttribute("inheritActions", metaField), typeDto.isInheritActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("parent", metaField), typeDto.getParentRef());
        Assert.assertEquals(resultTypeRecord.getAttribute("actions", metaField), typeDto.getActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("associations", metaField), typeDto.getAssociations());
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefsWithEmptyID() throws Exception {

        //  act
        List<MetaValue> resultTypeRecords = typeRecordsDao.getLocalRecordsMeta(
            Collections.singletonList(RecordRef.create("type", "")), metaField);

        //  assert
        Mockito.verify(typeService, Mockito.times(0)).getByExtId(Mockito.anyString());
        Assert.assertEquals(resultTypeRecords.size(), 1);
        MetaValue resultTypeRecord = resultTypeRecords.get(0);
        Assert.assertNull(resultTypeRecord.getId());
        Assert.assertNull(resultTypeRecord.getAttribute("name", metaField));
        Assert.assertNull(resultTypeRecord.getAttribute("description", metaField));
        Assert.assertNull(resultTypeRecord.getAttribute("tenant", metaField));
        Assert.assertNull(resultTypeRecord.getAttribute("extId", metaField));
        Assert.assertEquals(resultTypeRecord.getAttribute("inheritActions", metaField), false);
        Assert.assertNull(resultTypeRecord.getAttribute("parent", metaField));
        Assert.assertEquals(resultTypeRecord.getAttribute("actions", metaField), Collections.emptyList());
        Assert.assertEquals(resultTypeRecord.getAttribute("associations", metaField), Collections.emptyList());
    }

    @Test
    void testQueryLocalRecords() {

        //  arrange
        when(predicateService.filter(Mockito.any(RecordElements.class), Mockito.eq(predicate)))
            .thenReturn(Collections.singletonList(new RecordElement(recordsService, RecordRef.create("type", "type"))));
        when(typeService.getAll(Collections.singleton(typeDto.getId()))).thenReturn(Collections.singleton(typeDto));
        when(typeService.getAll()).thenReturn(Collections.singleton(typeDto));
        when(typeService.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(typeDto));

        //  act
        RecordsQueryResult<TypeRecordsDao.TypeRecord> resultRecordsQueryResult = typeRecordsDao.queryLocalRecords(recordsQuery, metaField);

        //  assert
        Assert.assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        TypeRecordsDao.TypeRecord resultTypeRecord = resultRecordsQueryResult.getRecords().get(0);
        Assert.assertEquals(resultTypeRecord.getAttribute("name", metaField), new MLText("name"));
        Assert.assertEquals(resultTypeRecord.getAttribute("description", metaField), new MLText("desc"));
        Assert.assertEquals(resultTypeRecord.getAttribute("extId", metaField), typeDto.getId());
        Assert.assertEquals(resultTypeRecord.getAttribute("inheritActions", metaField), typeDto.isInheritActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("parent", metaField), typeDto.getParentRef());
        Assert.assertEquals(resultTypeRecord.getAttribute("actions", metaField), typeDto.getActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("associations", metaField), typeDto.getAssociations());
    }

    @Test
    void testQueryLocalRecordsLanguageIsNotPredicate() {

        //  arrange
        recordsQuery.setLanguage("");
        when(typeService.getAll()).thenReturn(Collections.singleton(typeDto));
        when(typeService.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(typeDto));

        //  act
        RecordsQueryResult<TypeRecordsDao.TypeRecord> resultRecordsQueryResult = typeRecordsDao.queryLocalRecords(recordsQuery, metaField);

        //  assert
        Mockito.verify(typeService, Mockito.times(0)).getAll(Mockito.anySet());
        Assert.assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        TypeRecordsDao.TypeRecord resultTypeRecord = resultRecordsQueryResult.getRecords().get(0);

        Assert.assertEquals(resultTypeRecord.getAttribute("name", metaField), new MLText("name"));
        Assert.assertEquals(resultTypeRecord.getAttribute("description", metaField), new MLText("desc"));
        Assert.assertEquals(resultTypeRecord.getAttribute("extId", metaField), typeDto.getId());
        Assert.assertEquals(resultTypeRecord.getAttribute("inheritActions", metaField), typeDto.isInheritActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("parent", metaField), typeDto.getParentRef());
        Assert.assertEquals(resultTypeRecord.getAttribute("actions", metaField), typeDto.getActions());
        Assert.assertEquals(resultTypeRecord.getAttribute("associations", metaField), typeDto.getAssociations());
    }
}
