package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.domain.EcosSectionEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcosSectionRepository extends JpaRepository<EcosSectionEntity, Long> {

    @Query("select section from EcosSectionEntity section")
    List<EcosSectionEntity> findAll();

    @Query("SELECT section FROM EcosSectionEntity section WHERE section.uuid = ?1")
    Optional<EcosSectionEntity> findByUuid(String uuid);

    @Query("SELECT section FROM EcosSectionEntity section WHERE section.uuid IN ?1")
    List<EcosSectionEntity> findAllByUuid(List<String> uuid);
}
