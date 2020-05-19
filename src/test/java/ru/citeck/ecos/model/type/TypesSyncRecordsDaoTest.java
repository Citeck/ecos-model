package ru.citeck.ecos.model.type;

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
import ru.citeck.ecos.model.type.dto.CreateVariantDto;
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
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDAO;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;
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
    private RemoteSyncRecordsDAO<TypeDto> remoteSyncRecordsDAO;

    private final List<TypeDto> types = new ArrayList<>();

    @Before
    public void setup() {

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

        remoteSyncRecordsDAO = new RemoteSyncRecordsDAO<>(TYPES_SOURCE_ID, TypeDto.class);
        localRecordsService.register(remoteSyncRecordsDAO);

        generateData();

        localFactory.init();
    }

    @Test
    public void test() {

        RecordsQuery query = new RecordsQuery();
        query.setSourceId(TYPES_SOURCE_ID);
        query.setQuery(VoidPredicate.INSTANCE);
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);
        query.setMaxItems(1000);

        RecordsQueryResult<RecordRef> result = localRecordsService.queryRecords(query);
        assertEquals(TOTAL_TYPES + 1 /* +1 for base type */, result.getTotalCount());

        TypeDto dto = localRecordsService.getMeta(RecordRef.valueOf(TYPES_SOURCE_ID + "@type-id-100"), TypeDto.class);
        TypeDto origDto = types.stream().filter(v -> v.getId().equals("type-id-100")).findFirst().orElse(null);

        assertEquals(origDto, normalize(dto));

        assert origDto != null;
        Predicate predicate = Predicates.eq("attributes.attKey?str", origDto.getAttributes().get("attKey").asText());
        RecordsQuery query1 = new RecordsQuery();
        query1.setLanguage(PredicateService.LANGUAGE_PREDICATE);
        query1.setSourceId(TYPES_SOURCE_ID);
        query1.setQuery(predicate);

        RecordsQueryResult<RecordRef> recs = localRecordsService.queryRecords(query1);
        assertEquals(0, recs.getErrors().size());
        assertEquals(1, recs.getRecords().size());
        assertEquals(RecordRef.valueOf(TYPES_SOURCE_ID + "@type-id-100"), recs.getRecords().get(0));

        RecordsQueryResult<TypeDto> resultWithMeta = localRecordsService.queryRecords(query1, TypeDto.class);
        assertEquals(0, resultWithMeta.getErrors().size());
        assertEquals(1, resultWithMeta.getRecords().size());

        assertEquals(origDto, normalize(resultWithMeta.getRecords().get(0)));

        assertEquals(new HashSet<>(types), new HashSet<>(
            remoteSyncRecordsDAO.getRecords()
                .stream()
                .map(this::normalize)
                .collect(Collectors.toList())
        ));
    }

    TypeDto normalize(TypeDto dto) {
        List<AssociationDto> assocs = DataValue.create(dto.getAssociations()).toList(AssociationDto.class);
        assocs.sort(Comparator.comparing(AssociationDto::getId));
        dto.setAssociations(assocs);
        return dto;
    }

    void generateData() {

        TypeDto base = new TypeDto();
        base.setId("base");
        base.setName(new MLText("base"));
        typeService.save(base);
        types.add(base);

        for (int i = 0; i < TOTAL_TYPES; i++) {

            TypeDto typeDto = new TypeDto();

            typeDto.setId("type-id-" + i);

            typeDto.setActions(Arrays.asList(
                RecordRef.valueOf("action-" + i + "-0"),
                RecordRef.valueOf("action-" + i + "-1")
            ));

            typeDto.setAssociations(generateAssocs(i));
            typeDto.setAttributes(ObjectData.create("{\"attKey\":\"attValue-" + i + "\"}"));
            typeDto.setConfig(ObjectData.create("{\"configKey\":\"configValue-" + i + "\"}"));
            typeDto.setConfigForm(RecordRef.create("uiserv", "eform", "config-form-" + i));
            typeDto.setCreateVariants(generateCreateVariants(i));
            typeDto.setDashboardType("card-details");
            typeDto.setDescription(new MLText("Description-" + i));
            typeDto.setForm(RecordRef.valueOf("form-" + i));
            typeDto.setJournal(RecordRef.valueOf("journal-" + i));
            typeDto.setSourceId("source-" + i);
            typeDto.setName(new MLText("name-" + i));
            typeDto.setParent(RecordRef.valueOf("emodel/type@base"));

            types.add(typeDto);
            typeService.save(typeDto);
        }
    }

    List<CreateVariantDto> generateCreateVariants(int idx) {

        List<CreateVariantDto> result = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            CreateVariantDto dto = new CreateVariantDto();
            dto.setId("cv-" + idx + "-" + i);
            dto.setName(new MLText("Assoc " + idx + "-" + i));
            dto.setAttributes(ObjectData.create("{\"cvKey\":\"cvValue-" + idx + "-" + i + "\"}"));
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
