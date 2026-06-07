package com.breviare.users;

import com.breviare.common.ApiResponse;
import com.breviare.links.LinkResponse;
import com.breviare.links.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final LinkService linkService;

    public UserController(UserService userService, LinkService linkService) {
        this.userService = userService;
        this.linkService = linkService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@AuthenticationPrincipal UserDetails principal) {
        User user = userService.getById(UUID.fromString(principal.getUsername()));
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        User user = userService.updateProfile(UUID.fromString(principal.getUsername()), request);
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal UserDetails principal) {
        userService.deleteAccount(UUID.fromString(principal.getUsername()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/links")
    public ResponseEntity<ApiResponse<List<LinkResponse>>> listLinks(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean includeExpired,
            @RequestParam(required = false) String cursor
    ) {
        var page = linkService.listForOwner(UUID.fromString(principal.getUsername()), limit, includeExpired, cursor);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
