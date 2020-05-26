package ru.citeck.ecos.model.num.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.citeck.ecos.model.num.domain.NumTemplateEntity;

public interface NumTemplateRepository
    extends JpaRepository<NumTemplateEntity, Long>,
            JpaSpecificationExecutor<NumTemplateEntity> {

    NumTemplateEntity findByExtId(String extId);
}
