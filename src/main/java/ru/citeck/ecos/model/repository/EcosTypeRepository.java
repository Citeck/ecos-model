package ru.citeck.ecos.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.domain.EcosTypeEntity;

import java.util.List;

@Repository
public interface EcosTypeRepository extends JpaRepository<EcosTypeEntity, Long> {

    @Query("select type from EcosTypeEntity type")
    List<EcosTypeEntity> findAll();
}
