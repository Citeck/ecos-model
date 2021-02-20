package ru.citeck.ecos.model.type;

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
import ru.citeck.ecos.model.association.dto.AssocDirection;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef;
import ru.citeck.ecos.model.type.dto.TypeDto;
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
import ru.citeck.ecos.records3.record.query.dto.RecsQueryRes;
import ru.citeck.ecos.records3.record.query.dto.query.RecordsQuery;
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

    private RemoteSyncRecordsDao<TypeDto> remoteSyncRecordsDao;

    private final List<TypeDto> types = new ArrayList<>();

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

        remoteSyncRecordsDao = new RemoteSyncRecordsDao<>(TYPES_SOURCE_ID, TypeDto.class);
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

        TypeDto dto = localRecordsService.getAtts(RecordRef.valueOf(TYPES_SOURCE_ID + "@type-id-100"), TypeDto.class);
        TypeDto origDto = types.stream().filter(v -> v.getId().equals("type-id-100")).findFirst().orElse(null);

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

        RecsQueryRes<TypeDto> resultWithMeta = RequestContext.doWithCtx(localServiceFactory, ctx -> {
            RecsQueryRes<TypeDto> res = localRecordsService.query(query1, TypeDto.class);
            assertEquals(0, ctx.getErrors().size());
            return res;
        });
        assertEquals(1, resultWithMeta.getRecords().size());

        assertEquals(origDto, normalize(resultWithMeta.getRecords().get(0)));

        Set<TypeDto> expectedSet = new TreeSet<>(Comparator.comparing(TypeDto::getId));
        expectedSet.addAll(types);

        Set<TypeDto> actualSet = new TreeSet<>(Comparator.comparing(TypeDto::getId));
        actualSet.addAll(remoteSyncRecordsDao.getRecords().values());

        assertEquals(expectedSet, new HashSet<>(actualSet
                .stream()
                .map(this::normalize)
                .collect(Collectors.toList())
        ));
    }

    TypeDto normalize(TypeDto dto) {

        List<AssociationDto> assocs = DataValue.create(dto.getAssociations()).toList(AssociationDto.class);
        assocs.sort(Comparator.comparing(AssociationDto::getId));
        dto.setAssociations(assocs);

        if (!Boolean.FALSE.equals(dto.getDefaultCreateVariant())) {
            dto.setDefaultCreateVariant(null);
        }

        return dto;
    }

    void generateData() {

        TypeDto base = new TypeDto();
        base.setId("base");
        base.setName(new MLText("base"));
        types.add(new TypeDto(typeService.save(base)));

        TypeDto type = new TypeDto();
        type.setId("type");
        type.setName(new MLText("type"));
        types.add(new TypeDto(typeService.save(type)));

        for (int i = 0; i < TOTAL_TYPES; i++) {

            TypeDto typeDto = new TypeDto();

            typeDto.setId("type-id-" + i);

            typeDto.setActions(Arrays.asList(
                RecordRef.valueOf("uiserv/action@action-" + i + "-0"),
                RecordRef.valueOf("uiserv/action@action-" + i + "-1")
            ));

            typeDto.setAssociations(generateAssocs(i));
            typeDto.setAttributes(ObjectData.create("{\"attKey\":\"attValue-" + i + "\"}"));
            typeDto.setConfig(ObjectData.create("{\"configKey\":\"configValue-" + i + "\"}"));
            typeDto.setConfigFormRef(RecordRef.create("uiserv", "eform", "config-form-" + i));
            typeDto.setCreateVariants(generateCreateVariants(i));
            typeDto.setDashboardType("card-details");
            typeDto.setDescription(new MLText("Description-" + i));
            typeDto.setForm(RecordRef.valueOf("uiserv/eform@form-" + i));
            typeDto.setJournal(RecordRef.valueOf("uiserv/journal@journal-" + i));
            typeDto.setSourceId("source-" + i);
            typeDto.setName(new MLText("name-" + i));
            typeDto.setParent(RecordRef.valueOf("emodel/type@base"));
            typeDto.setDispNameTemplate(DataValue.create("{\"ru\": \"Тест\"}").getAs(MLText.class));
            typeDto.setInheritNumTemplate(true);
            typeDto.setNumTemplateRef(RecordRef.valueOf("emodel/num-template@test"));
            typeDto.setPostCreateActionRef(RecordRef.valueOf("uiserv/action@post-create-action"));

            types.add(typeDto);
            typeService.save(typeDto);
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

    List<AssociationDto> generateAssocs(int idx) {

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
    }
}
