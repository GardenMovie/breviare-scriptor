package com.breviare.users;

import com.breviare.common.BreviaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class UserService {

    private static final int MAX_USERNAME_CHANGES_PER_MONTH = 1;
    private static final int MAX_VANITY_CHANGES_PER_MONTH = 5;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> BreviaException.notFound("User not found"));
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getById(userId);

        if (request.username() != null) {
            resetMonthlyCounterIfNeeded(user);
            if (user.getUsernameChangeCountThisMonth() >= MAX_USERNAME_CHANGES_PER_MONTH) {
                throw BreviaException.rateLimited("Username can only be changed once per month");
            }
            if (userRepository.existsByUsername(request.username())) {
                throw BreviaException.conflict("Username already taken");
            }
            user.setUsername(request.username());
            user.setUsernameChangedAt(Instant.now());
            user.setUsernameChangeCountThisMonth(user.getUsernameChangeCountThisMonth() + 1);
        }

        if (request.vanityDestination() != null) {
            resetMonthlyCounterIfNeeded(user);
            if (user.getVanityDestinationChangeCountThisMonth() >= MAX_VANITY_CHANGES_PER_MONTH) {
                throw BreviaException.rateLimited("Vanity destination can only be changed 5 times per month");
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

    private void resetMonthlyCounterIfNeeded(User user) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);

        if (user.getUsernameChangedAt() != null) {
            YearMonth changedMonth = YearMonth.from(user.getUsernameChangedAt().atOffset(ZoneOffset.UTC));
            if (!changedMonth.equals(currentMonth)) {
                user.setUsernameChangeCountThisMonth(0);
            }
        }

        if (user.getVanityDestinationChangedAt() != null) {
            YearMonth changedMonth = YearMonth.from(user.getVanityDestinationChangedAt().atOffset(ZoneOffset.UTC));
            if (!changedMonth.equals(currentMonth)) {
                user.setVanityDestinationChangeCountThisMonth(0);
            }
        }
    }
}
