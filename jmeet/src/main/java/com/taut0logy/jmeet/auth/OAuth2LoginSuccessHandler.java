package com.taut0logy.jmeet.auth;

import com.taut0logy.jmeet.config.ClientProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final ClientProperties clientProperties;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public OAuth2LoginSuccessHandler(AuthService authService, ClientProperties clientProperties) {
        this.authService = authService;
        this.clientProperties = clientProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String providerUid = oauth2User.getName();
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        AppUser user = authService.findOrCreateOAuthUser("google", providerUid, email, name);

        AuthPrincipal principal = AuthPrincipal.from(user);
        Authentication normalized = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        ((org.springframework.security.authentication.AbstractAuthenticationToken) normalized)
                .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(normalized);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        String redirectPath = user.getPasswordHash() == null ? "/set-password" : "/dashboard";
        response.sendRedirect(clientProperties.baseUrl() + redirectPath);
    }
}
