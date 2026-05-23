package com.budget.common

import com.budget.auth.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

data class CurrentUserView(
    val displayName: String,
    val email: String,
    val role: UserRole,
)

@ControllerAdvice
class CurrentUserAdvice {

    @ModelAttribute("currentUser")
    fun currentUser(@AuthenticationPrincipal principal: Any?): CurrentUserView? = when (principal) {
        is UserPrincipal -> CurrentUserView(principal.displayName, principal.email, principal.role)
        is OAuth2User -> CurrentUserView(
            displayName = principal.getAttribute("name") ?: principal.getAttribute<String>("email") ?: "User",
            email = principal.getAttribute("email") ?: "",
            role = runCatching { UserRole.valueOf(principal.getAttribute<String>("role") ?: "HUSBAND") }
                .getOrDefault(UserRole.HUSBAND),
        )
        else -> null
    }
}
