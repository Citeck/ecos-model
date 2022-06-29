package ru.citeck.ecos.model.domain.authorities.patch

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import java.util.concurrent.Callable

@Component
@EcosPatch("add-alf-admins-to-ecos-admins", "2022-06-29T00:00:00Z")
class AddAlfAdminsGroupToEcosAdminsPatch(
    val recordsService: RecordsService
) : Callable<String> {

    companion object {
        const val ALF_ADMINS_GROUP = "ALFRESCO_ADMINISTRATORS"
    }

    override fun call(): String {
        val alfAdminsRef = AuthorityType.GROUP.getRef(ALF_ADMINS_GROUP)
        val atts = recordsService.getAtts(alfAdminsRef, AlfAdminsGroupAtts::class.java)
        return if (atts.notExists == true) {
            "$ALF_ADMINS_GROUP doesn't exists. Patch will be skipped"
        } else if (atts.authorityGroups?.contains(AuthorityGroupConstants.ADMIN_GROUP) == true) {
            "$ALF_ADMINS_GROUP already in ${AuthorityGroupConstants.ADMIN_GROUP} group"
        } else {
            recordsService.mutateAtt(
                alfAdminsRef,
                "att_add_${AuthorityConstants.ATT_AUTHORITY_GROUPS}",
                AuthorityType.GROUP.getRef(AuthorityGroupConstants.ADMIN_GROUP)
            )
            "$ALF_ADMINS_GROUP was added to ${AuthorityGroupConstants.ADMIN_GROUP}"
        }
    }

    data class AlfAdminsGroupAtts(
        @AttName("authorityGroups[]?localId")
        val authorityGroups: List<String>?,
        @AttName(RecordConstants.ATT_NOT_EXISTS + "?bool")
        val notExists: Boolean?
    )
}
