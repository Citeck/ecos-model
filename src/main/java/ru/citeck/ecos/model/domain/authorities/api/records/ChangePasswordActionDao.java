package ru.citeck.ecos.model.domain.authorities.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.service.keycloak.KeycloakUserService;
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChangePasswordActionDao implements ValueMutateDao<ChangePasswordActionDao.ChangeInfoDto> {

    private final KeycloakUserService keycloakUserService;

    @NotNull
    @Override
    public String getId() {
        return "change-password";
    }

    @Nullable
    @Override
    public Object mutate(@NotNull ChangeInfoDto changeInfoDto) throws Exception {

        String userName = changeInfoDto.getUserName();
        String newPassword = changeInfoDto.getNewPassword();

        keycloakUserService.updateUserPassword(userName, newPassword);

        return "";
    }

    @Data
    static class ChangeInfoDto {
        private String userName;
        private String newPassword;
    }
}
