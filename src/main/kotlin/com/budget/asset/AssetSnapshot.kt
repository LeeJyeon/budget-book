package com.budget.asset

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "asset_snapshots")
class AssetSnapshot(
    @Id
    @Column(name = "year_month", length = 7)
    var yearMonth: String,

    @Column(name = "total_assets", nullable = false)
    var totalAssets: Long,

    @Column(name = "debt", nullable = false)
    var debt: Long = 0L,

    @Column(name = "memo", length = 500)
    var memo: String? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
) {
    init {
        require(totalAssets >= 0) { "total_assets는 음수일 수 없습니다" }
        require(debt >= 0) { "debt는 음수일 수 없습니다" }
        require(yearMonth.matches(Regex("\\d{4}-\\d{2}"))) { "year_month 형식은 YYYY-MM" }
    }

    val netWorth: Long get() = totalAssets - debt
}
