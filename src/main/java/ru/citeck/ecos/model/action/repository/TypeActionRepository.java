package ru.citeck.ecos.model.action.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.citeck.ecos.model.action.domain.TypeActionEntity;

public interface TypeActionRepository extends JpaRepository<TypeActionEntity, Long> {
}
