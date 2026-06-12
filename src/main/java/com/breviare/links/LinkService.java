package com.breviare.links;

import com.breviare.common.BreviareException;
import com.breviare.users.User;
import com.breviare.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LinkService {

    private static final String BASE52 = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE_RETRIES = 10;

    private final LinkRepository linkRepository;
    private final UserRepository userRepository;
    private final LinkValidationService linkValidationService;
    private final SecureRandom random = new SecureRandom();

    @Value("${breviare.base-url}")
    private String baseUrl;

    public LinkService(LinkRepository linkRepository, UserRepository userRepository, LinkValidationService linkValidationService) {
        this.linkRepository = linkRepository;
        this.userRepository = userRepository;
        this.linkValidationService = linkValidationService;
    }

    @Transactional
    public Link create(CreateLinkRequest request, UUID ownerId) {
        linkValidationService.validate(request.destination());
        Link link = new Link();
        link.setCode(generateUniqueCode());
        link.setDestination(request.destination());

        if (ownerId != null) {
            User owner = userRepository.getReferenceById(ownerId);
            link.setOwner(owner);
            if (request.inactivityTtlDays() != null) link.setInactivityTtlDays(request.inactivityTtlDays());
            if (request.absoluteExpiresAt() != null) {
                if (request.absoluteExpiresAt().isBefore(Instant.now())) {
                    throw BreviareException.badRequest("absolute_expires_at must be in the future");
                }
                link.setAbsoluteExpiresAt(request.absoluteExpiresAt());
            }
        }

        return linkRepository.save(link);
    }

    public Link getByCode(String code, UUID requesterId) {
        Link link = linkRepository.findByCode(code).orElseThrow(() -> BreviareException.notFound("Link not found"));
        checkExpired(link);
        if (link.getOwner() != null) {
            if (requesterId == null) throw BreviareException.notFound("Link not found");
            if (!link.getOwner().getId().equals(requesterId)) throw BreviareException.forbidden("Not the owner");
        }
        return link;
    }

    public Optional<Link> findByCodeForRedirect(String code) {
        return linkRepository.findByCode(code);
    }

    @Transactional
    public Link update(String code, UpdateLinkRequest request, UUID requesterId) {
        if (request.destination() != null) linkValidationService.validate(request.destination());
        Link link = getByCode(code, requesterId);
        if (request.destination() != null) link.setDestination(request.destination());
        if (request.inactivityTtlDays() != null) link.setInactivityTtlDays(request.inactivityTtlDays());
        if (request.absoluteExpiresAt() != null) link.setAbsoluteExpiresAt(request.absoluteExpiresAt());
        return linkRepository.save(link);
    }

    @Transactional
    public void delete(String code, UUID requesterId) {
        Link link = getByCode(code, requesterId);
        linkRepository.delete(link);
    }

    public List<LinkResponse> listForOwner(UUID ownerId, int limit, boolean includeExpired, String cursor) {
        int page = parseCursor(cursor);
        var links = linkRepository.findByOwnerPaged(ownerId, includeExpired, PageRequest.of(page, Math.min(limit, 100)));
        return links.stream().map(l -> LinkResponse.from(l, baseUrl)).toList();
    }

    // Analytics seem to be a little weak here
    @Transactional
    public void recordClick(Link link) {
        link.setLastClickedAt(Instant.now());
        link.setClickCount(link.getClickCount() + 1);
        linkRepository.save(link);
    }

    @Scheduled(cron = "0 0 * * * *") // hourly
    @Transactional
    public void expireSweep() {
        Instant now = Instant.now();
        linkRepository.expireByAbsoluteTtl(now);
        // For inactivity, we use the minimum default TTL (30d) as a sweep window;
        // lazy per-request checks handle individual TTL variations.
        linkRepository.expireByInactivity(now, now.minusSeconds(30L * 86400));
    }

    private void checkExpired(Link link) {
        if (link.isExpired()) throw BreviareException.gone("Link has expired");
        Instant now = Instant.now();
        boolean inactivityExpired = link.getLastClickedAt()
                .plusSeconds((long) link.getInactivityTtlDays() * 86400)
                .isBefore(now);
        boolean absoluteExpired = link.getAbsoluteExpiresAt() != null && link.getAbsoluteExpiresAt().isBefore(now);
        if (inactivityExpired || absoluteExpired) throw BreviareException.gone("Link has expired");
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_CODE_RETRIES; i++) {
            String code = randomCode();
            if (linkRepository.findByCode(code).isEmpty()) return code;
        }
        throw BreviareException.serviceUnavailable("Could not generate a unique short code, please try again");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) sb.append(BASE52.charAt(random.nextInt(BASE52.length())));
        return sb.toString();
    }

    // Convoluted but allows for opaque cursors in the future
    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try { return Integer.parseInt(cursor); } catch (NumberFormatException e) { return 0; }
    }
}
