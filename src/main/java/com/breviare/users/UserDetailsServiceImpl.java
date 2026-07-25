package com.breviare.users;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Spring Security calls this with the subject from the JWT, which is the user's UUID
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        // Sign-in is delegated to Google, so there is no password to check. Spring Security's
        // User rejects a null password, so pass an empty placeholder that can never match.
        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                "",
                Collections.emptyList()
        );
    }
}
