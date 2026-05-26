package com.budget.asset

import org.springframework.data.jpa.repository.JpaRepository

interface AssetSnapshotRepository : JpaRepository<AssetSnapshot, String> {

    fun findAllByOrderByYearMonthAsc(): List<AssetSnapshot>

    fun findTopByOrderByYearMonthDesc(): AssetSnapshot?

    fun findAllByYearMonthBetweenOrderByYearMonthAsc(
        fromInclusive: String,
        toInclusive: String,
    ): List<AssetSnapshot>
}
