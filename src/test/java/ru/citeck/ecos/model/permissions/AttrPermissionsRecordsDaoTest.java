package ru.citeck.ecos.model.permissions;

import org.apache.groovy.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.webapp.api.constants.AppName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension;
import ru.citeck.ecos.model.domain.permissions.dto.AttributeDto;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.dto.PermissionsDto;
import ru.citeck.ecos.model.domain.permissions.dto.RuleDto;
import ru.citeck.ecos.model.domain.permissions.api.records.AttributesPermissionRecordsDao;
import ru.citeck.ecos.model.domain.permissions.service.AttributesPermissionsService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(EcosSpringExtension.class)
@SpringBootTest(classes = EcosModelApp.class)
public class AttrPermissionsRecordsDaoTest {

    @MockitoBean
    private AttributesPermissionsService service;
    @Autowired
    private RecordsService recordsService;

    private List<EntityRef> recordRefs;
    private RecordsQuery recordsQuery;
    private AttributesPermissionWithMetaDto metaDto;

    @BeforeEach
    void setUp() {

        recordRefs = Collections.singletonList(
            EntityRef.create("attrs_permission", "test_attrs_permission")
        );

        recordsQuery = RecordsQuery.create()
            .withSourceId(AttributesPermissionRecordsDao.ID)
            .withQuery(Predicates.alwaysTrue())
            .build();

        RuleDto rule = new RuleDto();
        rule.setAttributes(Collections.singletonList(
                new AttributeDto("t:testProp", new PermissionsDto(false, false))));

        metaDto = new AttributesPermissionWithMetaDto();
        metaDto.setId("test_attrs_permission");
        metaDto.setTypeRef(EntityRef.create(AppName.EMODEL, "type", "type"));
        metaDto.setRules(Collections.singletonList(rule));
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefs() throws Exception {

        when(service.getById(metaDto.getId())).thenReturn(Optional.of(metaDto));
        when(service.getByIdOrNull(metaDto.getId())).thenReturn(metaDto);

        var atts = recordsService.getAtts(recordRefs, Maps.of(
            "extId", "extId",
            "rules", "rules[]?json",
            "typeRef", "typeRef?id"
        ));

        assertEquals(atts.size(), 1);

        RecordAtts resultRecord = atts.get(0);

        assertEquals(metaDto.getId(), resultRecord.getId().getLocalId());
        assertEquals(metaDto.getId(), resultRecord.getAtt("extId").asText());
        assertEquals(metaDto.getRules(), resultRecord.getAtt("rules").asList(RuleDto.class));
        assertEquals(metaDto.getTypeRef().toString(), resultRecord.getAtt("typeRef").asText());
    }

    @Test
    void testGetLocalRecordsMetaFromRecordRefsWithEmptyID() throws Exception {

        var atts = recordsService.getAtts(EntityRef.create("attrs_permission", ""), Maps.of(
            "extId", "extId",
            "rules", "rules[]?json",
            "typeRef", "typeRef?id"
        ));

        Mockito.verify(service, Mockito.times(0)).getById(Mockito.anyString());

        assertThat(atts.getId().getLocalId()).isEmpty();
        assertThat(atts.getAtt("extId").asText()).isEmpty();
        assertThat(atts.getAtt("typeRef").asText()).isEmpty();
        assertThat(atts.getAtt("rules").asList(RuleDto.class)).isEmpty();
    }

    @Test
    void testQueryLocalRecords() {

        when(service.getAll(Collections.singleton(metaDto.getId()))).thenReturn(Collections.singleton(metaDto));
        when(service.getAll(Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Predicate.class), Mockito.anyList()))
            .thenReturn(Collections.singletonList(metaDto));

        var queryRes = recordsService.query(recordsQuery, Maps.of(
            "extId", "extId",
            "rules", "rules[]?json",
            "typeRef", "typeRef?id"
        ));

        assertEquals(queryRes.getTotalCount(), 1);
        RecordAtts resultRecord = queryRes.getRecords().getFirst();

        assertEquals(metaDto.getId(), resultRecord.getAtt("extId").asText());
        assertEquals(metaDto.getRules(), resultRecord.getAtt("rules").asList(RuleDto.class));
        assertEquals(metaDto.getTypeRef().toString(), resultRecord.getAtt("typeRef").asText());
    }

    @Test
    void testQueryLocalRecordsLanguageIsNotPredicate() {

        var query = recordsQuery.copy().withLanguage("").build();

        when(service.getAll(Mockito.anyInt(), Mockito.anyInt())).thenReturn(Collections.singletonList(metaDto));

        var recsQueryRes = recordsService.query(query, Maps.of(
            "extId", "extId",
            "rules", "rules[]?json",
            "typeRef", "typeRef?id"
        ));

        Mockito.verify(service, Mockito.times(0)).getAll(Mockito.anySet());

        assertEquals(recsQueryRes.getTotalCount(), 1);

        var resultRecord = recsQueryRes.getRecords().get(0);

        assertEquals(metaDto.getId(), resultRecord.getAtt("extId").asText());
        assertEquals(metaDto.getRules(), resultRecord.getAtt("rules").asList(RuleDto.class));
        assertEquals(metaDto.getTypeRef().toString(), resultRecord.getAtt("typeRef").asText());
    }
}
