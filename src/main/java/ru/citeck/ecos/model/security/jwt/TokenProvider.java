package ru.citeck.ecos.model.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.github.jhipster.config.JHipsterProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ru.citeck.ecos.context.lib.auth.data.TokenAuthData;

@Component
public class TokenProvider {

    private final Logger log = LoggerFactory.getLogger(TokenProvider.class);

    private static final String AUTHORITIES_KEY = "auth";

    private Key key;

    private long tokenValidityInMilliseconds;

    private long tokenValidityInMillisecondsForRememberMe;

    private final JHipsterProperties ecosRegistryProperties;

    public TokenProvider(JHipsterProperties ecosRegistryProperties) {
        this.ecosRegistryProperties = ecosRegistryProperties;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes;
        String secret = ecosRegistryProperties.getSecurity().getAuthentication().getJwt().getSecret();
        if (!StringUtils.isEmpty(secret)) {
            log.warn("Warning: the JWT key used is not Base64-encoded. " +
                "We recommend using the `jhipster.security.authentication.jwt.base64-secret` key for optimum security.");
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        } else {
            log.debug("Using a Base64-encoded JWT secret key");
            keyBytes = Decoders.BASE64.decode(ecosRegistryProperties.getSecurity().getAuthentication().getJwt().getBase64Secret());
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.tokenValidityInMilliseconds =
            1000 * ecosRegistryProperties.getSecurity().getAuthentication().getJwt().getTokenValidityInSeconds();
        this.tokenValidityInMillisecondsForRememberMe =
            1000 * ecosRegistryProperties.getSecurity().getAuthentication().getJwt()
                .getTokenValidityInSecondsForRememberMe();
    }

    public long getTokenValidityMs(boolean rememberMe) {
        if (rememberMe) {
            return this.tokenValidityInMillisecondsForRememberMe;
        } else {
            return this.tokenValidityInMilliseconds;
        }
    }

    public String createToken(Authentication authentication, boolean rememberMe) {

        String authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date validity = new Date(now + getTokenValidityMs(rememberMe));

        return Jwts.builder()
            .setSubject(authentication.getName())
            .claim(AUTHORITIES_KEY, authorities)
            .signWith(key, SignatureAlgorithm.HS512)
            .setExpiration(validity)
            .compact();
    }

    public TokenAuthData getAuthData(String token) {

        Claims claims = Jwts.parser()
            .setSigningKey(key)
            .parseClaimsJws(token)
            .getBody();

        List<String> authorities = Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
            .collect(Collectors.toList());

        return new TokenAuthData(claims.getSubject(), authorities, token);
    }

    public Authentication getAuthentication(String token) {

        TokenAuthData data = getAuthData(token);

        Collection<? extends GrantedAuthority> authorities = data.getAuthorities()
            .stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        User principal = new User(data.getUser(), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().setSigningKey(key).parseClaimsJws(authToken);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT signature.");
            log.trace("Invalid JWT signature trace: {}", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT token.");
            log.trace("Expired JWT token trace: {}", e);
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT token.");
            log.trace("Unsupported JWT token trace: {}", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT token compact of handler are invalid.");
            log.trace("JWT token compact of handler are invalid trace: {}", e);
        }
        return false;
    }
}
