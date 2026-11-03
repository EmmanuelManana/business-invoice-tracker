package com.invoicetracker.api.security;

import com.invoicetracker.application.common.CurrentUser;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers declare a {@link CurrentUser} parameter. The tenant id comes only from the
 * signed token, so a client cannot act on another business by changing request data.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    static final String BUSINESS_CLAIM = "bid";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            throw new InsufficientAuthenticationException("Not authenticated with a JWT");
        }
        Jwt jwt = token.getToken();
        String businessId = jwt.getClaimAsString(BUSINESS_CLAIM);
        if (businessId == null || jwt.getSubject() == null) {
            throw new InsufficientAuthenticationException("Token is missing required claims");
        }
        try {
            return new CurrentUser(
                    UUID.fromString(jwt.getSubject()), UUID.fromString(businessId), jwt.getClaimAsString("email"));
        } catch (IllegalArgumentException e) {
            throw new InsufficientAuthenticationException("Token claims are malformed", e);
        }
    }
}
