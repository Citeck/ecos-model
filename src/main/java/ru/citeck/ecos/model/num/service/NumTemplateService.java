package ru.citeck.ecos.model.num.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.data.entity.EntityMeta;
import ru.citeck.ecos.commons.data.entity.EntityWithMeta;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.utils.ExceptionUtils;
import ru.citeck.ecos.commons.utils.TmplUtils;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.model.num.domain.NumCounterEntity;
import ru.citeck.ecos.model.num.domain.NumTemplateEntity;
import ru.citeck.ecos.model.num.dto.NumTemplateDto;
import ru.citeck.ecos.model.num.repository.EcosNumCounterRepository;
import ru.citeck.ecos.model.num.repository.NumTemplateRepository;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;

import javax.persistence.EntityManager;
import java.time.Instant;
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

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private final List<BiConsumer<EntityWithMeta<NumTemplateDef>, EntityWithMeta<NumTemplateDef>>> listeners =
        new CopyOnWriteArrayList<>();

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

    public List<EntityWithMeta<NumTemplateDef>> getAll(int max, int skip, Predicate predicate, Sort sort) {

        if (sort == null) {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }

        PageRequest page = PageRequest.of(skip / max, max, sort);

        return templateRepo.findAll(toSpec(predicate), page)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public int getCount(Predicate predicate) {
        Specification<NumTemplateEntity> spec = toSpec(predicate);
        return spec != null ? (int) templateRepo.count(spec) : getCount();
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

    public long getNextNumber(RecordRef templateRef, ObjectData model) {

        NumTemplateEntity numTemplateEntity = templateRepo.findByExtId(templateRef.getId());
        if (numTemplateEntity == null) {
            throw new IllegalArgumentException("Number template doesn't exists: " + templateRef);
        }

        String key = TmplUtils.applyAtts(numTemplateEntity.getCounterKey(), model).asText();

        NumCounterEntity counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, key);
        if (counterEntity == null) {
            counterEntity = new NumCounterEntity();
            counterEntity.setKey(key);
            counterEntity.setCounter(0L);
            counterEntity.setTemplate(numTemplateEntity);
            try {
                counterEntity = counterRepo.save(counterEntity);
            } catch (Exception e) {
                log.warn("Counter can't be created. Perhaps another thread already did it", e);
                counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, key);
                if (counterEntity == null) {
                    throw new IllegalStateException("Counter is null");
                }
            }
        }

        Integer updatedCount = 0;
        for (int i = 0; i < 40 && updatedCount == 0; i++) {

            updatedCount = updateCounter(counterEntity, numTemplateEntity);
            if (updatedCount == null) {
                updatedCount = 0;
            }

            if (updatedCount == 0) {
                try {
                    Thread.sleep(i * 5L);
                } catch (InterruptedException e) {
                    ExceptionUtils.throwException(e);
                }
                counterEntity = counterRepo.findByTemplateAndKey(numTemplateEntity, key);
            }
        }

        if (updatedCount == 0) {
            throw new IllegalStateException("Counter can't be incremented. " +
                "Template ref: " + templateRef + " Model: " + model);
        }

        return counterEntity.getCounter() + 1;
    }

    protected Integer updateCounter(NumCounterEntity counter, NumTemplateEntity numTemplate) {

        return transactionTemplate.execute(status -> {
            int updated = entityManager.createQuery(
                "UPDATE NumCounterEntity " +
                    "SET counter = :oldCounter + 1 " +
                    "WHERE key = :counterKey " +
                    "AND counter = :oldCounter " +
                    "AND template = :numTemplate")
                .setParameter("oldCounter", counter.getCounter().intValue()) //todo: why Long is not working?
                .setParameter("counterKey", counter.getKey())
                .setParameter("numTemplate", numTemplate)
                .executeUpdate();

            status.flush();
            return updated;
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

    private Specification<NumTemplateEntity> toSpec(Predicate predicate) {

        if (predicate instanceof ValuePredicate) {

            ValuePredicate valuePred = (ValuePredicate) predicate;

            ValuePredicate.Type type = valuePred.getType();
            Object value = valuePred.getValue();
            String attribute = valuePred.getAttribute();

            if (RecordConstants.ATT_MODIFIED.equals(attribute)
                && ValuePredicate.Type.GT.equals(type)) {

                Instant instant = Json.getMapper().convert(value, Instant.class);
                if (instant != null) {
                    return (root, query, builder) ->
                        builder.greaterThan(root.get("lastModifiedDate").as(Instant.class), instant);
                }
            }
        }

        PredicateDto predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto.class);
        Specification<NumTemplateEntity> spec = null;

        if (StringUtils.isNotBlank(predicateDto.name)) {
            spec = (root, query, builder) ->
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name.toLowerCase() + "%");
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<NumTemplateEntity> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }

        return spec;
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String moduleId;
    }
}
