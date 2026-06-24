package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.dto.AssetResponse;
import com.serverscout.dto.TopologyResponse;
import com.serverscout.service.AssetService;
import com.serverscout.service.AttackSurfaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final AttackSurfaceService attackSurfaceService;

    private String getUsername(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping
    public ApiResponse<?> listAssets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long taskId,
            Authentication auth) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (taskId != null) {
            // Task-scoped asset query is backed by ScanAssetMapping query root.
            // Avoid applying Asset.updatedAt sort here, otherwise JPQL sort path can be invalid.
            pageable = PageRequest.of(page, size);
        }
        String username = getUsername(auth);
        boolean admin = isAdmin(auth);
        var result = assetService.listAssets(keyword, status, taskId, pageable, username, admin);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<AssetResponse> getAsset(@PathVariable Long id) {
        return ApiResponse.success(assetService.getAssetDetail(id));
    }

    @PutMapping("/{id}/tags")
    public ApiResponse<Void> updateTags(@PathVariable Long id, @RequestBody List<String> tags) {
        assetService.updateTags(id, tags);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/merge")
    public ApiResponse<AssetResponse> mergeAssets(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> srcInts = (List<Integer>) body.get("sourceIds");
        List<Long> sourceIds = srcInts.stream().map(Long::valueOf).toList();
        Long targetId = Long.valueOf(((Number) body.get("targetId")).longValue());
        return ApiResponse.success(assetService.getAssetDetail(
                assetService.mergeAssets(sourceIds, targetId).getId()));
    }

    @GetMapping("/topology")
    public ApiResponse<TopologyResponse> getTopology(Authentication auth) {
        String username = getUsername(auth);
        boolean admin = isAdmin(auth);
        return ApiResponse.success(assetService.getTopology(username, admin));
    }

    @GetMapping("/attack-surface")
    public ApiResponse<Map<String, Object>> getAttackSurface(Authentication auth) {
        String username = getUsername(auth);
        boolean admin = isAdmin(auth);
        return ApiResponse.success(attackSurfaceService.buildAttackSurfaceMap(username, admin));
    }
}
