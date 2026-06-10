package com.breviare.links;

import com.breviare.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;

    @Value("${breviare.base-url}")
    private String baseUrl;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LinkResponse>> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = linkService.create(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(LinkResponse.from(link, baseUrl)));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<LinkResponse>> get(@PathVariable String code) {
        Link link = linkService.getByCode(code, null);
        return ResponseEntity.ok(ApiResponse.ok(LinkResponse.from(link, baseUrl)));
    }

    @PatchMapping("/{code}")
    public ResponseEntity<ApiResponse<LinkResponse>> update(
            @PathVariable String code,
            @Valid @RequestBody UpdateLinkRequest request
    ) {
        Link link = linkService.update(code, request, null);
        return ResponseEntity.ok(ApiResponse.ok(LinkResponse.from(link, baseUrl)));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        linkService.delete(code, null);
        return ResponseEntity.noContent().build();
    }
}
