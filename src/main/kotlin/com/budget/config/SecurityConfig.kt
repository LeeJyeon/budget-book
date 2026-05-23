package com.budget.config

import com.budget.auth.DevAuthFilter
import com.budget.auth.UserRepository
import com.budget.auth.WhitelistOAuth2UserService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@Configuration
class SecurityConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.dev-auth", name = ["enabled"], havingValue = "true")
    fun devSecurityFilterChain(
        http: HttpSecurity,
        props: AppProperties,
        userRepository: UserRepository,
    ): SecurityFilterChain {
        val devFilter = DevAuthFilter(props, userRepository)
        return http
            .csrf { it.ignoringRequestMatchers(AntPathRequestMatcher("/h2-console/**")) }
            .headers { it.frameOptions { f -> f.disable() } } // for H2 console
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/h2-console/**", "/css/**", "/js/**", "/webjars/**", "/actuator/health")
                    .permitAll()
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(devFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.dev-auth", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun prodSecurityFilterChain(
        http: HttpSecurity,
        props: AppProperties,
        userRepository: UserRepository,
    ): SecurityFilterChain {
        val userService = WhitelistOAuth2UserService(props, userRepository)
        return http
            .csrf { /* default on */ }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/css/**", "/js/**", "/webjars/**", "/actuator/health", "/login").permitAll()
                auth.anyRequest().authenticated()
            }
            .oauth2Login { oauth ->
                oauth.userInfoEndpoint { it.userService(userService) }
            }
            .logout { it.logoutSuccessUrl("/") }
            .build()
    }
}
