package com.csg.workbench.auth;

import com.csg.workbench.account.AccountService;
import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService workbenchUsers(AccountService accounts) {
        return username -> {
            java.util.Map<String, Object> account = accounts.find(username);
            if (account == null) {
                throw new UsernameNotFoundException("unknown account");
            }
            boolean admin = "admin".equals(String.valueOf(account.get("role")));
            return User.withUsername(String.valueOf(account.get("username")))
                .password(String.valueOf(account.get("passwordHash")))
                .roles(admin ? new String[] {"ADMIN", "OPERATOR"} : new String[] {"OPERATOR"})
                .disabled(!AccountService.flag(account.get("enabled")))
                .build();
        };
    }

    @Bean
    SecurityFilterChain workbenchSecurity(HttpSecurity http, AccountService accounts) throws Exception {
        http.authorizeRequests()
            .antMatchers("/api/health", "/api/session/csrf", "/api/session/login").permitAll()
            .antMatchers("/api/**").authenticated()
            .anyRequest().permitAll()
            .and().requestCache().disable()
            .exceptionHandling().authenticationEntryPoint((request, response, failure) ->
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
            .and().addFilterAfter((request, response, chain) -> {
                org.springframework.security.core.Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.isAuthenticated()
                    && authentication.getName() != null && !"anonymousUser".equals(authentication.getName())) {
                    if (!accounts.enabled(authentication.getName())) {
                        ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }
                    HttpServletRequest httpRequest = (HttpServletRequest) request;
                    HttpSession session = httpRequest.getSession(false);
                    Object seen = session == null ? null : session.getAttribute(WorkbenchSession.CREDENTIALS_CHANGED_AT);
                    LocalDateTime changedAt = accounts.credentialsChangedAt(authentication.getName());
                    if (!(seen instanceof LocalDateTime) || changedAt == null || changedAt.isAfter((LocalDateTime) seen)) {
                        SecurityContextHolder.clearContext();
                        if (session != null) {
                            session.invalidate();
                        }
                        ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }
                }
                chain.doFilter(request, response);
            }, UsernamePasswordAuthenticationFilter.class)
            .formLogin().loginProcessingUrl("/api/session/login")
            .successHandler((request, response, auth) -> {
                request.getSession().setAttribute(WorkbenchSession.CREDENTIALS_CHANGED_AT,
                    accounts.credentialsChangedAt(auth.getName()));
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            })
            .failureHandler((request, response, failure) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
            .and().logout().logoutUrl("/api/session/logout")
            .deleteCookies("WORKBENCH_SESSION")
            .logoutSuccessHandler((request, response, auth) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT));
        return http.build();
    }
}
