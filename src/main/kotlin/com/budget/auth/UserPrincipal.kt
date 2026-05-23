package com.budget.auth

import com.budget.common.UserRole

data class UserPrincipal(
    val userId: Long,
    val email: String,
    val displayName: String,
    val role: UserRole,
)
