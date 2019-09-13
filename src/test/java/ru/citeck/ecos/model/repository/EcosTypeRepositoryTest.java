package ru.citeck.ecos.model.repository;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.model.domain.EcosTypeEntity;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@RunWith(SpringRunner.class)
@DataJpaTest
@DirtiesContext
public class EcosTypeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EcosTypeRepository typeRepository;

    @Test
    public void findByExtIdReturnValues() {
        EcosTypeEntity type = new EcosTypeEntity("b", null, "bname", "bdesc",
            "btenant", null, null, null, null);
        EcosTypeEntity type2 = new EcosTypeEntity("a", null, "aname", "adesc",
            "atenant", type, null, null, null);
        type.setChilds(Collections.singleton(type2));
        entityManager.persistAndFlush(type);
        entityManager.persistAndFlush(type2);

        Optional<EcosTypeEntity> op = typeRepository.findByExtId("a");
        Assert.assertTrue(op.isPresent());
        Assert.assertEquals("aname", op.get().getName());
        Assert.assertEquals("adesc", op.get().getDescription());
        Assert.assertEquals("atenant", op.get().getTenant());
        Assert.assertEquals("b", op.get().getParent().getExtId());
    }

    @Test
    public void findByExtIdNotReturnValues() {

        Optional<EcosTypeEntity> op = typeRepository.findByExtId("b");
        Assert.assertFalse(op.isPresent());
    }

    @Test
    public void findAllByExtIdsReturnValues() {

        EcosTypeEntity type = new EcosTypeEntity("c", null, "aname", "adesc",
            "atenant", null, null, null, null);
        entityManager.persistAndFlush(type);

        Set<EcosTypeEntity> set = typeRepository.findAllByExtIds(Collections.singleton("c"));
        Assert.assertEquals(1, set.size());
        EcosTypeEntity entity = set.iterator().next();
        Assert.assertEquals("aname", entity.getName());
        Assert.assertEquals("adesc", entity.getDescription());
        Assert.assertEquals("atenant", entity.getTenant());
        Assert.assertNull(entity.getParent());
    }

    @Test
    public void findAllExtIdsNotReturnValues() {

        Set<EcosTypeEntity> set = typeRepository.findAllByExtIds(Collections.singleton("z"));
        Assert.assertEquals(0, set.size());
    }
}
