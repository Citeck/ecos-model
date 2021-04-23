package ru.citeck.ecos.model.domain.type;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef;
import ru.citeck.ecos.model.type.dto.TypeDef;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;
import ru.citeck.ecos.records2.rest.RemoteRecordsRestApi;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.records3.record.resolver.RemoteRecordsResolver;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosModelApp.class)
public class TypesSyncRecordsDaoTest {

    private static final String TYPES_SOURCE_ID = "emodel/type";
    private static final int TOTAL_TYPES = 101;

    @Autowired
    private RecordsServiceFactory remoteServiceFactory;
    @Autowired
    private TypeService typeService;
    @Autowired
    private TypeRepository typeRepository;

    private RecordsService localRecordsService;
    private RecordsServiceFactory localServiceFactory;

    private RemoteSyncRecordsDao<TypeDef> remoteSyncRecordsDao;

    private final List<TypeDef> types = new ArrayList<>();

    @Before
    public void setup() {

        typeRepository.deleteAll();

        localServiceFactory = new RecordsServiceFactory() {
            @Override
            protected RemoteRecordsResolver createRemoteRecordsResolver() {
                return new RemoteRecordsResolver(this, new RemoteRecordsRestApi() {
                    @Override
                    public <T> T jsonPost(String url, Object request, Class<T> respType) {
                        @SuppressWarnings("unchecked")
                        T res = (T) remoteServiceFactory.getRestHandlerAdapter().queryRecords(
                            Objects.requireNonNull(request)
                        );
                        return Json.getMapper().convert(res, respType);
                    }
                });
            }
        };

        this.localRecordsService = localServiceFactory.getRecordsServiceV1();

        remoteSyncRecordsDao = new RemoteSyncRecordsDao<>(TYPES_SOURCE_ID, TypeDef.class);
        localRecordsService.register(remoteSyncRecordsDao);

        generateData();
    }

    @After
    public void afterTest() {
        typeRepository.deleteAll();
    }

    @Test
    public void test() {

        RecordsQuery query = RecordsQuery.create()
            .withSourceId(TYPES_SOURCE_ID)
            .withQuery(VoidPredicate.INSTANCE)
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withMaxItems(1000)
            .build();

        RecsQueryRes<RecordRef> result = localRecordsService.query(query);
        assertEquals(TOTAL_TYPES + 2 /* +1 for base and type types */, result.getTotalCount());
        assertEquals(TOTAL_TYPES + 2, remoteSyncRecordsDao.getRecords().size());

        TypeDef dto = localRecordsService.getAtts(RecordRef.valueOf(TYPES_SOURCE_ID + "@type-id-100"), TypeDef.class);
        TypeDef origDto = types.stream().filter(v -> v.getId().equals("type-id-100")).findFirst().orElse(null);

        assertEquals(origDto, normalize(dto));

        assert origDto != null;
        Predicate predicate = Predicates.eq("properties.attKey?str", origDto.getProperties().get("attKey").asText());
        RecordsQuery query1 = RecordsQuery.create()
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withSourceId(TYPES_SOURCE_ID)
            .withQuery(predicate)
            .build();

        RecsQueryRes<RecordRef> recs = RequestContext.doWithCtx(localServiceFactory, ctx -> {
            RecsQueryRes<RecordRef> res = localRecordsService.query(query1);
            assertEquals(0, ctx.getErrors().size());
            return res;
        });

        assertEquals(1, recs.getRecords().size());
        assertEquals(RecordRef.valueOf(TYPES_SOURCE_ID + "@type-id-100"), recs.getRecords().get(0));

        RecsQueryRes<TypeDef> resultWithMeta = RequestContext.doWithCtx(localServiceFactory, ctx -> {
            RecsQueryRes<TypeDef> res = localRecordsService.query(query1, TypeDef.class);
            assertEquals(0, ctx.getErrors().size());
            return res;
        });
        assertEquals(1, resultWithMeta.getRecords().size());

        assertEquals(origDto, normalize(resultWithMeta.getRecords().get(0)));

        Set<TypeDef> expectedSet = new TreeSet<>(Comparator.comparing(TypeDef::getId));
        expectedSet.addAll(types);

        Set<TypeDef> actualSet = new TreeSet<>(Comparator.comparing(TypeDef::getId));
        actualSet.addAll(remoteSyncRecordsDao.getRecords().values());

        assertEquals(expectedSet, new HashSet<>(actualSet
                .stream()
                .map(this::normalize)
                .collect(Collectors.toList())
        ));
    }

    TypeDef normalize(TypeDef dto) {

     /*   List<AssociationDto> assocs = DataValue.create(dto.getAssociations()).toList(AssociationDto.class);
        assocs.sort(Comparator.comparing(AssociationDto::getId));
        dto.setAssociations(assocs);

        if (!Boolean.FALSE.equals(dto.getDefaultCreateVariant())) {
            dto.setDefaultCreateVariant(null);
        }
*/
        return dto;
    }

    void generateData() {

        TypeDef.Builder base = TypeDef.create();
        base.withId("base");
        base.withName(new MLText("base"));
        types.add(typeService.save(base.build()));

        TypeDef.Builder type = TypeDef.create();
        type.withId("type");
        type.withName(new MLText("type"));
        types.add(typeService.save(type.build()));

        for (int i = 0; i < TOTAL_TYPES; i++) {

            TypeDef.Builder typeDto = TypeDef.create();

            typeDto.withId("type-id-" + i);

            typeDto.setActions(Arrays.asList(
                RecordRef.valueOf("uiserv/action@action-" + i + "-0"),
                RecordRef.valueOf("uiserv/action@action-" + i + "-1")
            ));

            //typeDto.setAssociations(generateAssocs(i));
            typeDto.withProperties(ObjectData.create("{\"attKey\":\"attValue-" + i + "\"}"));
            typeDto.setConfig(ObjectData.create("{\"configKey\":\"configValue-" + i + "\"}"));
            typeDto.withConfigFormRef(RecordRef.create("uiserv", "eform", "config-form-" + i));
            typeDto.withCreateVariants(generateCreateVariants(i));
            typeDto.withDashboardType("card-details");
            typeDto.withDescription(new MLText("Description-" + i));
            typeDto.withFormRef(RecordRef.valueOf("uiserv/form@form-" + i));
            typeDto.withJournalRef(RecordRef.valueOf("uiserv/journal@journal-" + i));
            typeDto.withSourceId("source-" + i);
            typeDto.withName(new MLText("name-" + i));
            typeDto.withParentRef(RecordRef.valueOf("emodel/type@base"));
            typeDto.withDispNameTemplate(DataValue.create("{\"ru\": \"Тест\"}").getAs(MLText.class));
            typeDto.withInheritNumTemplate(true);
            typeDto.withNumTemplateRef(RecordRef.valueOf("emodel/num-template@test"));
            typeDto.withPostCreateActionRef(RecordRef.valueOf("uiserv/action@post-create-action"));

            TypeDef typeDef = typeDto.build();
            types.add(typeDef);
            typeService.save(typeDef);
        }
    }

    List<CreateVariantDef> generateCreateVariants(int idx) {

        List<CreateVariantDef> result = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            CreateVariantDef dto = CreateVariantDef.create()
                .withId("cv-" + idx + "-" + i)
                .withName(new MLText("Assoc " + idx + "-" + i))
                .withAttributes(ObjectData.create("{\"cvKey\":\"cvValue-" + idx + "-" + i + "\"}"))
                .build();
            result.add(dto);
        }
        return result;
    }

   /* List<AssociationDto> generateAssocs(int idx) {

        List<AssociationDto> result = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AssociationDto dto = new AssociationDto();
            dto.setId("assoc-" + idx + "-" + i);
            dto.setDirection(AssocDirection.BOTH);
            dto.setName(new MLText("Assoc " + idx + "-" + i));
            dto.setTarget(RecordRef.create("emodel", "type", "base"));
            result.add(dto);
        }
        return result;
    }*/
}
