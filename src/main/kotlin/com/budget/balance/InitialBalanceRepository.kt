package com.budget.balance

import com.budget.common.Section
import org.springframework.data.jpa.repository.JpaRepository

interface InitialBalanceRepository : JpaRepository<InitialBalance, Section>
