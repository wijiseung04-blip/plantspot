package com.plantspot.backend.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.backend.building.Building;
import com.plantspot.backend.building.BuildingService;
import com.plantspot.backend.building.BuildingService.Nearby;
import com.plantspot.backend.building.HeightSource;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/** 건물 확인용 API. 좌표 x·y는 EPSG:5186(미터). */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

	private final BuildingService buildingService;

	public BuildingController(BuildingService buildingService) {
		this.buildingService = buildingService;
	}

	/** 예: GET /api/buildings?dong=하단동&jibun=1160-6 */
	@GetMapping
	public List<BuildingResponse> find(
			@RequestParam("dong") @NotBlank(message = "동을 입력하세요.") String dong,
			@RequestParam("jibun") @NotBlank(message = "지번을 입력하세요.") String jibun) {
		return buildingService.findByAddress(dong, jibun).stream().map(BuildingResponse::of).toList();
	}

	/** 동 선택 목록. GET /api/buildings/dongs */
	@GetMapping("/dongs")
	public List<String> dongs() {
		return buildingService.dongs();
	}

	/** 예: GET /api/buildings/nearby?dong=하단동&jibun=1160-6&radius=150 */
	@GetMapping("/nearby")
	public NearbyResponse nearby(
			@RequestParam("dong") @NotBlank(message = "동을 입력하세요.") String dong,
			@RequestParam("jibun") @NotBlank(message = "지번을 입력하세요.") String jibun,
			@RequestParam(name = "radius", defaultValue = "150") @DecimalMin(value = "1", message = "반경은 1m 이상이어야 합니다.")
			@DecimalMax(value = "1000", message = "반경은 1000m 이하여야 합니다.") double radius) {
		Nearby result = buildingService.nearby(dong, jibun, radius);
		return new NearbyResponse(BuildingResponse.of(result.base()), radius, result.neighbors().size(),
				result.neighbors().stream()
						.map(n -> new NeighborResponse(BuildingResponse.of(n.building()), round(n.distance()),
								round(n.azimuth())))
						.toList());
	}

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	public record BuildingResponse(Long id, String dong, String jibun, String buildingName, String dongLabel, String usage,
			Integer groundFloors, double height, HeightSource heightSource, double area, double x, double y,
			double latitude, double longitude) {

		static BuildingResponse of(Building b) {
			return new BuildingResponse(b.getId(), b.getDong(), b.getJibun(), b.getBuildingName(), b.getDongLabel(), b.getUsage(),
					b.getGroundFloors(), b.getHeight(), b.getHeightSource(), round(b.getArea()), round(b.getCenterX()),
					round(b.getCenterY()), Math.round(b.getLatitude() * 1e6) / 1e6,
					Math.round(b.getLongitude() * 1e6) / 1e6);
		}
	}

	/** azimuth: 기준 건물에서 본 방향(북 0, 동 90, 남 180, 서 270). */
	public record NeighborResponse(BuildingResponse building, double distance, double azimuth) {
	}

	public record NearbyResponse(BuildingResponse base, double radius, int count, List<NeighborResponse> neighbors) {
	}
}
