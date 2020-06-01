package ru.citeck.ecos.model.num;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.num.dto.NumTemplateDto;
import ru.citeck.ecos.model.num.repository.NumTemplateRepository;
import ru.citeck.ecos.model.num.service.NumTemplateService;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.rest.QueryBody;
import ru.citeck.ecos.records2.resolver.RemoteRecordsResolver;
import ru.citeck.ecos.records2.rest.RemoteRecordsRestApi;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDAO;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosModelApp.class)
public class NumTemplateSyncRecordsDaoTest {

    private static final String SOURCE_ID = "emodel/num-template";
    private static final int TOTAL_TEMPLATES = 101;

    @Autowired
    private RecordsServiceFactory remoteServiceFactory;
    @Autowired
    private NumTemplateService numTemplateService;
    @Autowired
    private NumTemplateRepository numTemplateRepository;

    private RecordsService localRecordsService;
    private RemoteSyncRecordsDAO<NumTemplateDto> remoteSyncRecordsDAO;

    private final List<NumTemplateDto> templates = new ArrayList<>();

    @Autowired
    private TypeService typeService;

    @Before
    public void setup() {

        numTemplateRepository.deleteAll();

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

        remoteSyncRecordsDAO = new RemoteSyncRecordsDAO<>(SOURCE_ID, NumTemplateDto.class);
        localRecordsService.register(remoteSyncRecordsDAO);

        generateData();
    }

    @Test
    public void test() {

        RecordsQuery query = new RecordsQuery();
        query.setSourceId(SOURCE_ID);
        query.setQuery(VoidPredicate.INSTANCE);
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);
        query.setMaxItems(1000);

        RecordsQueryResult<RecordRef> result = localRecordsService.queryRecords(query);
        assertEquals(TOTAL_TEMPLATES, result.getTotalCount());

        NumTemplateDto dto = localRecordsService.getMeta(RecordRef.valueOf(SOURCE_ID + "@template-id-100"), NumTemplateDto.class);
        NumTemplateDto origDto = templates.stream().filter(v -> v.getId().equals("template-id-100")).findFirst().orElse(null);

        assertEquals(origDto, normalize(dto));

        DataValue att = remoteServiceFactory.getRecordsService().getAttribute(RecordRef.valueOf(SOURCE_ID + "@template-id-100"), "modelAttributes[]");
        assertEquals(new ArrayList<>(Collections.singletonList("some-att")), new ArrayList<>(att.toStrList()));

        assertEquals(new HashSet<>(templates), new HashSet<>(
            remoteSyncRecordsDAO.getRecords()
                .values()
                .stream()
                .map(this::normalize)
                .collect(Collectors.toList())
        ));
    }

    NumTemplateDto normalize(NumTemplateDto dto) {
        return dto;
    }

    void generateData() {

        TypeDto base = new TypeDto();
        base.setId("base");
        base.setName(new MLText("base"));
        typeService.save(base);

        TypeDto numTmpltType = new TypeDto();
        numTmpltType.setId("number-template");
        numTmpltType.setName(new MLText("number-template"));
        typeService.save(numTmpltType);

        for (int i = 0; i < TOTAL_TEMPLATES; i++) {

            NumTemplateDto numTemplateDto = new NumTemplateDto();

            numTemplateDto.setId("template-id-" + i);

            numTemplateDto.setName("name-" + i);
            numTemplateDto.setCounterKey("counter${some-att}-key-" + i);

            templates.add(numTemplateDto);
            numTemplateService.save(numTemplateDto);
        }
    }
}
