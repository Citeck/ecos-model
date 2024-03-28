package ru.citeck.ecos.model.num.service;

import kotlin.jvm.functions.Function0;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.data.entity.EntityMeta;
import ru.citeck.ecos.commons.data.entity.EntityWithMeta;
import ru.citeck.ecos.commons.utils.TmplUtils;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.model.num.domain.NumCounterEntity;
import ru.citeck.ecos.model.num.domain.NumTemplateEntity;
import ru.citeck.ecos.model.num.dto.NumTemplateDto;
import ru.citeck.ecos.model.num.repository.EcosNumCounterRepository;
import ru.citeck.ecos.model.num.repository.NumTemplateRepository;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NumTemplateService {

    private final NumTemplateRepository templateRepo;
    private final EcosNumCounterRepository counterRepo;

    private final PlatformTransactionManager transactionManager;
    private TransactionTemplate doInNewTxnTemplate;

    private final EcosAppLockService ecosAppLockService;

    private final JpaSearchConverterFactory predicateToJpaConvFactory;
    private JpaSearchConverter<NumTemplateEntity> searchConverter;

    private final List<BiConsumer<EntityWithMeta<NumTemplateDef>, EntityWithMeta<NumTemplateDef>>> listeners =
        new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        searchConverter = predicateToJpaConvFactory.createConverter(NumTemplateEntity.class)
            .withDefaultPageSize(10000)
            .build();

        DefaultTransactionDefinition txnDef = new DefaultTransactionDefinition();
        txnDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doInNewTxnTemplate = new TransactionTemplate(transactionManager, txnDef);
    }

    @Transactional
    public void setNextNumber(EntityRef templateRef, String counterKey, long nextNumber) {

        NumTemplateEntity numTemplateEntity = templateRepo.findByExtId(templateRef.getLocalId());
        if (numTemplateEntity == null) {
            numTemplateEntity = new NumTemplateEntity();
            numTemplateEntity.setExtId(templateRef.getLocalId());
            numTemplateEntity.setName(templateRef.getLocalId());
            numTemplateEntity.setCounterKey(counterKey);
            numTemplateEntity = templateRepo.save(numTemplateEntity);
        }

        NumCounterEntity counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, counterKey);
        if (counterEntity == null) {
            counterEntity = new NumCounterEntity();
            counterEntity.setTemplate(numTemplateEntity);
            counterEntity.setKey(counterKey);
        }
        counterEntity.setCounter(nextNumber - 1L);
        counterRepo.save(counterEntity);
    }

    public List<EntityWithMeta<NumTemplateDef>> getAll() {
        return templateRepo.findAll()
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public List<EntityWithMeta<NumTemplateDef>> getAll(int max, int skip) {

        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        PageRequest page = PageRequest.of(skip / max, max, sort);

        return templateRepo.findAll(page)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public List<EntityWithMeta<NumTemplateDef>> getAll(int max, int skip, Predicate predicate, List<SortBy> sort) {
        return searchConverter.findAll(templateRepo, predicate, max, skip, sort).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public int getCount(Predicate predicate) {
        return (int) searchConverter.getCount(templateRepo, predicate);
    }

    public int getCount() {
        return (int) templateRepo.count();
    }

    public EntityWithMeta<NumTemplateDef> save(NumTemplateDto dto) {

        EntityWithMeta<NumTemplateDef> before = getByIdOrNull(dto.getId());

        NumTemplateEntity entity = toEntity(dto);
        entity = templateRepo.save(entity);
        EntityWithMeta<NumTemplateDef> changedDto = toDto(entity);

        listeners.forEach(l -> l.accept(before, changedDto));
        return changedDto;
    }

    public Optional<EntityWithMeta<NumTemplateDef>> getById(String id) {
        return Optional.ofNullable(getByIdOrNull(id));
    }

    public EntityWithMeta<NumTemplateDef> getByIdOrNull(String id) {
        NumTemplateEntity entity = templateRepo.findByExtId(id);
        if (entity == null) {
            return null;
        }
        return toDto(entity);
    }

    public long getNextNumber(EntityRef templateRef, ObjectData model) {
        return getNextNumber(templateRef, model, true);
    }

    public long getNextNumber(EntityRef templateRef, String counterKey, boolean increment) {

        NumTemplateEntity numTemplateEntity = getNumTemplateEntity(templateRef);

        return getNextNumber(numTemplateEntity, counterKey, increment);
    }

    public long getNextNumber(EntityRef templateRef, ObjectData model, boolean increment) {

        NumTemplateEntity numTemplateEntity = getNumTemplateEntity(templateRef);
        String counterKey = TmplUtils.applyAtts(numTemplateEntity.getCounterKey(), model).asText();

        return getNextNumber(numTemplateEntity, counterKey, increment);
    }

    @NotNull
    private NumTemplateEntity getNumTemplateEntity(EntityRef templateRef) {
        NumTemplateEntity numTemplateEntity = templateRepo.findByExtId(templateRef.getLocalId());
        if (numTemplateEntity == null) {
            throw new IllegalArgumentException("Number template doesn't exists: " + templateRef);
        }
        return numTemplateEntity;
    }

    private long getNextNumber(NumTemplateEntity numTemplateEntity, String counterKey, boolean increment) {

        if (!increment) {
            NumCounterEntity counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, counterKey);
            if (counterEntity == null) {
                return 1;
            } else {
                return counterEntity.getCounter() + 1;
            }
        }
        return ecosAppLockService.doInSync(
            "num-tlt-" + numTemplateEntity.getExtId() + "-" + counterKey,
            Duration.ofMinutes(10),
            lock -> doInNewTxn(() -> getNextNumberAndIncrement(numTemplateEntity, counterKey))
        );
    }

    private long getNextNumberAndIncrement(NumTemplateEntity numTemplateEntity, String counterKey) {

        NumCounterEntity counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, counterKey);

        if (counterEntity == null) {
            counterEntity = new NumCounterEntity();
            counterEntity.setKey(counterKey);
            counterEntity.setCounter(1L);
            counterEntity.setTemplate(numTemplateEntity);
        } else {
            counterEntity.setCounter(counterEntity.getCounter() + 1);
        }
        return counterRepo.save(counterEntity).getCounter();
    }

    private <T> T doInNewTxn(Function0<T> action) {
        return doInNewTxnTemplate.execute(status -> {
            T res = action.invoke();
            status.flush();
            return res;
        });
    }

    @Transactional
    public void delete(String id) {

        NumTemplateEntity template = templateRepo.findByExtId(id);
        if (template == null) {
            return;
        }
        List<NumCounterEntity> counters = counterRepo.findAllByTemplate(template);
        log.info("Delete counters: " + counters);

        counterRepo.deleteAll(counters);
        templateRepo.delete(template);
    }

    public void addListener(BiConsumer<EntityWithMeta<NumTemplateDef>, EntityWithMeta<NumTemplateDef>> listener) {
        this.listeners.add(listener);
    }

    private NumTemplateEntity toEntity(NumTemplateDto dto) {

        NumTemplateEntity entity = templateRepo.findByExtId(dto.getId());
        if (entity == null) {
            entity = new NumTemplateEntity();
            entity.setExtId(dto.getId());
        }

        entity.setCounterKey(dto.getCounterKey());
        entity.setName(dto.getName());

        return entity;
    }

    private EntityWithMeta<NumTemplateDef> toDto(NumTemplateEntity entity) {

        NumTemplateDef numTemplateDef = NumTemplateDef.create()
            .withId(entity.getExtId())
            .withCounterKey(entity.getCounterKey())
            .withName(entity.getName())
            .withModelAttributes(new ArrayList<>(TmplUtils.getAtts(entity.getCounterKey())))
            .build();

        EntityMeta meta = EntityMeta.create()
            .withCreated(entity.getCreatedDate())
            .withCreator(entity.getCreatedBy())
            .withModified(entity.getLastModifiedDate())
            .withModifier(entity.getLastModifiedBy())
            .build();

        return new EntityWithMeta<>(numTemplateDef, meta);
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String moduleId;
    }
}
