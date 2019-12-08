package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.citeck.ecos.model.domain.TypeActionEntity;

public interface TypeActionRepository extends JpaRepository<TypeActionEntity, Long> {
}
