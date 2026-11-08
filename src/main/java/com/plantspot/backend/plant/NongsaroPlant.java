package com.plantspot.backend.plant;

/** 농사로 gardenList + gardenDtl 에서 읽은 식물 하나. */
public record NongsaroPlant(String cntntsNo, String name, String scientificName, String englishName,
		String familyName, String thumbnailUrl, String imageUrl, String lightCodes, String lightNames,
		String placementCodes, String placementNames, String winterMinCode, String winterMinName,
		String growthTempName, String humidityName, String manageLevelCode, String manageLevelName,
		String waterSpring, String waterSummer, String waterAutumn, String waterWinter, String advice,
		String growthInfo) {
}
