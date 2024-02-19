package ru.citeck.ecos.model.domain.status.api.records;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StatusRecordsDao extends LocalRecordsDao implements LocalRecordsMetaDao<Object>, LocalRecordsQueryDao {

    //todo
    private Map<String, Object> statuses = new HashMap<String, Object>(){{
        put("new", new StatusDto("new", Json.getMapper().convert("{\"ru\":\"Новый\",\"en\":\"New\"}", MLText.class)));
        put("draft", new StatusDto("draft", Json.getMapper().convert("{\"ru\":\"Черновик\",\"en\":\"Draft\"}", MLText.class)));
        put("approve", new StatusDto("approve", Json.getMapper().convert("{\"ru\":\"Согласование\",\"en\":\"Approve\"}", MLText.class)));
        put("archive", new StatusDto("archive", Json.getMapper().convert("{\"ru\":\"Архив\",\"en\":\"Archive\"}", MLText.class)));
    }};

    @NotNull
    @Override
    public RecordsQueryResult<EntityRef> queryLocalRecords(@NotNull RecordsQuery recordsQuery) {
        return new RecordsQueryResult<>(statuses.keySet().stream()
            .map(s -> RecordRef.create(EcosModelApp.NAME, getId(), s))
            .collect(Collectors.toList()));
    }

    @NotNull
    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<EntityRef> list, @NotNull MetaField metaField) {
        return list.stream().map(EntityRef::getLocalId).map(
            id -> statuses.getOrDefault(id, EmptyValue.INSTANCE)
        ).collect(Collectors.toList());
    }

    @Override
    public String getId() {
        return "status";
    }

    @Data
    @AllArgsConstructor
    public static class StatusDto {
        private String id;
        private MLText name;
    }
}
