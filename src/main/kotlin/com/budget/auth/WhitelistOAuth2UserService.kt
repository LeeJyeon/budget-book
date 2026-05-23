package com.budget.auth

import com.budget.common.UserRole
import com.budget.config.AppProperties
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.transaction.annotation.Transactional

class WhitelistOAuth2UserService(
    private val props: AppProperties,
    private val userRepository: UserRepository,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val delegate = DefaultOAuth2UserService()

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val raw = delegate.loadUser(userRequest)
        val email = raw.getAttribute<String>("email")
            ?: throw OAuth2AuthenticationException(OAuth2Error("missing_email", "이메일 정보를 받지 못했습니다", null))

        if (email !in props.whitelist) {
            throw OAuth2AuthenticationException(OAuth2Error("access_denied", "허용된 사용자가 아닙니다: $email", null))
        }

        val displayName = raw.getAttribute<String>("name") ?: email
        val user = userRepository.findByEmail(email)
            ?: userRepository.save(
                User(
                    email = email,
                    displayName = displayName,
                    role = inferRole(email),
                ),
            )

        val attrs = raw.attributes.toMutableMap()
        attrs["userId"] = user.id!!
        attrs["role"] = user.role.name

        return DefaultOAuth2User(raw.authorities, attrs, "email")
    }

    private fun inferRole(email: String): UserRole {
        val idx = props.whitelist.indexOf(email)
        return if (idx == 0) UserRole.HUSBAND else UserRole.WIFE
    }
}
