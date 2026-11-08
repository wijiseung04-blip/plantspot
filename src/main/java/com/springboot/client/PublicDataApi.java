package com.springboot.client;

import com.springboot.dto.KasiRiseSet;

import java.time.LocalDate;

/** 공공데이터포털 API 호출 (테스트에서는 가짜로 바꾼다). */
public interface PublicDataApi {

	/** 천문연 위치별 출몰시각 */
	KasiRiseSet riseSet(LocalDate date, double latitude, double longitude);

	/** 기상청 ASOS 시간자료 하루치 */
	PublicDataParser.AsosPage asosDay(LocalDate date, int stationId);

	boolean hasKasiKey();

	boolean hasKmaKey();
}
