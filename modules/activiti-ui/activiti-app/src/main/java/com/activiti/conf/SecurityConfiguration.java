/**
 * Activiti app component part of the Activiti project
 * Copyright 2005-2015 Alfresco Software, Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package com.activiti.conf;

import javax.inject.Inject;

import com.activiti.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.StandardPasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

import com.activiti.web.CustomFormLoginConfig;

/**
 * Based on http://docs.spring.io/spring-security/site/docs/3.2.x/reference/htmlsingle/#multiple-httpsecurity
 * 
 * @author Joram Barrez
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, jsr250Enabled = true)
public class SecurityConfiguration {
	
	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);
	
	public static final String KEY_LDAP_ENABLED = "ldap.authentication.enabled";

    //
	// GLOBAL CONFIG
	//

	@Autowired
	private Environment env;

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) {

		// Default auth (database backed)
		try {
			auth.authenticationProvider(dbAuthenticationProvider());
		} catch (Exception e) {
			logger.error("Could not configure authentication mechanism:", e);
		}
	}

	@Bean
	public UserDetailsService userDetailsService() {
		com.activiti.security.UserDetailsService userDetailsService = new com.activiti.security.UserDetailsService();

		// Undocumented setting to configure the amount of time user data is cached before a new check for validity is made
		// Use <= 0 for always do a check
		userDetailsService.setUserValidityPeriod(env.getProperty("cache.users.recheck.period", Long.class, 30000L));

		return userDetailsService;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		PasswordEncoder encoder;
		if (isLDAPActive()) {
			encoder = new LDAPPasswordEncoder();
		} else {
			encoder = new StandardPasswordEncoder();
		}
		return encoder;
	}
	
	@Bean(name = "dbAuthenticationProvider")
	public AuthenticationProvider dbAuthenticationProvider() {
		CustomDaoAuthenticationProvider daoAuthenticationProvider = new CustomDaoAuthenticationProvider();
		daoAuthenticationProvider.setUserDetailsService(userDetailsService());
		PasswordEncoder passwordEncoder = passwordEncoder();
		if (isLDAPActive()) {
			daoAuthenticationProvider.setLDAPPasswordEncoder((LDAPPasswordEncoder)passwordEncoder);
		}
		daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);
		return daoAuthenticationProvider;
	}

	private boolean isLDAPActive() {
		return env.getProperty("ldap.authentication.active", Boolean.class, false);
	}

	//
	// REGULAR WEBAP CONFIG
	//
	
	@Configuration
	@Order(10) // API config first (has Order(1))
    public static class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

		private static final Logger logger = LoggerFactory.getLogger(FormLoginWebSecurityConfigurerAdapter.class);
		
	    @Inject
	    private Environment env;

	    @Inject
	    private AjaxAuthenticationSuccessHandler ajaxAuthenticationSuccessHandler;

	    @Inject
	    private AjaxAuthenticationFailureHandler ajaxAuthenticationFailureHandler;

	    @Inject
	    private AjaxLogoutSuccessHandler ajaxLogoutSuccessHandler;
	    
	    @Inject
	    private Http401UnauthorizedEntryPoint authenticationEntryPoint;
	    
	    @Override
	    protected void configure(HttpSecurity http) throws Exception {
	        http
	            .exceptionHandling()
	                .authenticationEntryPoint(authenticationEntryPoint) 
	                .and()
	            .sessionManagement()
	                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
	                .and()
	            .rememberMe()
	                .rememberMeServices(rememberMeServices())
	                .key(env.getProperty("security.rememberme.key"))
	                .and()
	            .logout()
	                .logoutUrl("/app/logout")
	                .logoutSuccessHandler(ajaxLogoutSuccessHandler)
	                .deleteCookies("JSESSIONID")
	                .permitAll()
	                .and()
	            .csrf()
	                .disable() // Disabled, cause enabling it will cause sessions
	            .headers()
	                .frameOptions()
	                	.sameOrigin()
	                	.addHeaderWriter(new XXssProtectionHeaderWriter())
	                .and()
	            .authorizeRequests()
	                .antMatchers("/*").permitAll()
	                .antMatchers("/app/rest/authenticate").permitAll()
                    .antMatchers("/app/rest/integration/login").permitAll()
                    .antMatchers("/app/rest/temporary/example-options").permitAll()
	                .antMatchers("/app/rest/idm/email-actions/*").permitAll()
	                .antMatchers("/app/rest/idm/signups").permitAll()
	                .antMatchers("/app/rest/idm/passwords").permitAll()
	                .antMatchers("/app/**").authenticated();

	        // Custom login form configurer to allow for non-standard HTTP-methods (eg. LOCK)
	        CustomFormLoginConfig<HttpSecurity> loginConfig = new CustomFormLoginConfig<HttpSecurity>();
	        loginConfig.loginProcessingUrl("/app/authentication")
	            .successHandler(ajaxAuthenticationSuccessHandler)
	            .failureHandler(ajaxAuthenticationFailureHandler)
	            .usernameParameter("j_username")
	            .passwordParameter("j_password")
	            .permitAll();
	        
	        http.apply(loginConfig);
	    }

	    @Bean
	    public RememberMeServices rememberMeServices() {
            return new CustomPersistentRememberMeServices(env, userDetailsService());
	    }
	    
	    @Bean
	    public RememberMeAuthenticationProvider rememberMeAuthenticationProvider() {
	        return new RememberMeAuthenticationProvider(env.getProperty("security.rememberme.key"));
	    }
	    
	    
    }

	public static class LdapAuthenticationEnabledCondition implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return context.getEnvironment().getProperty(KEY_LDAP_ENABLED, Boolean.class, false);
		}

	}

}
