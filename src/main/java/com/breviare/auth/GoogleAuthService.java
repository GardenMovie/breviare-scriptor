package com.breviare.auth;

import com.breviare.common.BreviareException;
import com.breviare.users.User;
import com.breviare.users.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.io.IOException;
import java.util.Collections;

@Service
public class GoogleAuthService {

    private static final String PROVIDER = "google";

    private final UserRepository userRepository;
    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(
            UserRepository userRepository,
            @Value("${breviare.oauth.google.client-id}") String clientId
    ) {
        this.userRepository = userRepository;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Transactional
    public User verifyAndResolveUser(String idTokenString) {
        GoogleIdToken idToken = verify(idTokenString);
        GoogleIdToken.Payload payload = idToken.getPayload();
        String googleUserId = payload.getSubject();
        String email = payload.getEmail();

        return userRepository.findByProviderAndProviderUserId(PROVIDER, googleUserId)
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setProvider(PROVIDER);
                    user.setProviderUserId(googleUserId);
                    return userRepository.save(user);
                });
    }

    private GoogleIdToken verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BreviareException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid Google ID token");
            }
            return idToken;
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new BreviareException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid Google ID token");
        }
    }
}
