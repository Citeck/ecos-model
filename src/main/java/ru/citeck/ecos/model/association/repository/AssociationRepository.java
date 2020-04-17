package ru.citeck.ecos.model.association.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.association.domain.AssociationEntity;

import java.util.Optional;
import java.util.Set;

@Repository
public interface AssociationRepository extends JpaRepository<AssociationEntity, Long> {

    @Query("SELECT ASSOC FROM AssociationEntity ASSOC WHERE ASSOC.extId = ?1")
    Optional<AssociationEntity> findByExtId(String extId);

    @Query("SELECT ASSOC FROM AssociationEntity ASSOC WHERE ASSOC.extId IN ?1")
    Set<AssociationEntity> findAllByExtIds(Set<String> extIds);
}
