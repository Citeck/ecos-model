package ru.citeck.ecos.model.domain.permissions.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface AttributesPermissionsRepository extends JpaRepository<AttributesPermissionEntity, Long>,
            JpaSpecificationExecutor<AttributesPermissionEntity> {

    @Query("SELECT AT FROM AttributesPermissionEntity AT WHERE AT.extId = ?1")
    Optional<AttributesPermissionEntity> findByExtId(String extId);

    @Query("SELECT AT FROM AttributesPermissionEntity AT WHERE AT.extId IN ?1")
    Set<AttributesPermissionEntity> findAllByExtIds(Set<String> extIds);
}
