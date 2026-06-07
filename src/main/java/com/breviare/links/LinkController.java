package com.breviare.links;

import com.breviare.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;

    @Value("${brevia.base-url}")
    private String baseUrl;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LinkResponse>> create(
            @Valid @RequestBody CreateLinkRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        UUID ownerId = principal != null ? UUID.fromString(principal.getUsername()) : null;
        Link link = linkService.create(request, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(LinkResponse.from(link, baseUrl)));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<LinkResponse>> get(
            @PathVariable String code,
            @AuthenticationPrincipal UserDetails principal
    ) {
        UUID requesterId = principal != null ? UUID.fromString(principal.getUsername()) : null;
        Link link = linkService.getByCode(code, requesterId);
        return ResponseEntity.ok(ApiResponse.ok(LinkResponse.from(link, baseUrl)));
    }

    @PatchMapping("/{code}")
    public ResponseEntity<ApiResponse<LinkResponse>> update(
            @PathVariable String code,
            @Valid @RequestBody UpdateLinkRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        UUID requesterId = UUID.fromString(principal.getUsername());
        Link link = linkService.update(code, request, requesterId);
        return ResponseEntity.ok(ApiResponse.ok(LinkResponse.from(link, baseUrl)));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(
            @PathVariable String code,
            @AuthenticationPrincipal UserDetails principal
    ) {
        UUID requesterId = UUID.fromString(principal.getUsername());
        linkService.delete(code, requesterId);
        return ResponseEntity.noContent().build();
    }
}
