package ru.citeck.ecos.model.domain.endpoint.api.webapi

import org.springframework.stereotype.Component
import ru.citeck.ecos.endpoints.lib.EcosEndpoints
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp
import ru.citeck.ecos.webapp.lib.endpoint.provider.ModelEcosEndpointsProvider

@Component
class GetEcosEndpointWebExecutor : EcosWebExecutor {

    override fun execute(request: EcosWebExecutorReq, response: EcosWebExecutorResp) {
        val reqBody = request.getBodyReader().readDto(ModelEcosEndpointsProvider.EndpointRequestBody::class.java)
        val endpoint = EcosEndpoints.getEndpointOrNull(reqBody.endpointId)
        val respBody = if (endpoint == null) {
            createEmptyResp()
        } else {
            ModelEcosEndpointsProvider.EndpointResponseBody(endpoint.getUrl(), endpoint.getCredentialsId())
        }
        response.getBodyWriter().writeDto(respBody)
    }

    private fun createEmptyResp(): ModelEcosEndpointsProvider.EndpointResponseBody {
        return ModelEcosEndpointsProvider.EndpointResponseBody("", "")
    }

    override fun getPath(): String {
        return ModelEcosEndpointsProvider.ENDPOINT_GET_PATH
    }

    override fun isReadOnly(): Boolean {
        return true
    }
}
