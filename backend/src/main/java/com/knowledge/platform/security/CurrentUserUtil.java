package com.knowledge.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CurrentUserUtil {

    @Autowired
    private JwtTokenProvider tokenProvider;

    public String getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal != null ? principal.toString() : null;
    }

    /**
     * <audio>/<video> 标签无法携带 Authorization 头，允许通过 ?token= 传入 JWT。
     */
    public String getCurrentUserId(HttpServletRequest request) {
        String userId = getCurrentUserId();
        if (userId != null) {
            return userId;
        }
        String token = request.getParameter("token");
        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            return tokenProvider.getUserIdFromToken(token);
        }
        return null;
    }

    public boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
