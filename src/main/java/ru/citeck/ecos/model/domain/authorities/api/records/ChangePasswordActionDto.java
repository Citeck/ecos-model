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
        String userName = changeInfoDto.getUserName();
        String userNewPassword = changeInfoDto.getNewPassword();

        keycloakUserService.updateUserPassword(userName, userNewPassword);

        return "";
    }

}
@Data
class ChangeInfoDto {
    private String userName;
    private String newPassword;
}
