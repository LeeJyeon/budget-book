package com.budget.auth

import com.budget.config.AppProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.filter.OncePerRequestFilter

class DevAuthFilter(
    private val props: AppProperties,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {

    @Transactional
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (SecurityContextHolder.getContext().authentication == null ||
            !SecurityContextHolder.getContext().authentication.isAuthenticated
        ) {
            val dev = props.devAuth
            val user = userRepository.findByEmail(dev.email)
                ?: userRepository.save(
                    User(
                        email = dev.email,
                        displayName = dev.displayName,
                        role = dev.role,
                    )
                )
            val principal = UserPrincipal(
                userId = user.id!!,
                email = user.email,
                displayName = user.displayName,
                role = user.role,
            )
            val auth = UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
            SecurityContextHolder.getContext().authentication = auth
        }
        filterChain.doFilter(request, response)
    }
}
