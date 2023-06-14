package ru.citeck.ecos.model.action;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.service.keycloak.KeycloakUserService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao;
import ru.citeck.ecos.webapp.api.content.EcosContentApi;


@Component
@Slf4j
public class ChangePassAction implements ValueMutateDao<ChangeInfoDto> {

    private final RecordsService recordsService;
    private final KeycloakUserService keycloakUserService;


    @Autowired
    public ChangePassAction(RecordsService recordsService, EcosContentApi contentApi, KeycloakUserService keycloakUserService) {
        this.recordsService = recordsService;
        this.keycloakUserService = keycloakUserService;
    }

    @NotNull
    @Override
    public String getId() {
        return "change-pass";
    }

    @Nullable
    @Override
    public Object mutate(@NotNull ChangeInfoDto changeInfoDto) throws Exception {
        RecordRef record = changeInfoDto.getRecordRef();
        String newpass = changeInfoDto.getNewpass();

        String username = recordsService.getAtt(record, "id").asText();
        keycloakUserService.updateUserPassword(username, newpass);

        return "";
    }

}
@Data
class ChangeInfoDto {
    private RecordRef recordRef;
    private String newpass;
}
