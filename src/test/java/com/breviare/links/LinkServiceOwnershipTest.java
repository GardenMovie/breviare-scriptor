package com.breviare.links;

import com.breviare.analytics.AnalyticsService;
import com.breviare.common.BreviareException;
import com.breviare.users.User;
import com.breviare.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkServiceOwnershipTest {

    @Mock
    private LinkRepository linkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LinkValidationService linkValidationService;
    @Mock
    private AnalyticsService analyticsService;

    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkService = new LinkService(linkRepository, userRepository, linkValidationService, analyticsService);
    }

    @Test
    void ownerCanFetchTheirOwnLink() {
        UUID ownerId = UUID.randomUUID();
        Link link = ownedLink(ownerId);
        when(linkRepository.findByCode("abc123")).thenReturn(Optional.of(link));

        Link result = linkService.getByCode("abc123", ownerId);

        assertThat(result).isSameAs(link);
    }

    @Test
    void otherUserCannotFetchSomeoneElsesLink() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Link link = ownedLink(ownerId);
        when(linkRepository.findByCode("abc123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> linkService.getByCode("abc123", otherUserId))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void anonymousRequesterGetsNotFoundRatherThanForbidden() {
        UUID ownerId = UUID.randomUUID();
        Link link = ownedLink(ownerId);
        when(linkRepository.findByCode("abc123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> linkService.getByCode("abc123", null))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void ownerlessLinkIsFetchableByAnyone() {
        Link link = freshLink();
        // no owner set
        when(linkRepository.findByCode("abc123")).thenReturn(Optional.of(link));

        Link result = linkService.getByCode("abc123", UUID.randomUUID());

        assertThat(result).isSameAs(link);
    }

    @Test
    void otherUserCannotUpdateSomeoneElsesLink() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Link link = ownedLink(ownerId);
        when(linkRepository.findByCode("abc123")).thenReturn(Optional.of(link));
        UpdateLinkRequest request = new UpdateLinkRequest("https://example.com/new", null, null);

        assertThatThrownBy(() -> linkService.update("abc123", request, otherUserId))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void otherUserCannotDeleteSomeoneElsesLink() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Link link = ownedLink(ownerId);
        when(linkRepository.findByCode("abc123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> linkService.delete("abc123", otherUserId))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void fetchingNonExistentCodeThrowsNotFound() {
        when(linkRepository.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.getByCode("missing", UUID.randomUUID()))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void expiredLinkThrowsGoneEvenForOwner() {
        UUID ownerId = UUID.randomUUID();
        Link link = ownedLink(ownerId);
        link.setExpired(true);
        when(linkRepository.findByCode("abc123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> linkService.getByCode("abc123", ownerId))
                .isInstanceOf(BreviareException.class)
                .extracting(e -> ((BreviareException) e).getCode())
                .isEqualTo("GONE");
    }

    private Link ownedLink(UUID ownerId) {
        Link link = freshLink();
        User owner = new User();
        setId(owner, ownerId);
        link.setOwner(owner);
        return link;
    }

    private Link freshLink() {
        Link link = new Link();
        link.setCode("abc123");
        link.setDestination("https://example.com");
        link.setLastClickedAt(Instant.now());
        return link;
    }

    private static void setId(User user, UUID id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
