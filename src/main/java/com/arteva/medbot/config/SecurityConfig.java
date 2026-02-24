package com.arteva.medbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Конфигурация безопасности REST API на основе Spring Security.
 * <p>
 * Правила доступа:
 * <ul>
 *   <li>{@code POST /ask} — публичный (используется Telegram-ботом и внешними клиентами)</li>
 *   <li>{@code /actuator/health, info, prometheus} — публичные (мониторинг)</li>
 *   <li>{@code POST /reindex} — требует роль ADMIN (Basic Auth)</li>
 *   <li>{@code /actuator/**} (остальные) — требует роль ADMIN</li>
 *   <li>Все прочие пути — запрещены ({@code denyAll})</li>
 * </ul>
 * <p>
 * Особенности:
 * <ul>
 *   <li>CSRF отключён (стателесс API)</li>
 *   <li>Сессии не создаются ({@code STATELESS})</li>
 *   <li>Пользователь хранится в памяти ({@link InMemoryUserDetailsManager}) с BCrypt</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Настройка цепочки фильтров безопасности.
     *
     * @param http конфигуратор Spring Security
     * @return сконфигурированная цепочка фильтров
     * @throws Exception при ошибке конфигурации
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ask").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/reindex", "/actuator/**").hasRole("ADMIN")
                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Создаёт хранилище пользователей в памяти с одним администратором.
     * <p>
     * Логин и пароль читаются из {@code api.admin.username} и {@code api.admin.password}.
     *
     * @param username        логин администратора
     * @param password        пароль администратора (открытый текст, будет захэширован BCrypt)
     * @param passwordEncoder энкодер паролей
     * @return хранилище пользователей
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${api.admin.username:admin}") String username,
            @Value("${api.admin.password}") String password,
            PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.builder()
                        .username(username)
                        .password(passwordEncoder.encode(password))
                        .roles("ADMIN")
                        .build()
        );
    }

    /**
     * Бин BCrypt-энкодера для хэширования паролей.
     *
     * @return экземпляр {@link BCryptPasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
