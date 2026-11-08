package com.plantspot.backend.building;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.backend.common.NotFoundException;

@Service
@Transactional(readOnly = true)
public class BuildingService {

	private final BuildingRepository repository;

	public BuildingService(BuildingRepository repository) {
		this.repository = repository;
	}

	public List<String> dongs() {
		return repository.findDistinctDongs();
	}

	/** 동·지번으로 건물 찾기. 없으면 NotFoundException. */
	public List<Building> findByAddress(String dong, String jibun) {
		List<Building> found = repository.findByDongAndJibunOrderByAreaDesc(dong.trim(), jibun.trim());
		if (found.isEmpty()) {
			throw new NotFoundException(dong + " " + jibun + " 건물을 찾을 수 없습니다.");
		}
		return found;
	}

	/** 기준 건물(동·지번 중 가장 큰 건물) 주변 radius(m) 안의 건물. 가까운 순. */
	public Nearby nearby(String dong, String jibun, double radius) {
		Building base = findByAddress(dong, jibun).get(0);
		List<Neighbor> neighbors = repository.findInBox(base.getCenterX() - radius, base.getCenterX() + radius,
				base.getCenterY() - radius, base.getCenterY() + radius).stream()
				.filter(b -> !Objects.equals(b.getId(), base.getId()))
				.map(b -> Neighbor.of(base, b))
				.filter(n -> n.distance() <= radius)
				.sorted(Comparator.comparingDouble(Neighbor::distance))
				.toList();
		return new Nearby(base, neighbors);
	}

	public record Nearby(Building base, List<Neighbor> neighbors) {
	}

	/**
	 * 주변 건물 하나.
	 *
	 * @param distance 중심점 사이 거리(m)
	 * @param azimuth  기준 건물에서 본 방향(도). 북 0, 동 90, 남 180, 서 270 — 해 방위각과 같은 기준
	 */
	public record Neighbor(Building building, double distance, double azimuth) {

		static Neighbor of(Building base, Building other) {
			double dx = other.getCenterX() - base.getCenterX();
			double dy = other.getCenterY() - base.getCenterY();
			double azimuth = Math.toDegrees(Math.atan2(dx, dy));
			if (azimuth < 0) {
				azimuth += 360;
			}
			return new Neighbor(other, Math.hypot(dx, dy), azimuth);
		}
	}
}
