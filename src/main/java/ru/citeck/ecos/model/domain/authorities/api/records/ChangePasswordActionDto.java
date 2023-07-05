package ru.citeck.ecos.model.domain.authorities.api.records;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.service.keycloak.KeycloakUserService;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;


@Component
@Slf4j
public class ChangePasswordActionDto implements ValueMutateDao<ChangeInfoDto> {

    private final RecordsService recordsService;
    private final KeycloakUserService keycloakUserService;


    @Autowired
    public ChangePasswordActionDto(RecordsService recordsService, KeycloakUserService keycloakUserService) {
        this.recordsService = recordsService;
        this.keycloakUserService = keycloakUserService;
    }

    @NotNull
    @Override
    public String getId() {
        return "change-password";
    }

    @Nullable
    @Override
    public Object mutate(@NotNull ChangeInfoDto changeInfoDto) throws Exception {
        EntityRef userRef = changeInfoDto.getRecordRef();
        String userNewPassword = changeInfoDto.getNewPassword();

        String username = recordsService.getAtt(userRef, "id").asText();
        keycloakUserService.updateUserPassword(username, userNewPassword);

        return "";
    }

}
@Data
class ChangeInfoDto {
    private EntityRef recordRef;
    private String newPassword;
}
