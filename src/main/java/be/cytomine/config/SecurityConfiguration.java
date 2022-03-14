package be.cytomine.config;

import be.cytomine.config.security.ApiKeyFilter;
import be.cytomine.repository.security.SecUserRepository;
import be.cytomine.security.*;
import be.cytomine.config.security.JWTConfigurer;
import be.cytomine.security.jwt.TokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final TokenProvider tokenProvider;

    private final DomainUserDetailsService domainUserDetailsService;

    private final SecUserRepository secUserRepository;


    public SecurityConfiguration(TokenProvider tokenProvider, DomainUserDetailsService domainUserDetailsService, SecUserRepository secUserRepository) {
        this.tokenProvider = tokenProvider;
        this.domainUserDetailsService = domainUserDetailsService;
        this.secUserRepository = secUserRepository;
    }

    @Bean
    public SwitchUserFilter switchUserFilter() {
        SwitchUserFilter filter = new SwitchUserFilter();
        filter.setUserDetailsService(domainUserDetailsService);
        filter.setSuccessHandler(ajaxAuthenticationSuccessHandler());
        filter.setFailureHandler(ajaxAuthenticationFailureHandler());
        filter.setUsernameParameter("username");
        filter.setSwitchUserUrl("/api/login/impersonate");
        //filter.setSwitchFailureUrl("/api/login/switchUser");
        //filter.setTargetUrl("/admin/user-management");
        return filter;
    }

    @Bean
    public AjaxAuthenticationSuccessHandler ajaxAuthenticationSuccessHandler() {
        return new AjaxAuthenticationSuccessHandler();
    }

    @Bean
    public AjaxAuthenticationFailureHandler ajaxAuthenticationFailureHandler() {
        return new AjaxAuthenticationFailureHandler();
    }

    @Bean
    public AjaxLogoutSuccessHandler ajaxLogoutSuccessHandler() {
        return new AjaxLogoutSuccessHandler();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new MessageDigestPasswordEncoder("SHA-256");
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(domainUserDetailsService).passwordEncoder(passwordEncoder());
    }

    @Override
    public void configure(WebSecurity web) {
        web.ignoring()
                .antMatchers(HttpMethod.OPTIONS, "/**")
                .antMatchers("/app/**/*.{js,html}")
                .antMatchers("/i18n/**")
                .antMatchers("/content/**")
                .antMatchers("/h2-console/**")
                .antMatchers("/test/**");
    }

    private JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
// @formatter:off
        http
            .csrf()
            .disable()
            .addFilterBefore(new ApiKeyFilter(domainUserDetailsService, secUserRepository), BasicAuthenticationFilter.class)
            .exceptionHandling().authenticationEntryPoint(
                    (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
//        .and()
//            .rememberMe()
//            .key("change_me")
//            .rememberMeParameter("remember_me")
//        .and()
//            .formLogin()
//            .loginProcessingUrl("/j_spring_security_check")
//            .usernameParameter("j_username")
//            .passwordParameter("j_password")
//            .successHandler(ajaxAuthenticationSuccessHandler())
//            .failureHandler(ajaxAuthenticationFailureHandler())
//            .permitAll()
        .and()
            .logout()
            .logoutUrl("/api/logout")
            .logoutSuccessHandler(ajaxLogoutSuccessHandler())
            .permitAll()
        .and()
            .authorizeRequests()
            .antMatchers("/api/authenticate").permitAll()
            .antMatchers("/api/register").permitAll()
            .antMatchers("/api/activate").permitAll()
            .antMatchers("/api/countries").permitAll()
            .antMatchers("/api/account/resetPassword/init").permitAll()
            .antMatchers("/api/account/resetPassword/finish").permitAll()
            .antMatchers("/api/**").authenticated()
            .antMatchers("/api/login/impersonate*").hasAuthority("ADMIN")
            .antMatchers("/api/logout/impersonate*").authenticated()
            .antMatchers(HttpMethod.POST, "/server/**").permitAll()
            .antMatchers("/**").permitAll()
        .and()
            .httpBasic()
        .and()
            .apply(securityConfigurerAdapter())
        .and()
            .addFilter(switchUserFilter());
        // @formatter:on
    }

//    grails.plugin.springsecurity.interceptUrlMap = [
//        '/admin/**':    ['ROLE_ADMIN','ROLE_SUPER_ADMIN'],
//        '/admincyto/**':    ['ROLE_ADMIN','ROLE_SUPER_ADMIN'],
//        '/monitoring/**':    ['ROLE_ADMIN','ROLE_SUPER_ADMIN'],
//        '/j_spring_security_switch_user': ['ROLE_ADMIN','ROLE_SUPER_ADMIN'],
//        '/securityInfo/**': ['ROLE_ADMIN','ROLE_SUPER_ADMIN'],
//        '/api/**':      ['IS_AUTHENTICATED_REMEMBERED'],
//        '/lib/**':      ['IS_AUTHENTICATED_ANONYMOUSLY'],
//        '/css/**':      ['IS_AUTHENTICATED_ANONYMOUSLY'],
//        '/images/**':   ['IS_AUTHENTICATED_ANONYMOUSLY'],
//        '/*':           ['IS_AUTHENTICATED_REMEMBERED'], //if cas authentication, active this      //beta comment
//        '/login/**':    ['IS_AUTHENTICATED_ANONYMOUSLY'],
//        '/logout/**':   ['IS_AUTHENTICATED_ANONYMOUSLY'],
//        '/status/**':   ['IS_AUTHENTICATED_ANONYMOUSLY']
//]
}