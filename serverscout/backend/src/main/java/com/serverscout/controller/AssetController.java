package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.dto.AssetResponse;
import com.serverscout.dto.TopologyResponse;
import com.serverscout.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public ApiResponse<?> listAssets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        var result = assetService.listAssets(keyword, status, pageable);
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
    public ApiResponse<TopologyResponse> getTopology() {
        return ApiResponse.success(assetService.getTopology());
    }
}
