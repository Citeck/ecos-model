package ru.citeck.ecos.model.domain.secret.service

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EventsEmitter
import ru.citeck.ecos.model.domain.secret.repo.EcosSecretEntity
import ru.citeck.ecos.model.domain.secret.repo.EcosSecretRepo
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.secrets.lib.secret.EcosSecret
import ru.citeck.ecos.secrets.lib.secret.EcosSecretImpl
import ru.citeck.ecos.secrets.lib.secret.EcosSecretType
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.lib.secret.event.SecretChangedEvent
import ru.citeck.ecos.webapp.lib.secret.provider.ModelEcosSecretsProvider
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.lang.IllegalArgumentException
import java.util.concurrent.CopyOnWriteArrayList

@Service
@Transactional
class EcosSecretService(
    private val repo: EcosSecretRepo,
    private val predicateJpaService: JpaSearchConverterFactory,
    private val modelSecretsProvider: ModelEcosSecretsProvider,
    private val eventsService: EventsService
) {

    private lateinit var searchConverter: JpaSearchConverter<EcosSecretEntity>
    private lateinit var secretChangedEventEmitter: EventsEmitter<SecretChangedEvent>

    private val onChangeListeners = CopyOnWriteArrayList<(EcosSecretDto) -> Unit>()

    @PostConstruct
    fun init() {
        searchConverter = predicateJpaService.createConverter(EcosSecretEntity::class.java)
            .withDefaultPageSize(10000)
            .build()
        modelSecretsProvider.setCustomSecretResolver(this::getSecret)
        secretChangedEventEmitter = eventsService.getEmitter {
            withEventClass(SecretChangedEvent::class.java)
            withEventType(SecretChangedEvent.EVENT_ID)
            withSource("ecos-model.secrets-service")
        }
    }

    fun getSecret(id: String): EcosSecret? {

        if (!AuthContext.isRunAsSystem()) {
            error("Permission denied")
        }

        val entity = repo.findByExtId(id) ?: return null
        val type = entity.type ?: return null
        val data = entity.data ?: return null

        if (type.isBlank() || data.isEmpty()) {
            return null
        }

        val secretType = try {
            EcosSecretType.valueOf(type)
        } catch (_: IllegalArgumentException) {
            return null
        }

        return EcosSecretImpl(id, secretType, data)
    }

    fun getById(id: String): EcosSecretDto? {
        return repo.findByExtId(id)?.let { mapToDto(it) }
    }

    fun getByIdWithMeta(id: String): EntityWithMeta<EcosSecretDto>? {
        return repo.findByExtId(id)?.let { mapToDtoWithMeta(it) }
    }

    fun delete(id: String) {
        repo.deleteByExtId(id)
    }

    fun find(
        max: Int,
        skip: Int,
        predicate: Predicate,
        sort: List<SortBy>
    ): List<EntityWithMeta<EcosSecretDto>> {
        return searchConverter.findAll(
            repo,
            preProcessPredicate(predicate),
            max,
            skip,
            sort
        ).map { mapToDtoWithMeta(it) }
    }

    private fun preProcessPredicate(predicate: Predicate): Predicate {
        return PredicateUtils.mapAttributePredicates(
            predicate
        ) {
            if (it.getAttribute() == "data") {
                Predicates.alwaysFalse()
            } else {
                it
            }
        } ?: Predicates.alwaysFalse()
    }

    fun save(dto: EcosSecretDto): EcosSecretDto {
        if (dto.id.isBlank()) {
            error("Secret id is empty")
        }
        if (!AuthContext.isRunAsSystemOrAdmin()) {
            error("Permission denied. You can't change secret '${dto.id}'")
        }
        val entity = mapToEntity(dto)
        val entityAfterSave = repo.save(entity)
        val dtoAfterSave = mapToDto(entityAfterSave)
        TxnContext.doAfterCommit(0f, false) {
            secretChangedEventEmitter.emit(SecretChangedEvent(dto.id))
            onChangeListeners.forEach { it.invoke(dtoAfterSave) }
        }
        return dtoAfterSave
    }

    fun getCount(predicate: Predicate): Long {
        return searchConverter.getCount(repo, preProcessPredicate(predicate))
    }

    fun listenLocalChanges(listener: (EcosSecretDto) -> Unit) {
        onChangeListeners.add(listener)
    }

    private fun mapToEntity(dto: EcosSecretDto): EcosSecretEntity {
        val entity = repo.findByExtId(dto.id) ?: EcosSecretEntity.create(dto.id)
        entity.type = dto.type
        entity.name = Json.mapper.toStringNotNull(dto.name)
        if (dto.data != null && dto.data.isNotEmpty()) {
            entity.data = Json.mapper.toBytesNotNull(dto.data)
        }
        return entity
    }

    private fun mapToDtoWithMeta(entity: EcosSecretEntity): EntityWithMeta<EcosSecretDto> {
        return EntityWithMeta(
            mapToDto(entity),
            EntityMeta(
                entity.createdDate,
                entity.createdBy,
                entity.lastModifiedDate,
                entity.lastModifiedBy,
            )
        )
    }

    private fun mapToDto(entity: EcosSecretEntity): EcosSecretDto {
        return EcosSecretDto(
            entity.extId,
            Json.mapper.read(entity.name, MLText::class.java) ?: MLText(),
            entity.type ?: "",
            null
        )
    }
}
