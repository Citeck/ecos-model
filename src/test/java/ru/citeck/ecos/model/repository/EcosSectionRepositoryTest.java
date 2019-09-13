package ru.citeck.ecos.model.repository;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.model.domain.EcosSectionEntity;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@RunWith(SpringRunner.class)
@DataJpaTest
@DirtiesContext
public class EcosSectionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EcosSectionRepository sectionRepository;

    @Test
    public void findByExtIdReturnValues() {

        EcosSectionEntity section = new EcosSectionEntity("a", null, null,
            "aname", "adesc", "atenant");
        entityManager.persistAndFlush(section);

        Optional<EcosSectionEntity> op = sectionRepository.findByExtId("a");
        Assert.assertTrue(op.isPresent());
        Assert.assertEquals("aname", op.get().getName());
        Assert.assertEquals("adesc", op.get().getDescription());
        Assert.assertEquals("atenant", op.get().getTenant());
        Assert.assertNull(op.get().getTypes());
    }

    @Test
    public void findByExtIdNotReturnValues() {

        Optional<EcosSectionEntity> op = sectionRepository.findByExtId("b");
        Assert.assertFalse(op.isPresent());
    }

    @Test
    public void findAllByExtIdsReturnValues() {

        EcosSectionEntity section = new EcosSectionEntity("c", null, null,
            "aname", "adesc", "atenant");
        entityManager.persistAndFlush(section);

        Set<EcosSectionEntity> set = sectionRepository.findAllByExtIds(Collections.singleton("c"));
        Assert.assertEquals(1, set.size());
        EcosSectionEntity entity = set.iterator().next();
        Assert.assertEquals("aname", entity.getName());
        Assert.assertEquals("adesc", entity.getDescription());
        Assert.assertEquals("atenant", entity.getTenant());
        Assert.assertNull(entity.getTypes());
    }

    @Test
    public void findAllExtIdsNotReturnValues() {

        Set<EcosSectionEntity> set = sectionRepository.findAllByExtIds(Collections.singleton("b"));
        Assert.assertEquals(0, set.size());
    }
}
