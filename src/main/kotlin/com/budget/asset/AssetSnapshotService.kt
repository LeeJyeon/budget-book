package com.budget.asset

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

class AssetSnapshotNotFoundException(yearMonth: String) :
    RuntimeException("자산 스냅샷이 없습니다: $yearMonth")

@Service
@Transactional
class AssetSnapshotService(
    private val snapshotRepository: AssetSnapshotRepository,
) {

    fun upsert(yearMonth: String, form: AssetSnapshotForm): AssetSnapshot {
        validateYearMonth(yearMonth)
        val totalAssets = requireNotNull(form.totalAssets) { "총자산은 필수입니다." }
        val debt = form.debt ?: 0L
        require(totalAssets >= 0) { "총자산은 0 이상이어야 합니다." }
        require(debt >= 0) { "부채는 0 이상이어야 합니다." }

        val existing = snapshotRepository.findById(yearMonth).orElse(null)
        return if (existing != null) {
            existing.totalAssets = totalAssets
            existing.debt = debt
            existing.memo = form.memo
            snapshotRepository.save(existing)
        } else {
            snapshotRepository.save(
                AssetSnapshot(
                    yearMonth = yearMonth,
                    totalAssets = totalAssets,
                    debt = debt,
                    memo = form.memo,
                ),
            )
        }
    }

    fun delete(yearMonth: String) {
        validateYearMonth(yearMonth)
        if (!snapshotRepository.existsById(yearMonth)) {
            throw AssetSnapshotNotFoundException(yearMonth)
        }
        snapshotRepository.deleteById(yearMonth)
    }

    @Transactional(readOnly = true)
    fun listInRange(from: YearMonth, to: YearMonth): List<AssetSnapshot> {
        require(!from.isAfter(to)) { "from은 to보다 이전이거나 같아야 합니다." }
        return snapshotRepository.findAllByYearMonthBetweenOrderByYearMonthAsc(
            from.toString(),
            to.toString(),
        )
    }

    private fun validateYearMonth(yearMonth: String) {
        require(yearMonth.matches(YEAR_MONTH_REGEX)) { "year_month 형식은 YYYY-MM 이어야 합니다: $yearMonth" }
    }

    companion object {
        private val YEAR_MONTH_REGEX = Regex("\\d{4}-\\d{2}")
    }
}
