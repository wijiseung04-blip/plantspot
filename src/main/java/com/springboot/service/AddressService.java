package com.springboot.service;

import com.springboot.client.AddressApi;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.domain.Building;
import com.springboot.repository.BuildingRepository;
import com.springboot.domain.HeightSource;
import com.springboot.exception.UnavailableException;
import com.springboot.calculator.TmConverter;
import com.springboot.calculator.Wkt;

/**
 * 도로명주소(또는 지번주소)로 우리 건물 찾기: 브이월드 주소 검색 → 좌표(EPSG:5186) →
 * building 테이블에서 그 점을 품은 건물(없으면 30m 안 가장 가까운 건물).
 */
@Service
@Transactional(readOnly = true)
public class AddressService {

	private static final Logger log = LoggerFactory.getLogger(AddressService.class);
	/** 외곽선 안에 없을 때 가장 가까운 건물을 찾는 거리(m) */
	static final double NEAREST_LIMIT_M = 30;

	private final AddressApi api;
	private final BuildingRepository buildings;

	public AddressService(AddressApi api, BuildingRepository buildings) {
		this.api = api;
		this.buildings = buildings;
	}

	public List<AddressResult> search(String query) {
		String q = query.trim();
		if (q.length() < 2) {
			throw new IllegalArgumentException("주소를 2글자 이상 입력하세요.");
		}
		if (!api.hasKey()) {
			throw new UnavailableException("주소 검색 키(api.vworld.key)가 없어 동·지번으로 찾아 주세요.");
		}
		List<AddressApi.Candidate> found;
		try {
			found = api.search(q, "road");
			if (found.isEmpty()) {
				found = api.search(q, "parcel"); // 도로명이 없으면 지번주소로
			}
		} catch (IllegalStateException e) {
			log.warn("주소 검색 실패: {}", e.getMessage());
			throw new UnavailableException("주소 검색 서비스에 연결하지 못했습니다: " + e.getMessage());
		}
		// 아파트 단지처럼 같은 주소가 동마다 여러 번 오면 하나로 합친다 (주소 + 건물명 상세가 같으면 같은 결과)
		Map<String, AddressApi.Candidate> unique = new LinkedHashMap<>();
		for (AddressApi.Candidate c : found) {
			unique.putIfAbsent(c.roadAddress() + "|" + c.parcelAddress() + "|" + c.buildingDetail(), c);
		}
		log.info("주소 검색 '{}' → {}건 (중복 합쳐 {}건)", q, found.size(), unique.size());
		return unique.values().stream().map(c -> new AddressResult(c.roadAddress(), c.parcelAddress(), c.zipcode(),
				c.buildingName(), c.buildingDetail(), TmConverter.toLatLon(c.x(), c.y()).latitude(),
				TmConverter.toLatLon(c.x(), c.y()).longitude(), match(c.x(), c.y()).map(MatchedBuilding::of).orElse(null)))
				.toList();
	}

	/** 점(EPSG:5186)이 외곽선 안에 있는 건물, 없으면 30m 안 가장 가까운 건물. */
	public Optional<Building> match(double x, double y) {
		List<Building> near = buildings.findInBox(x - NEAREST_LIMIT_M, x + NEAREST_LIMIT_M, y - NEAREST_LIMIT_M,
				y + NEAREST_LIMIT_M);
		Optional<Building> inside = near.stream().filter(b -> contains(b.getFootprintWkt(), x, y))
				.min(Comparator.comparingDouble(Building::getArea));
		if (inside.isPresent()) {
			return inside;
		}
		return near.stream()
				.filter(b -> Math.hypot(b.getCenterX() - x, b.getCenterY() - y) <= NEAREST_LIMIT_M)
				.min(Comparator.comparingDouble(b -> Math.hypot(b.getCenterX() - x, b.getCenterY() - y)));
	}

	static boolean contains(String wkt, double x, double y) {
		boolean in = false;
		for (List<double[]> ring : Wkt.rings(wkt)) {
			// 링마다 짝홀 규칙 → 구멍은 자동으로 빠진다
			for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
				double[] a = ring.get(i);
				double[] b = ring.get(j);
				if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
					in = !in;
				}
			}
		}
		return in;
	}

	/** 주소 후보 하나. building 이 null 이면 우리 건물 데이터(사하구)에 없는 주소. */
	public record AddressResult(String roadAddress, String parcelAddress, String zipcode, String buildingName,
			String buildingDetail, double latitude, double longitude, MatchedBuilding building) {
	}

	public record MatchedBuilding(Long id, String dong, String jibun, double height, HeightSource heightSource,
			Integer groundFloors) {

		static MatchedBuilding of(Building b) {
			return new MatchedBuilding(b.getId(), b.getDong(), b.getJibun(), b.getHeight(), b.getHeightSource(),
					b.getGroundFloors());
		}
	}
}
