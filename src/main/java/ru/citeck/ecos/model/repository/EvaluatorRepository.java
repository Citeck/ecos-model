package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.citeck.ecos.model.domain.EvaluatorEntity;

/**
 * @author Roman Makarskiy
 */
public interface EvaluatorRepository extends JpaRepository<EvaluatorEntity, Long> {
}
