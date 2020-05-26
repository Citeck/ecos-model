package ru.citeck.ecos.model.num.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.citeck.ecos.model.num.domain.NumCounterEntity;
import ru.citeck.ecos.model.num.domain.NumTemplateEntity;

import java.util.List;

public interface EcosNumCounterRepository extends JpaRepository<NumCounterEntity, Long> {

    NumCounterEntity findByTemplateAndKey(NumTemplateEntity template, String key);

    List<NumCounterEntity> findAllByTemplate(NumTemplateEntity template);
}
