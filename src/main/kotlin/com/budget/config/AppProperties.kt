package com.budget.config

import com.budget.common.UserRole
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val timezone: String = "Asia/Seoul",
    val whitelist: List<String> = emptyList(),
    val devAuth: DevAuth = DevAuth(),
) {
    data class DevAuth(
        val enabled: Boolean = false,
        val email: String = "dev@example.com",
        val displayName: String = "Dev User",
        val role: UserRole = UserRole.HUSBAND,
    )
}
