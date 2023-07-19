package ru.citeck.ecos.model.domain.secret.api.webapi

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.secrets.lib.EcosSecrets
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp
import ru.citeck.ecos.webapp.lib.secret.provider.ModelEcosSecretsProvider

@Component
class GetEcosSecretWebExecutor : EcosWebExecutor {

    override fun execute(request: EcosWebExecutorReq, response: EcosWebExecutorResp) {
        val reqBody = request.getBodyReader().readDto(ModelEcosSecretsProvider.SecretRequestBody::class.java)
        val secret = EcosSecrets.getSecretOrNull(reqBody.secretId)
        val respBody = if (secret == null) {
            createEmptyResp()
        } else {
            val data = ObjectData.create(secret.getData())
            if (data.isEmpty()) {
                createEmptyResp()
            } else {
                ModelEcosSecretsProvider.SecretResponseBody(
                    secret.getType(),
                    ObjectData.create(data)
                )
            }
        }
        response.getBodyWriter().writeDto(respBody)
    }

    private fun createEmptyResp(): ModelEcosSecretsProvider.SecretResponseBody {
        return ModelEcosSecretsProvider.SecretResponseBody(null, ObjectData.create())
    }

    override fun getPath(): String {
        return ModelEcosSecretsProvider.SECRET_GET_PATH
    }

    override fun isReadOnly(): Boolean {
        return true
    }
}
