package com.erp.utils.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class SecurityMockProvider {

    /**
     * Створює об'єкт автентифікації, який імітує логін через браузер (OIDC),
     * на основі реального JWT токена з AuthService.
     */
    public static Authentication getMockAuthentication(String token) {
        DecodedJWT decodedJWT = JWT.decode(token);

        // 1. Витягуємо дані з токена (claims)
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", decodedJWT.getSubject());
        claims.put("preferred_username", decodedJWT.getClaim("preferred_username").asString());

        // Витягуємо ролі та пермішени точно так, як вони лежать у JWT
        List<String> roles = decodedJWT.getClaim("role").asList(String.class);
        List<String> permissions = decodedJWT.getClaim("permissions").asList(String.class);

        claims.put("role", roles != null ? roles : List.of());
        claims.put("permissions", permissions != null ? permissions : List.of());

        // 2. Створюємо OidcIdToken (необхідний для DefaultOidcUser)
        OidcIdToken idToken = new OidcIdToken(
                token,
                decodedJWT.getIssuedAtAsInstant() != null ? decodedJWT.getIssuedAtAsInstant() : Instant.now(),
                decodedJWT.getExpiresAtAsInstant(),
                claims
        );

        // 3. Формуємо Granted Authorities (додаємо ROLE_ префікс для Spring Security)
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            authorities.addAll(roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList()));
        }
        authorities.add(new SimpleGrantedAuthority("OIDC_USER"));

        // 4. Створюємо Principal (DefaultOidcUser)
        DefaultOidcUser principal = new DefaultOidcUser(authorities, idToken);

        // 5. Повертаємо токен, який CustomPermissionEvaluator зможе скастити
        return new OAuth2AuthenticationToken(principal, authorities, "tk-admin");
    }
}