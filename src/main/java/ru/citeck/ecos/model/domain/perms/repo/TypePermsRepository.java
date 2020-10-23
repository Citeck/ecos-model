package ru.citeck.ecos.model.domain.perms.repo;

import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TypePermsRepository extends JpaRepository<TypePermsEntity, Long>,
            JpaSpecificationExecutor<TypePermsEntity> {

    @Nullable
    TypePermsEntity findByExtId(String extId);

    @Nullable
    TypePermsEntity findByTypeRef(String typeRef);
}
