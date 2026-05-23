package com.budget.recurring

import com.budget.auth.User
import com.budget.common.Section
import com.budget.common.TxType
import com.budget.tag.Tag
import com.budget.tag.TagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class RecurringNotFoundException(val recurringId: Long) :
    RuntimeException("정기 항목을 찾을 수 없어요: id=$recurringId")

@Service
@Transactional
class RecurringService(
    private val recurringRepository: RecurringTransactionRepository,
    private val tagRepository: TagRepository,
) {

    @Transactional(readOnly = true)
    fun listAll(): List<RecurringTransaction> =
        recurringRepository.findAllByOrderByActiveDescDayOfMonthAscNameAsc()

    @Transactional(readOnly = true)
    fun listActive(): List<RecurringTransaction> =
        recurringRepository.findAllByActiveTrueOrderByDayOfMonthAscNameAsc()

    @Transactional(readOnly = true)
    fun get(id: Long): RecurringTransaction =
        recurringRepository.findById(id).orElseThrow { RecurringNotFoundException(id) }

    fun create(
        name: String,
        section: Section,
        type: TxType,
        amount: Long,
        dayOfMonth: Int?,
        memo: String?,
        tagIds: List<Long>,
        active: Boolean,
        createdBy: User,
    ): RecurringTransaction {
        val tags = loadTags(tagIds)
        val entity = RecurringTransaction(
            name = name.trim(),
            section = section,
            type = type,
            amount = amount,
            createdBy = createdBy,
            dayOfMonth = dayOfMonth?.toShort(),
            memo = memo?.trim()?.ifBlank { null },
            active = active,
            tags = tags.toMutableSet(),
        )
        return recurringRepository.save(entity)
    }

    fun update(
        id: Long,
        name: String,
        section: Section,
        type: TxType,
        amount: Long,
        dayOfMonth: Int?,
        memo: String?,
        tagIds: List<Long>,
        active: Boolean,
    ): RecurringTransaction {
        val entity = get(id)
        require(amount > 0) { "amount는 양수여야 합니다" }
        require(dayOfMonth == null || dayOfMonth in 1..31) { "day_of_month는 1~31 사이여야 합니다" }
        entity.name = name.trim()
        entity.section = section
        entity.type = type
        entity.amount = amount
        entity.dayOfMonth = dayOfMonth?.toShort()
        entity.memo = memo?.trim()?.ifBlank { null }
        entity.active = active
        val tags = loadTags(tagIds)
        entity.tags.clear()
        entity.tags.addAll(tags)
        return entity
    }

    fun delete(id: Long) {
        if (!recurringRepository.existsById(id)) {
            throw RecurringNotFoundException(id)
        }
        recurringRepository.deleteById(id)
    }

    fun toggleActive(id: Long): RecurringTransaction {
        val entity = get(id)
        entity.active = !entity.active
        return entity
    }

    /**
     * Build the prefill URL for `/transactions/new` based on a recurring memo item.
     * Example: `/transactions/new?section=SHARED&type=EXPENSE&amount=9500&memo=%EB%84%B7%ED%94%8C%EB%A6%AD%EC%8A%A4&tagIds=3,7`
     */
    @Transactional(readOnly = true)
    fun buildPrefillUrl(id: Long): String {
        val entity = get(id)
        val builder = UriComponentsBuilder.fromPath("/transactions/new")
            .queryParam("section", entity.section.name)
            .queryParam("type", entity.type.name)
            .queryParam("amount", entity.amount)
            .queryParam("memo", encode(entity.name))
        if (entity.tags.isNotEmpty()) {
            val tagIdsCsv = entity.tags.mapNotNull { it.id }.sorted().joinToString(",")
            if (tagIdsCsv.isNotBlank()) {
                builder.queryParam("tagIds", tagIdsCsv)
            }
        }
        return builder.build(true).toUriString()
    }

    private fun loadTags(tagIds: List<Long>): List<Tag> {
        if (tagIds.isEmpty()) return emptyList()
        val unique = tagIds.distinct()
        return tagRepository.findAllById(unique).toList()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
