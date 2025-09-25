package ru.citeck.ecos.model.num.service

import com.hazelcast.core.HazelcastInstance
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.utils.TmplUtils.applyAtts
import ru.citeck.ecos.commons.utils.TmplUtils.getAtts
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.num.domain.NumCounterEntity
import ru.citeck.ecos.model.num.domain.NumTemplateEntity
import ru.citeck.ecos.model.num.dto.NumTemplateDto
import ru.citeck.ecos.model.num.repository.EcosNumCounterRepository
import ru.citeck.ecos.model.num.repository.NumTemplateRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

@Slf4j
@Service
@RequiredArgsConstructor
class NumTemplateService(
    private val templateRepo: NumTemplateRepository,
    private val counterRepo: EcosNumCounterRepository,
    private val transactionManager: PlatformTransactionManager,
    private val hazelcast: HazelcastInstance,
    private val predicateToJpaConvFactory: JpaSearchConverterFactory,
    private val workspaceService: WorkspaceService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private lateinit var doInNewTxnTemplate: TransactionTemplate
    private lateinit var searchConverter: JpaSearchConverter<NumTemplateEntity>

    private val listeners: MutableList<(EntityWithMeta<NumTemplateDef>?, EntityWithMeta<NumTemplateDef>) -> Unit> =
        CopyOnWriteArrayList()

    @PostConstruct
    fun init() {
        searchConverter = predicateToJpaConvFactory.createConverter(NumTemplateEntity::class.java)
            .withDefaultPageSize(10000)
            .build()

        val txnDef = DefaultTransactionDefinition()
        txnDef.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        doInNewTxnTemplate = TransactionTemplate(transactionManager, txnDef)
    }

    @Transactional
    fun setNextNumber(templateRef: EntityRef, counterKey: String, nextNumber: Long) {
        val idInWs = workspaceService.convertToIdInWs(templateRef.getLocalId())
        var numTemplateEntity = templateRepo.findByWorkspaceAndExtId(idInWs.workspace, idInWs.id)
        if (numTemplateEntity == null) {
            numTemplateEntity = NumTemplateEntity()
            numTemplateEntity.extId = templateRef.getLocalId()
            numTemplateEntity.name = templateRef.getLocalId()
            numTemplateEntity.counterKey = counterKey
            numTemplateEntity = templateRepo.save<NumTemplateEntity>(numTemplateEntity)
        }

        var counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, counterKey)
        if (counterEntity == null) {
            counterEntity = NumCounterEntity()
            counterEntity.template = numTemplateEntity
            counterEntity.key = counterKey
        }
        counterEntity.counter = nextNumber - 1L
        counterRepo.save(counterEntity)
    }

    fun getAll(): List<EntityWithMeta<NumTemplateDef>> {
        return templateRepo.findAll().map(this::toDto)
    }

    fun getAll(max: Int, skip: Int): List<EntityWithMeta<NumTemplateDef>> {
        val sort = Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, sort)

        return templateRepo.findAll(page)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList())
    }

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: List<SortBy>): List<EntityWithMeta<NumTemplateDef>> {
        return searchConverter.findAll(templateRepo, predicate, max, skip, sort).stream()
            .map(this::toDto)
            .collect(Collectors.toList())
    }

    fun getCount(predicate: Predicate): Long {
        return searchConverter.getCount(templateRepo, predicate)
    }

    fun getCount(): Long {
        return templateRepo.count()
    }

    fun save(dto: NumTemplateDto): EntityWithMeta<NumTemplateDef> {

        val before = getByIdOrNull(IdInWs.create(dto.workspace, dto.id))

        var entity = toEntity(dto)
        entity = templateRepo.save(entity)
        val changedDto = toDto(entity)

        listeners.forEach { it(before, changedDto) }
        return changedDto
    }

    fun getByIdOrNull(id: IdInWs): EntityWithMeta<NumTemplateDef>? {
        val entity = templateRepo.findByWorkspaceAndExtId(id.workspace, id.id) ?: return null
        return toDto(entity)
    }

    fun getNextNumber(templateRef: EntityRef, model: ObjectData): Long {
        return getNextNumber(templateRef, model, true)
    }

    fun getNextNumber(templateRef: EntityRef, counterKey: String, increment: Boolean): Long {
        val numTemplateEntity = getNumTemplateEntity(templateRef)

        return getNextNumber(numTemplateEntity, counterKey, increment)
    }

    fun getNextNumber(templateRef: EntityRef, model: ObjectData, increment: Boolean): Long {
        val numTemplateEntity = getNumTemplateEntity(templateRef)
        val counterKey = applyAtts(numTemplateEntity.counterKey, model).asText()

        return getNextNumber(numTemplateEntity, counterKey, increment)
    }

    private fun getNumTemplateEntity(templateRef: EntityRef): NumTemplateEntity {
        val idInWs = workspaceService.convertToIdInWs(templateRef.getLocalId())
        val numTemplateEntity = templateRepo.findByWorkspaceAndExtId(idInWs.workspace, idInWs.id)
        requireNotNull(numTemplateEntity) { "Number template doesn't exists: $templateRef" }
        return numTemplateEntity
    }

    private fun getNextNumber(numTemplateEntity: NumTemplateEntity, counterKey: String, increment: Boolean): Long {

        if (!increment) {
            val counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, counterKey)
            return if (counterEntity == null) {
                1
            } else {
                counterEntity.counter + 1
            }
        }

        val lockingStartedAt = System.currentTimeMillis()

        val lock = hazelcast.cpSubsystem.getLock("num-tlt-" + numTemplateEntity.extId + "-" + counterKey)
        check(lock.tryLock(10, TimeUnit.MINUTES)) { "Number template lock can't be locked" }
        try {
            val lockingTime = System.currentTimeMillis() - lockingStartedAt
            if (lockingTime > 1000) {
                log.warn { "Too high lockingTime: $lockingTime" }
            }
            return doInNewTxn { getNextNumberAndIncrement(numTemplateEntity, counterKey) }
        } finally {
            lock.unlock()
        }
    }

    private fun getNextNumberAndIncrement(numTemplateEntity: NumTemplateEntity, counterKey: String): Long {

        var counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, counterKey)

        if (counterEntity == null) {
            counterEntity = NumCounterEntity()
            counterEntity.key = counterKey
            counterEntity.counter = 1L
            counterEntity.template = numTemplateEntity
        } else {
            counterEntity.counter += 1
        }
        return counterRepo.save(counterEntity).counter
    }

    private fun <T : Any> doInNewTxn(action: () -> T): T {
        return doInNewTxnTemplate.execute<T> { status: TransactionStatus ->
            val res = action.invoke()
            status.flush()
            res
        }!!
    }

    @Transactional
    fun delete(id: IdInWs) {

        val template = templateRepo.findByWorkspaceAndExtId(id.workspace, id.id) ?: return
        val counters = counterRepo.findAllByTemplate(template)
        log.info { "Delete counters: $counters" }

        counterRepo.deleteAll(counters)
        templateRepo.delete(template)
    }

    fun addListener(listener: (EntityWithMeta<NumTemplateDef>?, EntityWithMeta<NumTemplateDef>) -> Unit) {
        listeners.add(listener)
    }

    private fun toEntity(dto: NumTemplateDto): NumTemplateEntity {
        var entity = templateRepo.findByWorkspaceAndExtId(dto.workspace, dto.id)
        if (entity == null) {
            entity = NumTemplateEntity()
            entity.extId = dto.id
            entity.workspace = dto.workspace
        }

        entity.counterKey = dto.counterKey
        entity.name = dto.name

        return entity
    }

    private fun toDto(entity: NumTemplateEntity): EntityWithMeta<NumTemplateDef> {
        val numTemplateDef = NumTemplateDef.create()
            .withId(entity.extId)
            .withCounterKey(entity.counterKey)
            .withName(entity.name)
            .withWorkspace(entity.workspace)
            .withModelAttributes(ArrayList(getAtts(entity.counterKey)))
            .build()

        val meta = EntityMeta.create()
            .withCreated(entity.createdDate)
            .withCreator(entity.createdBy)
            .withModified(entity.lastModifiedDate)
            .withModifier(entity.lastModifiedBy)
            .build()

        return EntityWithMeta(numTemplateDef, meta)
    }
}
