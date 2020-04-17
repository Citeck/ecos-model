package ru.citeck.ecos.model.section.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.section.domain.SectionEntity;

import java.util.Optional;
import java.util.Set;

@Repository
public interface SectionRepository extends JpaRepository<SectionEntity, Long> {

    @Query("SELECT SECTION FROM SectionEntity SECTION WHERE SECTION.extId = ?1")
    Optional<SectionEntity> findByExtId(String extId);

    @Query("SELECT SECTION FROM SectionEntity SECTION WHERE SECTION.extId IN ?1")
    Set<SectionEntity> findAllByExtIds(Set<String> extIds);
}
