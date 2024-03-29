package ru.citeck.ecos.model.records.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.section.records.dao.SectionRecordsDao;
import ru.citeck.ecos.model.section.records.record.SectionRecord;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.service.impl.SectionServiceImpl;
import ru.citeck.ecos.records2.predicate.PredicateServiceImpl;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.RecordElements;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(EcosSpringExtension.class)
@SpringBootTest(classes = EcosModelApp.class)
public class SectionRecordsDaoTest {

    @MockBean
    private SectionServiceImpl sectionService;

    @MockBean
    private PredicateServiceImpl predicateService;

    @MockBean
    private RecordsService recordsService;

    private SectionRecordsDao sectionRecordsDao;

    private List<EntityRef> recordRefs;
    private RecordsQuery recordsQuery;
    private Set<EntityRef> types;
    private SectionDto sectionDto;
    private MetaField metaField;
    private Predicate predicate;

    @BeforeEach
    void setUp() {
        sectionRecordsDao = new SectionRecordsDao(sectionService, predicateService);
        sectionRecordsDao.setRecordsServiceFactory(new RecordsServiceFactory());

        recordRefs = Arrays.asList(
            EntityRef.create("section", "section")
        );

        recordsQuery = new RecordsQuery();
        recordsQuery.setQuery("query");
        recordsQuery.setLanguage("query");

        types = Collections.singleton(
            EntityRef.create("type", "type")
        );

        sectionDto = new SectionDto();
        sectionDto.setId("section");
        sectionDto.setName(new MLText("name"));
        sectionDto.setTenant("tenant");
        sectionDto.setDescription("desc");
        sectionDto.setTypes(types);

        predicate = new ValuePredicate();
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefs() {

        //  arrange
        when(sectionService.getByExtId(sectionDto.getId())).thenReturn(sectionDto);

        //  act
        List<SectionRecord> resultSectionRecords = sectionRecordsDao.getLocalRecordsMeta(recordRefs, Mockito.any());

        //  assert
        assertEquals(resultSectionRecords.size(), 1);
        SectionRecord resultSectionRecord = resultSectionRecords.get(0);
        assertEquals(resultSectionRecord.getId(), sectionDto.getId());
        assertEquals(resultSectionRecord.getAttribute("name", metaField), sectionDto.getName());
        assertEquals(resultSectionRecord.getAttribute("description", metaField), sectionDto.getDescription());
        assertEquals(resultSectionRecord.getAttribute("tenant", metaField), sectionDto.getTenant());
        assertEquals(resultSectionRecord.getAttribute("types", metaField), sectionDto.getTypes());
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefsWithEmptyID() {

        //  act
        List<SectionRecord> resultSectionRecords = sectionRecordsDao.getLocalRecordsMeta(
            Collections.singletonList(RecordRef.create("section", "")), metaField);

        //  assert
        Mockito.verify(sectionService, Mockito.times(0)).getByExtId(Mockito.anyString());
        assertEquals(resultSectionRecords.size(), 1);
        SectionRecord resultSectionRecord = resultSectionRecords.get(0);
        assertNull(resultSectionRecord.getId());
        assertNull(resultSectionRecord.getAttribute("name", metaField));
        assertNull(resultSectionRecord.getAttribute("description", metaField));
        assertNull(resultSectionRecord.getAttribute("tenant", metaField));
        assertNull(resultSectionRecord.getAttribute("types", metaField));
    }

    @Test
    void testQueryLocalRecords() {

        //  arrange
        when(predicateService.filter(Mockito.any(RecordElements.class), Mockito.eq(predicate)))
            .thenReturn(Collections.singletonList(new RecordElement(recordsService, RecordRef.create("", "section"))));
        when(sectionService.getAll(Collections.singleton(sectionDto.getId()))).thenReturn(Collections.singleton(sectionDto));
        when(sectionService.getAll()).thenReturn(Collections.singleton(sectionDto));
        when(sectionService.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(sectionDto));

        //  act
        RecordsQueryResult<SectionRecord> resultRecordsQueryResult = sectionRecordsDao.queryLocalRecords(recordsQuery, metaField);

        //  assert
        assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        SectionRecord resultSectionRecord = resultRecordsQueryResult.getRecords().get(0);
        assertEquals(resultSectionRecord.getAttribute("name", metaField), new MLText("name"));
        assertEquals(resultSectionRecord.getAttribute("description", metaField), "desc");
        assertEquals(resultSectionRecord.getAttribute("tenant", metaField), "tenant");
        assertEquals(resultSectionRecord.getAttribute("types", metaField), types);
    }

    @Test
    void testQueryLocalRecordsLanguageIsNotPredicate() {

        //  arrange
        recordsQuery.setLanguage("");
        when(sectionService.getAll()).thenReturn(Collections.singleton(sectionDto));
        when(sectionService.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(sectionDto));

        //  act
        RecordsQueryResult<SectionRecord> resultRecordsQueryResult = sectionRecordsDao.queryLocalRecords(recordsQuery, metaField);

        //  assert
        Mockito.verify(sectionService, Mockito.times(0)).getAll(Mockito.anySet());
        assertEquals(resultRecordsQueryResult.getTotalCount(), 1);
        SectionRecord resultSectionRecord = resultRecordsQueryResult.getRecords().get(0);
        assertEquals(resultSectionRecord.getAttribute("name", metaField), new MLText("name"));
        assertEquals(resultSectionRecord.getAttribute("description", metaField), "desc");
        assertEquals(resultSectionRecord.getAttribute("tenant", metaField), "tenant");
        assertEquals(resultSectionRecord.getAttribute("types", metaField), types);
    }
}
