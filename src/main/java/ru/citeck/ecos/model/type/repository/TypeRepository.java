package ru.citeck.ecos.model.type.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.citeck.ecos.model.type.domain.TypeEntity;

import java.util.Optional;
import java.util.Set;

@Repository
public interface TypeRepository extends JpaRepository<TypeEntity, Long>, JpaSpecificationExecutor<TypeEntity> {

    @Query("SELECT TYPE FROM TypeEntity TYPE WHERE TYPE.extId = ?1")
    Optional<TypeEntity> findByExtId(String extId);

    boolean existsByExtId(String extId);

    @Query("SELECT TYPE FROM TypeEntity TYPE WHERE TYPE.extId IN ?1")
    Set<TypeEntity> findAllByExtIds(Set<String> extIds);

    Set<TypeEntity> findAllByParent(TypeEntity parent);

    @Query("SELECT TYPE " +
        "FROM TypeEntity TYPE " +
        "JOIN TYPE.aliases aliases " +
        "WHERE ?1 = aliases")
    Optional<TypeEntity> findByContainsInAliases(String alias);
}