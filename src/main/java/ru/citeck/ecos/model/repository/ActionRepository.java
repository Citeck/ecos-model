package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.citeck.ecos.model.domain.ActionEntity;

/**
 * @author Roman Makarskiy
 */
public interface ActionRepository extends JpaRepository<ActionEntity, Long> {
}
