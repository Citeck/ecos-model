package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.domain.EcosTypeEntity;

import java.util.Optional;
import java.util.Set;

@Repository
public interface EcosTypeRepository extends JpaRepository<EcosTypeEntity, Long> {

    @Query("SELECT TYPE FROM EcosTypeEntity TYPE WHERE TYPE.extId = ?1")
    Optional<EcosTypeEntity> findByExtId(String extId);

    @Query("SELECT TYPE FROM EcosTypeEntity TYPE WHERE TYPE.extId IN ?1")
    Set<EcosTypeEntity> findAllByExtIds(Set<String> extIds);
}
