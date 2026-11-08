package com.plantspot.backend.address;

import java.util.List;

/** 주소 검색 API (테스트에서는 가짜로 바꾼다). */
public interface AddressApi {

	/** 주소 후보. x·y 는 EPSG:5186(미터) */
	/** buildingDetail: 건물명 상세(아파트 동 이름 등, 브이월드 bldnmdc) */
	record Candidate(String roadAddress, String parcelAddress, String zipcode, String buildingName,
			String buildingDetail, double x, double y) {
	}

	/** category: road(도로명) / parcel(지번) */
	List<Candidate> search(String query, String category);

	boolean hasKey();
}
