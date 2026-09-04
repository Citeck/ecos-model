package ru.citeck.ecos.model.num.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.citeck.ecos.model.num.domain.NumCounterEntity;
import ru.citeck.ecos.model.num.domain.NumTemplateEntity;

import java.util.List;

public interface EcosNumCounterRepository extends JpaRepository<NumCounterEntity, Long> {

    NumCounterEntity findByTemplateAndKey(NumTemplateEntity template, String key);

    List<NumCounterEntity> findAllByTemplate(NumTemplateEntity template);

    /**
     * Increment the counter in the database itself.
     *
     * <p>The row-level lock taken by this statement is held until the transaction commits, so
     * concurrent increments of the same counter are serialized by the database across all
     * application instances. That is what makes a distributed lock unnecessary here.
     *
     * @return 1 when the counter exists, 0 when it has never been used yet
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NumCounterEntity c set c.counter = c.counter + 1 " +
        "where c.template.id = :templateId and c.key = :key")
    int incrementCounter(@Param("templateId") Long templateId, @Param("key") String key);

    /**
     * Read the counter value. Called right after {@link #incrementCounter} within the same
     * transaction, where the row is still locked, so the value read is the one just written.
     */
    @Query("select c.counter from NumCounterEntity c " +
        "where c.template.id = :templateId and c.key = :key")
    Long findCounterValue(@Param("templateId") Long templateId, @Param("key") String key);
}
