package com.csg.workbench.auth;

import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    UserDetailsService workbenchUsers(@Value("${WORKBENCH_LOGIN_USER}") String username,
                                     @Value("${WORKBENCH_LOGIN_PASSWORD_HASH}") String hash) {
        if (!username.matches("[a-zA-Z0-9_.-]{1,20}") || !hash.matches("\\{bcrypt}\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalArgumentException("Configure a workbench username and a {bcrypt} password hash");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username).password(hash).roles("OPERATOR").build());
    }

    @Bean
    SecurityFilterChain workbenchSecurity(HttpSecurity http) throws Exception {
        http.authorizeRequests()
            .antMatchers("/api/health", "/api/session/csrf", "/api/session/login").permitAll()
            .antMatchers("/api/**").authenticated()
            .anyRequest().permitAll()
            .and().requestCache().disable()
            .exceptionHandling().authenticationEntryPoint((request, response, failure) ->
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
            .and().formLogin().loginProcessingUrl("/api/session/login")
            .successHandler((request, response, auth) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
            .failureHandler((request, response, failure) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
            .and().logout().logoutUrl("/api/session/logout")
            .deleteCookies("WORKBENCH_SESSION")
            .logoutSuccessHandler((request, response, auth) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT));
        return http.build();
    }
}
