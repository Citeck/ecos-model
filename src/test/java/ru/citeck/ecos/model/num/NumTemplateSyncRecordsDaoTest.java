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
import ru.citeck.ecos.model.type.dto.TypeDef;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;
import ru.citeck.ecos.records2.rest.RemoteRecordsRestApi;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.records3.record.resolver.RemoteRecordsResolver;

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
    private RecordsServiceFactory localServiceFactory;
    private RemoteSyncRecordsDao<NumTemplateDto> remoteSyncRecordsDAO;

    private final List<NumTemplateDto> templates = new ArrayList<>();

    @Autowired
    private TypeService typeService;

    @Before
    public void setup() {

        numTemplateRepository.deleteAll();

        localServiceFactory = new RecordsServiceFactory() {
            @Override
            protected RemoteRecordsResolver createRemoteRecordsResolver() {
                return new RemoteRecordsResolver(this, new RemoteRecordsRestApi() {
                    @Override
                    public <T> T jsonPost(String url, Object request, Class<T> respType) {
                        @SuppressWarnings("unchecked")
                        T res = (T) remoteServiceFactory.getRestHandlerAdapter().queryRecords(request);
                        return Json.getMapper().convert(res, respType);
                    }
                });
            }
        };

        this.localRecordsService = localServiceFactory.getRecordsServiceV1();

        remoteSyncRecordsDAO = new RemoteSyncRecordsDao<>(SOURCE_ID, NumTemplateDto.class);
        localRecordsService.register(remoteSyncRecordsDAO);

        generateData();
    }

    @Test
    public void test() {

        RecordsQuery query = RecordsQuery.create()
            .withSourceId(SOURCE_ID)
            .withQuery(VoidPredicate.INSTANCE)
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withMaxItems(1000)
            .build();

        RecsQueryRes<RecordRef> result = localRecordsService.query(query);
        assertEquals(TOTAL_TEMPLATES, result.getTotalCount());

        NumTemplateDto dto = localRecordsService.getAtts(RecordRef.valueOf(SOURCE_ID + "@template-id-100"), NumTemplateDto.class);
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

        TypeDef.Builder base = TypeDef.create();
        base.withId("base");
        base.withName(new MLText("base"));
        typeService.save(base.build());

        TypeDef.Builder numTmpltType = TypeDef.create();
        numTmpltType.withId("number-template");
        numTmpltType.withName(new MLText("number-template"));
        typeService.save(numTmpltType.build());

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
