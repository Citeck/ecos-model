package ru.citeck.ecos.model.permissions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.domain.permissions.dto.*;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionsRepository;
import ru.citeck.ecos.model.domain.permissions.service.AttributesPermissionsService;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.rest.QueryBody;
import ru.citeck.ecos.records2.resolver.RemoteRecordsResolver;
import ru.citeck.ecos.records2.rest.RemoteRecordsRestApi;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosModelApp.class)
public class AttrPermissionsSyncRecordsDaoTest {

    private static final String SOURCE_ID = "emodel/attrs_permission";
    private static final int TOTAL_TYPES = 100;

    @Autowired
    private RecordsServiceFactory remoteServiceFactory;
    @Autowired
    private AttributesPermissionsService service;
    @Autowired
    private TypeService typeService;
    @Autowired
    private AttributesPermissionsRepository repository;
    @Autowired
    private TypeRepository typeRepository;

    private RecordsService localRecordsService;
    private RemoteSyncRecordsDao<AttributesPermissionDto> remoteSyncRecordsDao;

    private final List<AttributesPermissionDto> permissions = new ArrayList<>();

    @Before
    public void setup() {

        repository.deleteAll();
        typeRepository.deleteAll();

        RecordsServiceFactory localFactory = new RecordsServiceFactory() {
            @Override
            protected RemoteRecordsResolver createRemoteRecordsResolver() {
                return new RemoteRecordsResolver(this, new RemoteRecordsRestApi() {
                    @Override
                    public <T> T jsonPost(String url, Object request, Class<T> respType) {
                        @SuppressWarnings("unchecked")
                        T res = (T) remoteServiceFactory.getRestHandler().queryRecords(
                            Objects.requireNonNull(Json.getMapper().convert(request, QueryBody.class))
                        );
                        return Json.getMapper().convert(res, respType);
                    }
                });
            }
        };

        this.localRecordsService = localFactory.getRecordsService();

        remoteSyncRecordsDao = new RemoteSyncRecordsDao<>(SOURCE_ID, AttributesPermissionDto.class);
        localRecordsService.register(remoteSyncRecordsDao);

        generateData();
    }

    @After
    public void afterTest() {
        repository.deleteAll();
        typeRepository.deleteAll();
    }

    @Test
    public void test() {

        RecordsQuery query = new RecordsQuery();
        query.setSourceId(SOURCE_ID);
        query.setQuery(VoidPredicate.INSTANCE);
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);
        query.setMaxItems(100);

        RecordsQueryResult<RecordRef> result = localRecordsService.queryRecords(query);
        assertEquals(TOTAL_TYPES , result.getTotalCount());
        assertEquals(TOTAL_TYPES, remoteSyncRecordsDao.getRecords().size());

        AttributesPermissionDto origDto = permissions.stream().filter(v -> v.getId().equals("att-perm-id-1")).findFirst().orElse(null);
        AttributesPermissionDto dto = localRecordsService.getMeta(RecordRef.valueOf(SOURCE_ID + "@att-perm-id-1"), AttributesPermissionDto.class);

        assertEquals(origDto, dto);

        assert origDto != null;
        Predicate predicate = Predicates.eq("typeRef", origDto.getTypeRef());
        RecordsQuery query1 = new RecordsQuery();
        query1.setLanguage(PredicateService.LANGUAGE_PREDICATE);
        query1.setSourceId(SOURCE_ID);
        query1.setQuery(predicate);

        RecordsQueryResult<RecordRef> recs = localRecordsService.queryRecords(query1);
        assertEquals(0, recs.getErrors().size());
        assertEquals(1, recs.getRecords().size());
        assertEquals(RecordRef.valueOf(SOURCE_ID + "@att-perm-id-1"), recs.getRecords().get(0));

        RecordsQueryResult<AttributesPermissionDto> resultWithMeta = localRecordsService.queryRecords(query1, AttributesPermissionDto.class);
        assertEquals(0, resultWithMeta.getErrors().size());
        assertEquals(1, resultWithMeta.getRecords().size());

        System.out.println(resultWithMeta.getRecords());

        Set<AttributesPermissionDto> expectedSet = new TreeSet<>(Comparator.comparing(AttributesPermissionDto::getId));
        expectedSet.addAll(permissions);

        Set<AttributesPermissionDto> actualSet = new TreeSet<>(Comparator.comparing(AttributesPermissionDto::getId));
        actualSet.addAll(remoteSyncRecordsDao.getRecords().values());

        assertEquals(expectedSet, new HashSet<>(actualSet
                .stream()
                .collect(Collectors.toList())
        ));
    }

    void generateData() {

        TypeDto base = new TypeDto();
        base.setId("base");
        base.setName(new MLText("base"));
        typeService.save(base);

        for (int i = 0; i < TOTAL_TYPES; i++) {

            AttributesPermissionDto dto = new AttributesPermissionDto();

            dto.setId("att-perm-id-" + i);

            TypeDto type = new TypeDto();
            type.setId("atype-id-" + i);
            type.setName(new MLText("atype-id-" + i));
            type.setParent(RecordRef.valueOf("emodel/type@base"));
            typeService.save(type);

            dto.setTypeRef(RecordRef.valueOf("emodel/type@atype-id-" + i));

            RuleDto rule = new RuleDto();
            rule.setCondition(ObjectData.create("{\"att\":\"t:conditionAttr\",\"val\":\"1000000\",\"t\":\"gt\"}"));
            rule.setAttributes(Collections.singletonList(
                    new AttributeDto("t:test", new PermissionsDto(false, false))));

            dto.setRules(Collections.singletonList(rule));

            service.save(dto);

            permissions.add(dto);
        }

        TypeDto typeNotFound = new TypeDto();
        typeNotFound.setId("not-f");
        typeNotFound.setName(new MLText("not-f"));
        typeNotFound.setParent(RecordRef.valueOf("emodel/type@atype-id-0"));
        typeService.save(typeNotFound);
    }
}
