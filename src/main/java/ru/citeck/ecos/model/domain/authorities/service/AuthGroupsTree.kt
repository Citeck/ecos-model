package ru.citeck.ecos.model.domain.authorities.service

class AuthGroupsTree(val version: Long) {

    private val groups = HashMap<String, Group>()

    fun getGroup(id: String): Group? {
        return groups[id]
    }

    fun getOrCreateGroup(id: String): Group {
        return groups.computeIfAbsent(id) { Group(it) }
    }

    class Group(
        val id: String,
        val parents: MutableSet<Group> = HashSet(),
        val children: MutableSet<Group> = HashSet()
    ) {

        fun doWithEachParentFull(action: (Group) -> Unit) {
            for (parent in parents) {
                action(parent)
            }
            for (parent in parents) {
                parent.doWithEachParentFull(action)
            }
        }

        fun doWithEachChildFull(action: (Group) -> Unit) {
            for (parent in children) {
                action(parent)
            }
            for (parent in children) {
                parent.doWithEachChildFull(action)
            }
        }

        fun fillFullParentsId(result: MutableSet<String> = HashSet()): Set<String> {
            for (parent in parents) {
                result.add(parent.id)
            }
            for (parent in parents) {
                parent.fillFullParentsId(result)
            }
            return result
        }

        fun addParent(parent: Group) {
            parent.children.add(this)
            parents.add(parent)
        }

        fun addChild(child: Group) {
            if (child.id == id) {
                error("Group can't be added to itself. Id: $id")
            }
            child.parents.add(this)
            children.add(child)
        }

        fun isGroupInParents(group: Group): Boolean {
            return parents.contains(group) || parents.any { it.isGroupInParents(group) }
        }

        fun copy(): Group {
            return Group(this.id, HashSet(parents), HashSet(children))
        }
    }
}
