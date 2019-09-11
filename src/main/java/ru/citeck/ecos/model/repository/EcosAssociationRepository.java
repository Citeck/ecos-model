package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;

import java.util.Optional;
import java.util.Set;

@Repository
public interface EcosAssociationRepository extends JpaRepository<EcosAssociationEntity, Long> {

    @Query("SELECT ASSOC FROM EcosAssociationEntity ASSOC WHERE ASSOC.extId = ?1")
    Optional<EcosAssociationEntity> findByExtId(String extId);

    @Query("SELECT ASSOC FROM EcosAssociationEntity ASSOC WHERE ASSOC.extId IN ?1")
    Set<EcosAssociationEntity> findAllByExtIds(Set<String> extIds);
}
