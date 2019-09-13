package ru.citeck.ecos.model.repository;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@RunWith(SpringRunner.class)
@DataJpaTest
@DirtiesContext
public class EcosAssociationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EcosAssociationRepository associationRepository;

    @Test
    public void findByExtIdReturnValues() {
        EcosTypeEntity type = new EcosTypeEntity("a", null, "aname", "adesc",
            "atenant", null, null, null, null);
        entityManager.persistAndFlush(type);

        EcosAssociationEntity assoc = new EcosAssociationEntity("a",null, "aname", "atitle", type);
        entityManager.persistAndFlush(assoc);

        type.setAssociations(Collections.singleton(assoc));

        Optional<EcosAssociationEntity> op = associationRepository.findByExtId("a");
        Assert.assertTrue(op.isPresent());
        Assert.assertEquals("aname", op.get().getName());
        Assert.assertEquals("atitle", op.get().getTitle());
        Assert.assertEquals("a", op.get().getType().getExtId());
    }

    @Test
    public void findByExtIdNotReturnValues() {

        Optional<EcosAssociationEntity> op = associationRepository.findByExtId("b");
        Assert.assertFalse(op.isPresent());
    }

    @Test
    public void findAllByExtIdsReturnValues() {

        EcosTypeEntity type = new EcosTypeEntity("b", null, "aname", "adesc",
            "atenant", null, null, null, null);
        entityManager.persistAndFlush(type);

        EcosAssociationEntity assoc = new EcosAssociationEntity("c",null, "aname", "atitle", type);
        entityManager.persistAndFlush(assoc);

        type.setAssociations(Collections.singleton(assoc));

        Set<EcosAssociationEntity> set = associationRepository.findAllByExtIds(Collections.singleton("c"));
        Assert.assertEquals(1, set.size());
        EcosAssociationEntity entity = set.iterator().next();
        Assert.assertEquals("aname", entity.getName());
        Assert.assertEquals("atitle", entity.getTitle());
        Assert.assertEquals("b", entity.getType().getExtId());
    }

    @Test
    public void findAllExtIdsNotReturnValues() {

        Set<EcosAssociationEntity> set = associationRepository.findAllByExtIds(Collections.singleton("b"));
        Assert.assertEquals(0, set.size());
    }
}
