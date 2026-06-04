package ru.citeck.ecos.model.type.service.utils

import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.util.function.Function

/**
 * Single source of truth for the set of workspace-scoped [EntityRef] fields of a [TypeDef].
 *
 * [rewrite] applies [transform] to every non-empty ws-scoped ref and returns a copy. The covered
 * set is: parentRef, formRef, journalRef, numTemplateRef, boardRef, configFormRef,
 * postCreateActionRef, actions[], associations[].target, associations[].journals[],
 * createVariants[].typeRef/formRef/postActionRef. Non-ref fields (incl. workspace) are left
 * untouched — callers set workspace separately.
 *
 * Every site that converts refs to/from the `CURRENT_WS:` placeholder routes through here so the
 * covered set cannot drift between export and the import paths:
 *  - [ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler] — deploy/import + listen/export;
 *  - `TypesRepoRecordsDao.getData` — `?data` YAML export;
 *  - `TypesRepoRecordsMutDao` — records-mutate / artifact-upload import.
 */
object TypeWorkspaceRefs {

    @JvmStatic
    fun rewrite(typeDef: TypeDef, transform: Function<EntityRef, EntityRef>): TypeDef {
        return typeDef.copy()
            .withParentRef(apply(typeDef.parentRef, transform))
            .withFormRef(apply(typeDef.formRef, transform))
            .withJournalRef(apply(typeDef.journalRef, transform))
            .withNumTemplateRef(apply(typeDef.numTemplateRef, transform))
            .withBoardRef(apply(typeDef.boardRef, transform))
            .withConfigFormRef(apply(typeDef.configFormRef, transform))
            .withPostCreateActionRef(apply(typeDef.postCreateActionRef, transform))
            .withActions(typeDef.actions.map { apply(it, transform) })
            .withAssociations(
                typeDef.associations.map { assoc ->
                    assoc.copy()
                        .withTarget(apply(assoc.target, transform))
                        .withJournals(assoc.journals.map { apply(it, transform) })
                        .build()
                }
            )
            .withCreateVariants(
                typeDef.createVariants.map { cv ->
                    cv.copy()
                        .withTypeRef(apply(cv.typeRef, transform))
                        .withFormRef(apply(cv.formRef, transform))
                        .withPostActionRef(apply(cv.postActionRef, transform))
                        .build()
                }
            )
            .build()
    }

    private fun apply(ref: EntityRef, transform: Function<EntityRef, EntityRef>): EntityRef {
        return if (EntityRef.isNotEmpty(ref)) transform.apply(ref) else ref
    }
}
