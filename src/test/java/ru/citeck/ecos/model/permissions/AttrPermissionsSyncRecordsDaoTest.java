package ru.citeck.ecos.model.permissions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.test.commons.EcosWebAppApiMock;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.domain.permissions.dto.*;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionsRepository;
import ru.citeck.ecos.model.domain.permissions.service.AttributesPermissionsService;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.model.type.service.TypesService;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.webapp.api.EcosWebAppApi;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EcosSpringExtension.class)
@SpringBootTest(classes = EcosModelApp.class)
public class AttrPermissionsSyncRecordsDaoTest {

    private static final String SOURCE_ID = "emodel/attrs_permission";
    private static final int TOTAL_TYPES = 100;

    @Autowired
    private RecordsServiceFactory remoteServiceFactory;
    @Autowired
    private AttributesPermissionsService service;
    @Autowired
    private TypesService typeService;
    @Autowired
    private AttributesPermissionsRepository repository;
    @Autowired
    private TypeRepository typeRepository;

    private RecordsService localRecordsService;
    private RecordsServiceFactory localServiceFactory;
    private RemoteSyncRecordsDao<AttributesPermissionDto> remoteSyncRecordsDao;

    private final List<AttributesPermissionDto> permissions = new ArrayList<>();

    @BeforeEach
    public void setup() {

        repository.deleteAll();
        typeRepository.deleteAll();

        EcosWebAppApiMock webAppCtxMock = new EcosWebAppApiMock();
        webAppCtxMock.setWebClientExecuteImpl((targetApp, path, request) ->
            remoteServiceFactory.getRestHandlerAdapter().queryRecords(
                Json.getMapper().convertNotNull(request, ObjectNode.class),
                2
            )
        );
        localServiceFactory = new RecordsServiceFactory() {
            public EcosWebAppApi getEcosWebAppApi() {
                return webAppCtxMock;
            }
        };

        this.localRecordsService = localServiceFactory.getRecordsService();

        generateData();

        remoteSyncRecordsDao = new RemoteSyncRecordsDao<>(SOURCE_ID, AttributesPermissionDto.class);
        localRecordsService.register(remoteSyncRecordsDao);
    }

    @AfterEach
    public void afterTest() {
        repository.deleteAll();
        typeRepository.deleteAll();
    }

    @Test
    public void test() {

        RecordsQuery query = RecordsQuery.create()
            .withSourceId(SOURCE_ID)
            .withQuery(VoidPredicate.INSTANCE)
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withMaxItems(100)
            .build();

        RecsQueryRes<EntityRef> result = localRecordsService.query(query);
        assertEquals(TOTAL_TYPES , result.getTotalCount());
        assertEquals(TOTAL_TYPES, remoteSyncRecordsDao.getRecords().size());

        AttributesPermissionDto origDto = permissions.stream().filter(v -> v.getId().equals("att-perm-id-1")).findFirst().orElse(null);
        AttributesPermissionDto dto = localRecordsService.getAtts(EntityRef.valueOf(SOURCE_ID + "@att-perm-id-1"), AttributesPermissionDto.class);

        assertEquals(origDto, dto);

        assert origDto != null;
        Predicate predicate = Predicates.eq("typeRef?id", origDto.getTypeRef());
        RecordsQuery query1 = RecordsQuery.create()
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withSourceId(SOURCE_ID)
            .withQuery(predicate)
            .build();

        RecsQueryRes<EntityRef> recs = RequestContext.doWithCtx(localServiceFactory, ctx -> {
            RecsQueryRes<EntityRef> res = localRecordsService.query(query1);
            assertEquals(0, ctx.getErrors().size());
            return res;
        });
        assertEquals(1, recs.getRecords().size());
        assertEquals(EntityRef.valueOf(SOURCE_ID + "@att-perm-id-1"), recs.getRecords().get(0));

        RecsQueryRes<AttributesPermissionDto> resultWithMeta = RequestContext.doWithCtx(localServiceFactory, ctx -> {
            RecsQueryRes<AttributesPermissionDto> res = localRecordsService.query(query1, AttributesPermissionDto.class);
            assertEquals(0, ctx.getErrors().size());
            return res;
        });
        assertEquals(1, resultWithMeta.getRecords().size());

        System.out.println(resultWithMeta.getRecords());

        Set<AttributesPermissionDto> expectedSet = new TreeSet<>(Comparator.comparing(AttributesPermissionDto::getId));
        expectedSet.addAll(permissions);

        Set<AttributesPermissionDto> actualSet = new TreeSet<>(Comparator.comparing(AttributesPermissionDto::getId));
        actualSet.addAll(remoteSyncRecordsDao.getRecords().values());

        assertEquals(expectedSet, new HashSet<>(new ArrayList<>(actualSet)));
    }

    void generateData() {

        TypeDef.Builder base = TypeDef.create();
        base.withId("base");
        base.withName(new MLText("base"));
        typeService.save(base.build());

        for (int i = 0; i < TOTAL_TYPES; i++) {

            AttributesPermissionDto dto = new AttributesPermissionDto();

            dto.setId("att-perm-id-" + i);

            TypeDef.Builder type = TypeDef.create();
            type.withId("atype-id-" + i);
            type.withName(new MLText("atype-id-" + i));
            type.withParentRef(EntityRef.valueOf("emodel/type@base"));
            typeService.save(type.build());

            dto.setTypeRef(EntityRef.valueOf("emodel/type@atype-id-" + i));

            RuleDto rule = new RuleDto();
            rule.setRoles(Collections.emptyList());
            rule.setStatuses(Collections.emptyList());
            rule.setCondition(ObjectData.create("{\"att\":\"t:conditionAttr\",\"val\":\"1000000\",\"t\":\"gt\"}"));
            rule.setAttributes(Collections.singletonList(
                    new AttributeDto("t:test", new PermissionsDto(false, false))));

            dto.setRules(Collections.singletonList(rule));

            service.save(dto);

            permissions.add(dto);
        }

        TypeDef.Builder typeNotFound = TypeDef.create();
        typeNotFound.withId("not-f");
        typeNotFound.withName(new MLText("not-f"));
        typeNotFound.withParentRef(EntityRef.valueOf("emodel/type@atype-id-0"));

        typeService.save(typeNotFound.build());
    }
}
