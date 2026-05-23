package com.budget.transaction

import com.budget.auth.User
import com.budget.tag.Tag
import com.budget.tag.TagRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class TransactionNotFoundException(val txId: Long) :
    RuntimeException("거래를 찾을 수 없어요: id=$txId")

@Service
@Transactional
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
) {

    @Transactional(readOnly = true)
    fun list(filter: TransactionFilter, pageable: Pageable): Page<Transaction> {
        val sorted = if (pageable.sort.isUnsorted) {
            PageRequest.of(
                pageable.pageNumber,
                pageable.pageSize,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")),
            )
        } else {
            pageable
        }
        val spec = TransactionSpecifications.filter(filter)
        val page = transactionRepository.findAll(spec, sorted)
        // Initialize lazy tags while still inside the transaction (open-in-view = false).
        page.content.forEach { it.tags.size }
        return page
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Transaction {
        val tx = transactionRepository.findById(id).orElseThrow { TransactionNotFoundException(id) }
        // Force initialization while still inside the transaction (open-in-view = false).
        tx.tags.size
        return tx
    }

    fun create(form: TransactionForm, createdBy: User): Transaction {
        val tags = resolveTags(form.tagIds)
        val transaction = Transaction(
            section = form.section!!,
            type = form.type!!,
            amount = form.amount!!,
            occurredAt = form.occurredAt!!,
            createdBy = createdBy,
            memo = form.memo?.takeIf { it.isNotBlank() },
            tags = tags.toMutableSet(),
        )
        return transactionRepository.save(transaction)
    }

    fun update(id: Long, form: TransactionForm): Transaction {
        val transaction = transactionRepository.findById(id)
            .orElseThrow { TransactionNotFoundException(id) }
        val tags = resolveTags(form.tagIds)
        transaction.section = form.section!!
        transaction.type = form.type!!
        transaction.amount = form.amount!!
        transaction.occurredAt = form.occurredAt!!
        transaction.memo = form.memo?.takeIf { it.isNotBlank() }
        transaction.tags.clear()
        transaction.tags.addAll(tags)
        return transaction
    }

    fun delete(id: Long) {
        if (!transactionRepository.existsById(id)) {
            throw TransactionNotFoundException(id)
        }
        transactionRepository.deleteById(id)
    }

    private fun resolveTags(tagIds: List<Long>): List<Tag> {
        if (tagIds.isEmpty()) return emptyList()
        val distinctIds = tagIds.distinct()
        val tags = tagRepository.findAllById(distinctIds)
        if (tags.size != distinctIds.size) {
            val missing = distinctIds.toSet() - tags.mapNotNull { it.id }.toSet()
            error("태그를 찾을 수 없어요: ${missing.joinToString(",")}")
        }
        return tags
    }
}
