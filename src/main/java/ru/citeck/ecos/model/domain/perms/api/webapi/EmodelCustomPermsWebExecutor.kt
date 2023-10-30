package ru.citeck.ecos.model.domain.perms.api.webapi

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.perms.api.CustomRecordPermsApiImpl
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutor
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorReq
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorResp
import ru.citeck.ecos.webapp.lib.perms.component.custom.CustomRecordPerms
import ru.citeck.ecos.webapp.lib.perms.component.custom.CustomRecordPermsEmodelWebApi

@Component
class EmodelCustomPermsWebExecutor(
    private val service: CustomRecordPermsApiImpl
) : EcosWebExecutor {

    override fun execute(request: EcosWebExecutorReq, response: EcosWebExecutorResp) {

        val record = request.getHeaders().get(CustomRecordPermsEmodelWebApi.HEADER_REC) ?: ""

        val respData = if (record.isBlank()) {
            CustomRecordPerms.EMPTY
        } else {
            service.getPerms(record.toEntityRef())
        }
        response.getBodyWriter().writeDto(respData)
    }

    override fun getPath(): String {
        return CustomRecordPermsEmodelWebApi.PATH
    }

    override fun isReadOnly(): Boolean {
        return true
    }
}
