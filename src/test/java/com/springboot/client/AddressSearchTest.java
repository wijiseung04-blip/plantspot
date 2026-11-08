package com.springboot.client;

import com.springboot.service.AddressService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.springboot.support.TestBuildings;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AddressSearchTest {

	@MockitoBean
	AddressApi api;

	@Autowired
	MockMvc mvc;

	@Autowired
	AddressService service;

	@Autowired
	JdbcTemplate jdbc;

	static List<AddressApi.Candidate> sample() throws Exception {
		try (InputStream in = AddressSearchTest.class.getResourceAsStream("/vworld/search-road.json")) {
			return VworldAddressApi.parse(in.readAllBytes());
		}
	}

	@BeforeEach
	void setUp() {
		TestBuildings.insertHadan(jdbc);
		when(api.hasKey()).thenReturn(true);
	}

	@Test
	void 브이월드_응답_읽기() throws Exception {
		List<AddressApi.Candidate> c = sample();
		assertThat(c).hasSize(2);
		assertThat(c.get(0).roadAddress()).isEqualTo("부산광역시 사하구 테스트로 1");
		assertThat(c.get(0).parcelAddress()).isEqualTo("하단동 1160-6");
		assertThat(c.get(0).x()).isEqualTo(378735.0);
		assertThat(c.get(1).buildingName()).isNull();
		assertThat(VworldAddressApi.parse("{\"response\":{\"status\":\"NOT_FOUND\"}}".getBytes())).isEmpty();
	}

	@Test
	void 도로명주소로_우리_건물을_찾는다_비회원도_가능() throws Exception {
		when(api.search(anyString(), eq("road"))).thenReturn(sample());
		mvc.perform(get("/api/address/search").param("query", "테스트로 1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].building.dong").value("하단동"))
				.andExpect(jsonPath("$[0].building.jibun").value("1160-6"))
				.andExpect(jsonPath("$[0].latitude", Matchers.closeTo(35.115, 0.001)))
				.andExpect(jsonPath("$[1].building").doesNotExist()); // 사하구 밖
	}

	@Test
	void 도로명_결과가_없으면_지번주소로_다시_찾는다() throws Exception {
		when(api.search(anyString(), eq("road"))).thenReturn(List.of());
		when(api.search(anyString(), eq("parcel"))).thenReturn(sample().subList(0, 1));
		assertThat(service.search("하단동 1160-6")).hasSize(1);
		verify(api).search("하단동 1160-6", "parcel");
	}

	@Test
	void 외곽선_안의_점은_그_건물() {
		// 기준 건물 외곽선(20 × 15m)을 넣고, 중심에서 40m 떨어진 큰 건물도 넣는다
		jdbc.update("UPDATE building SET footprint_wkt = ? WHERE jibun = '1160-6'",
				"MULTIPOLYGON (((378721.47 281651.93, 378741.47 281651.93, 378741.47 281666.93, 378721.47 281666.93, 378721.47 281651.93)))");
		assertThat(service.match(378740.0, 281665.0)).map(b -> b.getJibun()).contains("1160-6");
		// 외곽선 밖이지만 30m 안 → 가장 가까운 건물
		assertThat(service.match(378731.47, 281685.0)).map(b -> b.getJibun()).contains("1160-6");
		// 30m 밖 → 없음
		assertThat(service.match(378731.47, 281759.0)).isEmpty();
	}

	@Test
	void 키가_없으면_503_짧은_검색어는_400() throws Exception {
		when(api.hasKey()).thenReturn(false);
		mvc.perform(get("/api/address/search").param("query", "낙동대로 550"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message", Matchers.containsString("동·지번")));
		mvc.perform(get("/api/address/search").param("query", "a")).andExpect(status().isBadRequest());
	}

	@Test
	void 외부_서비스_오류는_503() throws Exception {
		when(api.search(anyString(), anyString())).thenThrow(new IllegalStateException("브이월드 HTTP 500"));
		mvc.perform(get("/api/address/search").param("query", "낙동대로 550"))
				.andExpect(status().isServiceUnavailable());
	}
}
