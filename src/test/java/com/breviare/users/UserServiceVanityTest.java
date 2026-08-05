package com.breviare.users;

import com.breviare.blocklist.BlocklistRepository;
import com.breviare.common.BreviareException;
import com.breviare.links.LinkValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceVanityTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BlocklistRepository blocklistRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        // Real LinkValidationService rather than a mock: it is a final-ish concrete class that
        // Mockito cannot mock on this JDK, and the blank Safe Browsing key makes it hermetic.
        // Mirrors the setup in LinkValidationServiceTest.
        LinkValidationService linkValidationService = new LinkValidationService(
                RestClient.builder(), blocklistRepository, "", "https://brvr.io");
        userService = new UserService(userRepository, linkValidationService);
    }

    // --- vanity destination validation ---

    @Test
    void rejectsVanityDestinationWithNonHttpScheme() {
        User user = userWithUsername("scriptor");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile(
                user.getId(), new UpdateProfileRequest(null, "javascript:alert(1)")))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsVanityDestinationPointingBackAtOwnDomain() {
        User user = userWithUsername("scriptor");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile(
                user.getId(), new UpdateProfileRequest(null, "https://brvr.io/abc123")))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void rejectsBlocklistedVanityDestination() {
        User user = userWithUsername("scriptor");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(blocklistRepository.existsByDomain("malware.test")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(
                user.getId(), new UpdateProfileRequest(null, "https://malware.test/x")))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("REJECTED");
    }

    @Test
    void acceptsCleanVanityDestination() {
        User user = userWithUsername("scriptor");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(blocklistRepository.existsByDomain("example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateProfile(
                user.getId(), new UpdateProfileRequest(null, "https://example.com/me"));

        assertThat(result.getVanityDestination()).isEqualTo("https://example.com/me");
        assertThat(result.getVanityDestinationChangeCountThisMonth()).isEqualTo(1);
    }

    @Test
    void clearingVanityDestinationSkipsValidation() {
        User user = userWithUsername("scriptor");
        user.setVanityDestination("https://example.com/old");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // An empty string means "clear it" — it would fail URL parsing if it reached the validator.
        User result = userService.updateProfile(user.getId(), new UpdateProfileRequest(null, ""));

        assertThat(result.getVanityDestination()).isNull();
    }

    @Test
    void vanityDestinationStillRequiresAClaimedUsername() {
        User user = userWithoutUsername();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile(
                user.getId(), new UpdateProfileRequest(null, "https://example.com")))
                .isInstanceOf(BreviareException.class)
                .hasMessageContaining("claim a username");
    }

    @Test
    void rateLimitIsCheckedBeforeValidation() {
        User user = userWithUsername("scriptor");
        user.setVanityDestinationChangeCountThisMonth(5);
        user.setVanityDestinationChangedAt(Instant.now());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile(
                user.getId(), new UpdateProfileRequest(null, "https://example.com")))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("RATE_LIMITED");
    }

    // --- concurrent username claim races ---

    @Test
    void concurrentClaimLosingTheRaceGetsConflictNotServerError() {
        User user = userWithoutUsername();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        // Pre-check passes: the competing claim commits between this check and our flush.
        when(userRepository.existsByUsername("scriptor")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("users_username_key"));

        assertThatThrownBy(() -> userService.claimUsername(user.getId(), "scriptor"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("CONFLICT");
    }

    @Test
    void concurrentUsernameChangeViaProfileUpdateGetsConflict() {
        User user = userWithUsername("olduser");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("scriptor")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("users_username_key"));

        assertThatThrownBy(() -> userService.updateProfile(
                user.getId(), new UpdateProfileRequest("scriptor", null)))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("CONFLICT");
    }

    @Test
    void uncontendedClaimSucceeds() {
        User user = userWithoutUsername();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("scriptor")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.claimUsername(user.getId(), "scriptor");

        assertThat(result.getUsername()).isEqualTo("scriptor");
    }

    @Test
    void claimIsRejectedWhenPreCheckAlreadySeesTheUsername() {
        User user = userWithoutUsername();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("scriptor")).thenReturn(true);

        assertThatThrownBy(() -> userService.claimUsername(user.getId(), "scriptor"))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("CONFLICT");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void unrelatedIntegrityViolationsStillSurfaceAsConflict() {
        // Documents a known limitation: saveOrConflict does not inspect which constraint failed,
        // so any integrity violation on this save is reported as a username conflict.
        User user = userWithUsername("scriptor");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(blocklistRepository.existsByDomain("example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("some_other_constraint"));

        assertThatThrownBy(() -> userService.updateProfile(
                user.getId(), new UpdateProfileRequest(null, "https://example.com")))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("CONFLICT");
    }

    // --- helpers ---

    private User userWithoutUsername() {
        User user = new User();
        setId(user, UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setProvider("google");
        user.setProviderUserId("g-1");
        return user;
    }

    private User userWithUsername(String username) {
        User user = userWithoutUsername();
        user.setUsername(username);
        return user;
    }

    // id is generated by the persistence layer, so tests set it reflectively.
    private static void setId(User user, UUID id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
