package com.breviare.users;

import com.breviare.common.BreviareException;
import com.breviare.links.LinkValidationService;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final LinkValidationService linkValidationService;

    public UserService(UserRepository userRepository, LinkValidationService linkValidationService) {
        this.userRepository = userRepository;
        this.linkValidationService = linkValidationService;
    }

    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> BreviareException.notFound("User not found"));
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    @Transactional
    public User claimUsername(UUID userId, String username) {
        User user = getById(userId);
        if (user.getUsername() != null) {
            throw BreviareException.conflict("Username already claimed; use the profile update endpoint to change it");
        }
        if (userRepository.existsByUsername(username)) {
            throw BreviareException.conflict("Username already taken");
        }
        user.setUsername(username);
        return saveOrConflict(user);
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
            if (user.getUsername() == null) {
                throw BreviareException.badRequest("You must claim a username before setting a vanity destination");
            }
            resetVanityCounterIfNeeded(user);
            if (user.getVanityDestinationChangeCountThisMonth() >= MAX_VANITY_CHANGES_PER_MONTH) {
                throw BreviareException.rateLimited("Vanity destination can only be changed 5 times per month");
            }
            boolean clearing = request.vanityDestination().isEmpty();
            if (!clearing) {
                linkValidationService.validate(request.vanityDestination());
            }
            user.setVanityDestination(clearing ? null : request.vanityDestination());
            // Ideally we would only update this if its the first change in the month, so its 30 days from the first change
            user.setVanityDestinationChangedAt(Instant.now());
            user.setVanityDestinationChangeCountThisMonth(user.getVanityDestinationChangeCountThisMonth() + 1);
        }

        return saveOrConflict(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        userRepository.deleteById(userId);
    }

    // The existsByUsername pre-checks above leave a window where two concurrent claims both pass.
    // The DB unique constraint is the real guard; flush here so it surfaces as a 409 rather than
    // escaping as a DataIntegrityViolationException at commit time (which the handler maps to a 500).
    private User saveOrConflict(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw BreviareException.conflict("Username already taken");
        }
    }

    private boolean changedThisMonth(Instant changedAt) {
        if (changedAt == null) {
            return false;
        }
        YearMonth changedMonth = YearMonth.from(changedAt.atOffset(ZoneOffset.UTC));
        return changedMonth.equals(YearMonth.now(ZoneOffset.UTC));
    }

    // This is wrong, reset happens monthly not 30 days
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
