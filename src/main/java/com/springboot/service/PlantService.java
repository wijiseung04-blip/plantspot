package com.springboot.service;

import com.springboot.calculator.PlantConverter;
import com.springboot.domain.PlantSource;
import com.springboot.domain.PlantSpecies;
import com.springboot.event.PlantEvents;
import com.springboot.repository.PlantSpeciesRepository;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.exception.NotFoundException;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 식물 검색(비회원 가능)과 관리자 변환표 관리. */
@Service
@Transactional(readOnly = true)
public class PlantService {

	private static final Logger log = LoggerFactory.getLogger(PlantService.class);

	private final PlantSpeciesRepository repository;
	private final org.springframework.context.ApplicationEventPublisher events;

	public PlantService(PlantSpeciesRepository repository, org.springframework.context.ApplicationEventPublisher events) {
		this.repository = repository;
		this.events = events;
	}

	public PlantPage search(String keyword, String light, String level, int page, int size) {
		Page<PlantSpecies> p = repository.search(blank(keyword), blank(light), blank(level),
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
		return new PlantPage(p.getContent().stream().map(PlantResponse::of).toList(), p.getNumber(), p.getSize(),
				p.getTotalElements());
	}

	public PlantResponse get(Long id) {
		return PlantResponse.of(load(id));
	}

	public PlantSpecies load(Long id) {
		return repository.findById(id).orElseThrow(() -> new NotFoundException("식물을 찾을 수 없습니다."));
	}

	public List<PlantSpecies> all() {
		return repository.findAll();
	}

	public long count() {
		return repository.count();
	}

	// ----- 관리자 -----

	@Transactional
	public PlantResponse adjust(Long id, ConversionRequest req) {
		req.check();
		PlantSpecies p = load(id);
		p.adjust(req.name(), req.toConverted());
		events.publishEvent(new PlantEvents.PlantChanged(id));
		log.info("변환표 수정: {} {}", id, p.getName());
		return PlantResponse.of(p);
	}

	@Transactional
	public PlantResponse reset(Long id) {
		PlantSpecies p = load(id);
		p.resetConversion();
		events.publishEvent(new PlantEvents.PlantChanged(id));
		return PlantResponse.of(p);
	}

	@Transactional
	public PlantResponse createManual(ConversionRequest req) {
		req.check();
		if (req.name() == null || req.name().isBlank()) {
			throw new IllegalArgumentException("식물 이름을 입력하세요.");
		}
		PlantSpecies p = PlantSpecies.manual(req.name().trim());
		p.adjust(req.name(), req.toConverted());
		log.info("식물 직접 추가: {}", p.getName());
		return PlantResponse.of(repository.save(p));
	}

	@Transactional
	public void delete(Long id) {
		PlantSpecies p = load(id);
		events.publishEvent(new PlantEvents.PlantDeleting(id));
		repository.delete(p);
	}

	private static String blank(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	// ----- 요청·응답 -----

	/** 관리자 변환표 입력. [필수: Bean Validation] */
	public record ConversionRequest(
			@Size(max = 100, message = "이름은 100자 이하입니다.") String name,
			@NotNull(message = "최소 lux를 입력하세요.") @Min(value = 0, message = "lux는 0 이상입니다.")
			@Max(value = 100000, message = "lux는 100,000 이하입니다.") Integer minLux,
			@NotNull(message = "최대 lux를 입력하세요.") @Min(value = 0, message = "lux는 0 이상입니다.")
			@Max(value = 100000, message = "lux는 100,000 이하입니다.") Integer maxLux,
			@NotNull(message = "최소 직사광 시간을 입력하세요.") @Min(value = 0, message = "직사광 시간은 0~720분입니다.")
			@Max(value = 720, message = "직사광 시간은 0~720분입니다.") Integer minDirectMinutes,
			@Min(value = 0, message = "직사광 시간은 0~720분입니다.")
			@Max(value = 720, message = "직사광 시간은 0~720분입니다.") Integer maxDirectMinutes,
			@Min(value = 0, message = "거리는 0~3000cm입니다.") @Max(value = 3000, message = "거리는 0~3000cm입니다.") Integer minWindowDistance,
			@Min(value = 0, message = "거리는 0~3000cm입니다.") @Max(value = 3000, message = "거리는 0~3000cm입니다.") Integer maxWindowDistance,
			@Min(value = -20, message = "겨울 최저온도는 -20~30℃입니다.")
			@Max(value = 30, message = "겨울 최저온도는 -20~30℃입니다.") Integer winterMinTemp) {

		void check() {
			if (minLux > maxLux) {
				throw new IllegalArgumentException("최소 lux가 최대 lux보다 큽니다.");
			}
			if (maxDirectMinutes != null && minDirectMinutes > maxDirectMinutes) {
				throw new IllegalArgumentException("최소 직사광 시간이 최대보다 큽니다.");
			}
			if (minWindowDistance != null && maxWindowDistance != null && minWindowDistance > maxWindowDistance) {
				throw new IllegalArgumentException("창에서 최소 거리가 최대 거리보다 큽니다.");
			}
		}

		PlantConverter.Converted toConverted() {
			return new PlantConverter.Converted(minLux, maxLux, minDirectMinutes, maxDirectMinutes, minWindowDistance,
					maxWindowDistance, winterMinTemp);
		}
	}

	public record PlantPage(List<PlantResponse> items, int page, int size, long total) {
	}

	public record PlantResponse(Long id, String name, String scientificName, String englishName, String familyName,
			String thumbnailUrl, String imageUrl, String lightCodes, String lightNames, String placementCodes,
			String placementNames, String winterMinName, String growthTempName, String humidityName,
			String manageLevelCode, String manageLevelName, String waterSpring, String waterSummer,
			String waterAutumn, String waterWinter, String advice, String growthInfo, int minLux, int maxLux,
			int minDirectMinutes, Integer maxDirectMinutes, Integer minWindowDistance, Integer maxWindowDistance,
			Integer winterMinTemp, boolean adjusted, PlantSource source, LocalDateTime updatedAt) {

		public static PlantResponse of(PlantSpecies p) {
			return new PlantResponse(p.getId(), p.getName(), p.getScientificName(), p.getEnglishName(),
					p.getFamilyName(), p.getThumbnailUrl(), p.getImageUrl(), p.getLightCodes(), p.getLightNames(),
					p.getPlacementCodes(), p.getPlacementNames(), p.getWinterMinName(), p.getGrowthTempName(),
					p.getHumidityName(), p.getManageLevelCode(), p.getManageLevelName(), p.getWaterSpring(),
					p.getWaterSummer(), p.getWaterAutumn(), p.getWaterWinter(), p.getAdvice(), p.getGrowthInfo(),
					p.getMinLux(), p.getMaxLux(), p.getMinDirectMinutes(), p.getMaxDirectMinutes(),
					p.getMinWindowDistance(), p.getMaxWindowDistance(), p.getWinterMinTemp(), p.isAdjusted(),
					p.getSource(), p.getUpdatedAt());
		}
	}
}
