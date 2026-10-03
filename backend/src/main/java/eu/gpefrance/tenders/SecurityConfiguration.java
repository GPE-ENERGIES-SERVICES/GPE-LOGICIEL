package eu.gpefrance.tenders;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Session-based security for the web interface. The CSRF token is sent in the
 * XSRF-TOKEN cookie and must come back in the X-XSRF-TOKEN header.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(requests -> requests
			.requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
			.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
			.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
			.requestMatchers(HttpMethod.POST, "/api/collect-runs").hasRole("ADMIN")
			.requestMatchers(HttpMethod.PUT, "/api/filter-criteria").hasRole("ADMIN")
			.requestMatchers("/api/users/**").hasRole("ADMIN")
			.requestMatchers(HttpMethod.GET, "/api/analyses", "/api/analyses/quality").hasRole("ADMIN")
			.anyRequest().authenticated())
			.csrf(csrf -> csrf.spa())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable());
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

}
