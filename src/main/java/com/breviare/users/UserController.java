package com.breviare.users;

import com.breviare.common.ApiResponse;
import com.breviare.links.LinkResponse;
import com.breviare.links.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@PathVariable UUID id) {
        User user = userService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        User user = userService.updateProfile(id, request);
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        userService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/links")
    public ResponseEntity<ApiResponse<List<LinkResponse>>> listLinks(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean includeExpired,
            @RequestParam(required = false) String cursor
    ) {
        var page = linkService.listForOwner(id, limit, includeExpired, cursor);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
