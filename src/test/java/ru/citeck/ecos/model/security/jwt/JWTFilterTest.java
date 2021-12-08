package ru.citeck.ecos.model.security.jwt;

import io.github.jhipster.config.JHipsterProperties;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.DispatcherServlet;
import ru.citeck.ecos.context.lib.auth.AuthComponent;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.context.lib.auth.AuthRole;
import ru.citeck.ecos.context.lib.spring.config.SpringAuthComponent;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class JWTFilterTest {

    private TokenProvider tokenProvider;

    private JWTFilter jwtFilter;

    @Before
    public void setup() {
        JHipsterProperties jHipsterProperties = new JHipsterProperties();
        tokenProvider = new TokenProvider(jHipsterProperties);
        ReflectionTestUtils.setField(tokenProvider, "key",
            Keys.hmacShaKeyFor(Decoders.BASE64
                .decode("fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8")));

        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", 60000);
        jwtFilter = new JWTFilter(tokenProvider);
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    @Test
    public void testJWTFilter() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "test-user",
            "test-password",
            Collections.singletonList(new SimpleGrantedAuthority(AuthRole.USER))
        );
        String jwt = tokenProvider.createToken(authentication, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthContext.INSTANCE.setComponent(new SpringAuthComponent());
        String[] userInFilter = new String[1];
        MockFilterChain filterChain = new MockFilterChain(new DispatcherServlet(), new Filter() {
            public void init(FilterConfig filterConfig) {}
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
                userInFilter[0] = SecurityContextHolder.getContext().getAuthentication().getName();
            }
            public void destroy() {}
        });

        jwtFilter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(userInFilter[0]).isEqualTo("test-user");
        //assertThat(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString()).isEqualTo(jwt);
    }

    @Test
    public void testJWTFilterInvalidToken() throws Exception {
        String jwt = "wrong_jwt";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        //Assertions.assertThatThrownBy(() ->
            jwtFilter.doFilter(request, response, filterChain);
        //).isExactlyInstanceOf(AccessDeniedException.class);
    }

    @Test
    public void testJWTFilterMissingAuthorization() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        //Assertions.assertThatThrownBy(() ->
            jwtFilter.doFilter(request, response, filterChain);
        //).isExactlyInstanceOf(AccessDeniedException.class);
    }

    @Test
    public void testJWTFilterMissingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JWTFilter.AUTHORIZATION_HEADER, "Bearer ");
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        //Assertions.assertThatThrownBy(() ->
            jwtFilter.doFilter(request, response, filterChain);
        //).isExactlyInstanceOf(AccessDeniedException.class);
    }

    @Test
    public void testJWTFilterWrongScheme() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "test-user",
            "test-password",
            Collections.singletonList(new SimpleGrantedAuthority(AuthRole.USER))
        );
        String jwt = tokenProvider.createToken(authentication, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JWTFilter.AUTHORIZATION_HEADER, "Basic " + jwt);
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        //Assertions.assertThatThrownBy(() ->
            jwtFilter.doFilter(request, response, filterChain);
        //).isExactlyInstanceOf(AccessDeniedException.class);
    }
}
