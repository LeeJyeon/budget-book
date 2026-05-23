package com.budget.tag

import com.budget.common.TagType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class DuplicateTagException(val tagName: String, val tagType: TagType) :
    RuntimeException("이미 같은 이름과 종류의 태그가 있어요: $tagName (${tagType.label})")

class TagNotFoundException(val tagId: Long) :
    RuntimeException("태그를 찾을 수 없어요: id=$tagId")

@Service
@Transactional
class TagService(
    private val tagRepository: TagRepository,
) {
    @Transactional(readOnly = true)
    fun listAll(): List<Tag> = tagRepository.findAllByOrderByTypeAscNameAsc()

    @Transactional(readOnly = true)
    fun listGrouped(): Map<TagType, List<Tag>> {
        val all = tagRepository.findAllByOrderByTypeAscNameAsc()
        val grouped = all.groupBy { it.type }
        return TagType.values().associateWith { grouped[it].orEmpty() }
    }

    @Transactional(readOnly = true)
    fun search(q: String): List<Tag> {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return emptyList()
        return tagRepository.search(trimmed)
    }

    fun create(name: String, color: String, type: TagType): Tag {
        val cleanName = name.trim()
        tagRepository.findByNameAndType(cleanName, type)?.let {
            throw DuplicateTagException(cleanName, type)
        }
        return tagRepository.save(Tag(name = cleanName, color = color, type = type))
    }

    fun update(id: Long, name: String, color: String, type: TagType): Tag {
        val tag = tagRepository.findById(id).orElseThrow { TagNotFoundException(id) }
        val cleanName = name.trim()
        val duplicate = tagRepository.findByNameAndType(cleanName, type)
        if (duplicate != null && duplicate.id != id) {
            throw DuplicateTagException(cleanName, type)
        }
        tag.name = cleanName
        tag.color = color
        tag.type = type
        return tag
    }

    fun delete(id: Long) {
        if (!tagRepository.existsById(id)) {
            throw TagNotFoundException(id)
        }
        tagRepository.deleteById(id)
    }
}
