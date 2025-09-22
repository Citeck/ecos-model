package ru.citeck.ecos.model.domain.treesearch

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.aspect.registry.EcosAspectsRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

@Component
class TreeSearchComponent(
    private val aspectsRegistry: EcosAspectsRegistry,
    private val recordsServiceFactory: RecordsServiceFactory
) {

    companion object {
        private const val HAS_TREE_LEAF_ASSOC_ALIAS_PREFIX = "__has_tree_leaf_assoc_"
    }

    @Value("\${ecos.treeSearchLeafAssocs}")
    private var treeSearchLeafAssocs: String = ""

    lateinit var treeLeafAssocs: Set<String>
    lateinit var treeLeafAspects: Set<String>

    private val attsByDto = ConcurrentHashMap<KClass<*>, Map<String, String>>()

    @PostConstruct
    fun init() {
        treeLeafAssocs = treeSearchLeafAssocs.split(",").mapTo(HashSet()) { it.trim() }
        treeLeafAspects = aspectsRegistry.getAspectsForAtts(treeLeafAssocs).mapTo(HashSet()) { it.getLocalId() }
    }

    fun getAttsWithNonEmptyLeafAssocsToLoad(dto: KClass<*>): Map<String, String> {
        val baseAtts = LinkedHashMap(attsByDto.computeIfAbsent(dto) {
            recordsServiceFactory.attSchemaWriter.writeToMap(
                recordsServiceFactory.dtoSchemaReader.read(dto.java)
            )
        })
        treeLeafAssocs.forEach {
            baseAtts[HAS_TREE_LEAF_ASSOC_ALIAS_PREFIX + it] = "_has.src_assoc_$it?bool!"
        }
        return baseAtts
    }

    fun <T : Any> getAttsWithChangedLeafAssocs(atts: ObjectData, dataType: KClass<T>): AttsWithChangedLeafAssocs<T> {
        val dto = atts.getAsNotNull(dataType.java)

        return AttsWithChangedLeafAssocs(dto, extractNonEmptyLeafSourceAtts(atts))
    }

    fun getNonEmptyLeafSourceAssocs(ref: EntityRef): Set<String> {
        return getNonEmptyLeafSourceAssocs(listOf(ref)).first()
    }

    fun getNonEmptyLeafSourceAssocs(refs: List<EntityRef>): List<Set<String>> {
        val attsToLoad = HashMap<String, String>()
        treeLeafAssocs.forEach {
            attsToLoad[HAS_TREE_LEAF_ASSOC_ALIAS_PREFIX + it] = "_has.assoc_src_$it?bool!"
        }
        return recordsServiceFactory.recordsService.getAtts(refs, attsToLoad).map {
            extractNonEmptyLeafSourceAtts(it.getAtts())
        }
    }

    private fun extractNonEmptyLeafSourceAtts(atts: ObjectData): Set<String> {
        val leafAssocsToUpdate = HashSet<String>()
        atts.forEach { k, v ->
            if (k.startsWith(HAS_TREE_LEAF_ASSOC_ALIAS_PREFIX) && v.asBoolean()) {
                leafAssocsToUpdate.add(k.substringAfter(HAS_TREE_LEAF_ASSOC_ALIAS_PREFIX))
            }
        }
        return leafAssocsToUpdate
    }

    class AttsWithChangedLeafAssocs<T : Any>(
        val atts: T,
        val nonEmptyLeafAssocs: Set<String>
    )
}
