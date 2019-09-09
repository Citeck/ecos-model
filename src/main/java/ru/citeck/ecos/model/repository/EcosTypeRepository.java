package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.domain.EcosTypeEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EcosTypeRepository extends JpaRepository<EcosTypeEntity, Long> {

    @Query("select type from EcosTypeEntity type")
    List<EcosTypeEntity> findAll();

    @Query("SELECT type FROM EcosTypeEntity type WHERE type.uuid = ?1")
    Optional<EcosTypeEntity> findByUuid(String uuid);

    @Query("SELECT type FROM EcosTypeEntity type WHERE type.uuid IN ?1")
    Set<EcosTypeEntity> findAllByUuid(List<String> uuid);
}
