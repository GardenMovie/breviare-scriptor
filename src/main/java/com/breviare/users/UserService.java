package com.breviare.users;

import com.breviare.common.BreviareException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class UserService {

    private static final int MAX_VANITY_CHANGES_PER_MONTH = 5;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> BreviareException.notFound("User not found"));
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getById(userId);

        if (request.username() != null) {
            if (changedThisMonth(user.getUsernameChangedAt())) {
                throw BreviareException.rateLimited("Username can only be changed once per month");
            }
            if (userRepository.existsByUsername(request.username())) {
                throw BreviareException.conflict("Username already taken");
            }
            user.setUsername(request.username());
            user.setUsernameChangedAt(Instant.now());
        }

        if (request.vanityDestination() != null) {
            resetVanityCounterIfNeeded(user);
            if (user.getVanityDestinationChangeCountThisMonth() >= MAX_VANITY_CHANGES_PER_MONTH) {
                throw BreviareException.rateLimited("Vanity destination can only be changed 5 times per month");
            }
            user.setVanityDestination(request.vanityDestination().isEmpty() ? null : request.vanityDestination());
            user.setVanityDestinationChangedAt(Instant.now());
            user.setVanityDestinationChangeCountThisMonth(user.getVanityDestinationChangeCountThisMonth() + 1);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        userRepository.deleteById(userId);
    }

    private boolean changedThisMonth(Instant changedAt) {
        if (changedAt == null) {
            return false;
        }
        YearMonth changedMonth = YearMonth.from(changedAt.atOffset(ZoneOffset.UTC));
        return changedMonth.equals(YearMonth.now(ZoneOffset.UTC));
    }

    private void resetVanityCounterIfNeeded(User user) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);

        if (user.getVanityDestinationChangedAt() != null) {
            YearMonth changedMonth = YearMonth.from(user.getVanityDestinationChangedAt().atOffset(ZoneOffset.UTC));
            if (!changedMonth.equals(currentMonth)) {
                user.setVanityDestinationChangeCountThisMonth(0);
            }
        }
    }
}
