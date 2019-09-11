package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.domain.EcosSectionEntity;

import java.util.Optional;
import java.util.Set;

@Repository
public interface EcosSectionRepository extends JpaRepository<EcosSectionEntity, Long> {

    @Query("SELECT SECTION FROM EcosSectionEntity SECTION WHERE SECTION.extId = ?1")
    Optional<EcosSectionEntity> findByExtId(String extId);

    @Query("SELECT SECTION FROM EcosSectionEntity SECTION WHERE SECTION.extId IN ?1")
    Set<EcosSectionEntity> findAllByExtIds(Set<String> extIds);
}
