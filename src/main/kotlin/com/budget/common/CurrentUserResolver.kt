package com.budget.common

import com.budget.auth.User
import com.budget.auth.UserPrincipal
import com.budget.auth.UserRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Component

@Component
class CurrentUserResolver(
    private val userRepository: UserRepository,
) {
    fun requireUser(): User {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        val email: String = when (principal) {
            is UserPrincipal -> principal.email
            is OAuth2User -> principal.getAttribute("email") ?: error("OAuth2 principal에 email 없음")
            else -> error("인증되지 않은 요청")
        }
        return userRepository.findByEmail(email) ?: error("DB에 사용자 없음: $email")
    }
}
