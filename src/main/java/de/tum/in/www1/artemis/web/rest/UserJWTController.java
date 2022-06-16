package de.tum.in.www1.artemis.web.rest;

import java.util.Optional;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.UserNotActivatedException;
import de.tum.in.www1.artemis.security.jwt.JWTFilter;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.CaptchaRequiredException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

/**
 * Controller to authenticate users.
 */
@RestController
@RequestMapping("/api")
public class UserJWTController {

    private static final Logger log = LoggerFactory.getLogger(UserJWTController.class);

    private static final String ENTITY_NAME = "userJWTController";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TokenProvider tokenProvider;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final Optional<SAML2Service> saml2Service;

    private final UserRepository userRepository;

    @Value("${artemis.personal-access-token.max-lifetime}")
    private long personalAccessTokenMaxLifetimeMilliseconds;

    public UserJWTController(TokenProvider tokenProvider, AuthenticationManagerBuilder authenticationManagerBuilder, Optional<SAML2Service> saml2Service,
            UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.saml2Service = saml2Service;
        this.userRepository = userRepository;
    }

    /**
     * Authorizes a User
     * @param loginVM user credentials View Mode
     * @param userAgent User Agent
     * @return a JWT Token if the authorization is successful
     */
    @PostMapping("/authenticate")
    public ResponseEntity<JWTToken> authorize(@Valid @RequestBody LoginVM loginVM, @RequestHeader("User-Agent") String userAgent) {

        var username = loginVM.getUsername();
        var password = loginVM.getPassword();
        SecurityUtils.checkUsernameAndPasswordValidity(username, password);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

        try {
            authenticationToken.setDetails(Pair.of("userAgent", userAgent));
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            boolean rememberMe = loginVM.isRememberMe() != null && loginVM.isRememberMe();
            String jwt = tokenProvider.createToken(authentication, rememberMe);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
            return new ResponseEntity<>(new JWTToken(jwt), httpHeaders, HttpStatus.OK);
        }
        catch (CaptchaRequiredException ex) {
            log.warn("CAPTCHA required in JIRA during login for user {}", loginVM.getUsername());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", ex.getMessage()).build();
        }
    }

    /**
     * Authorizes a User logged in with SAML2
     *
     * @param body the body of the request. "true" to remember the user.
     * @return a JWT Token if the authorization is successful
     */
    @PostMapping("/saml2")
    public ResponseEntity<JWTToken> authorizeSAML2(@RequestBody final String body) {
        if (saml2Service.isEmpty()) {
            throw new AccessForbiddenException("SAML2 is disabled");
        }

        final boolean rememberMe = Boolean.parseBoolean(body);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof final Saml2AuthenticatedPrincipal principal)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        log.debug("SAML2 authentication: {}", authentication);

        try {
            authentication = saml2Service.get().handleAuthentication(principal);
        }
        catch (UserNotActivatedException e) {
            // If the exception is not caught a 401 is returned.
            // That does not match the actual reason and would trigger authentication in the client
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", e.getMessage()).build();
        }

        final String jwt = tokenProvider.createToken(authentication, rememberMe);
        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
        return new ResponseEntity<>(new JWTToken(jwt), httpHeaders, HttpStatus.OK);
    }

    /**
     * Generates a personal access token (JWT token) for the current user.
     *
     * @param lifetimeMilliseconds the lifetime for the JWT token
     * @return a JWT Token
     */
    @PostMapping("/personal-access-token")
    @FeatureToggle(Feature.PersonalAccessTokens)
    public ResponseEntity<JWTToken> getPersonalAccessToken(@RequestBody Long lifetimeMilliseconds) {
        User user = userRepository.getUser();

        if (!user.getActivated()) {
            throw new UserNotActivatedException("User was disabled!");
        }

        if (lifetimeMilliseconds > this.personalAccessTokenMaxLifetimeMilliseconds) {
            long difference = this.personalAccessTokenMaxLifetimeMilliseconds - lifetimeMilliseconds;
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidPATLifetime",
                    "Requested token lifetime exceeds the maximum lifetime for personal access tokens by " + difference + " ms")).build();
        }

        // Automatically returns 401 if not fully authorized
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final String jwt = tokenProvider.createTokenWithCustomDuration(authentication, lifetimeMilliseconds);
        return new ResponseEntity<>(new JWTToken(jwt), HttpStatus.OK);
    }

    /**
     * Returns the maximum lifetime for personal access tokens.
     * @return the maximum lifetime for personal access tokens
     */
    @GetMapping("/personal-access-token/maximum-lifetime")
    @FeatureToggle(Feature.PersonalAccessTokens)
    public ResponseEntity<Long> getPersonalAccessTokenMaxLifetime() {
        return new ResponseEntity<>(this.personalAccessTokenMaxLifetimeMilliseconds, HttpStatus.OK);
    }

    /**
     * Object to return as body in JWT Authentication.
     */
    public static class JWTToken {

        private String idToken;

        /**
         * Make Jackson happy
         */
        JWTToken() {
        }

        JWTToken(String idToken) {
            this.setIdToken(idToken);
        }

        @JsonProperty("id_token")
        public String getIdToken() {
            return idToken;
        }

        void setIdToken(String idToken) {
            this.idToken = idToken;
        }
    }
}
