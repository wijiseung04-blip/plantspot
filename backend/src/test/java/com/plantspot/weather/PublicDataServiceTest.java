package com.plantspot.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.member.LoginMember;
import com.plantspot.member.MemberService;
import com.plantspot.member.Role;
import com.plantspot.sun.SunService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicDataServiceTest {

	@MockitoBean
	PublicDataApi api;

	@Autowired
	PublicDataService service;

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	static final LocalDate DAY = LocalDate.of(2026, 11, 7);

	@BeforeEach
	void setUp() {
		when(api.hasKasiKey()).thenReturn(true);
		when(api.hasKmaKey()).thenReturn(true);
		when(api.riseSet(any(), anyDouble(), anyDouble()))
				.thenReturn(PublicDataParser.parseRiseSet(PublicDataParserTest.resource("kasi-riseset.xml")));
		when(api.asosDay(any(), anyInt()))
				.thenReturn(PublicDataParser.parseAsos(PublicDataParserTest.resource("asos-day.json")));
	}

	@Test
	void 출몰시각은_한번만_부르고_저장한다() {
		service.sunEvent(DAY, 35.1160, 128.9605);
		service.sunEvent(DAY, 35.1190, 128.9601); // 같은 칸(소수 둘째 자리 35.12, 128.96)
		verify(api, times(1)).riseSet(any(), anyDouble(), anyDouble());
	}

	@Test
	void 계산_일출과_천문연_값의_차이() {
		PublicDataService.SunCompare c = service.compare(LocalDate.of(2026, 11, 8), SunService.DEFAULT_LATITUDE,
				SunService.DEFAULT_LONGITUDE);
		assertThat(c.kasiAvailable()).isTrue();
		assertThat(c.sunriseDiffMin()).isBetween(-2.0, 2.0);
		assertThat(c.sunsetDiffMin()).isBetween(-2.0, 2.0);
	}

	@Test
	void 시민박명_밖은_자연광이_아니다() {
		assertThat(service.isNaturalLight(LocalDateTime.of(2026, 11, 8, 6, 0), 35.11, 128.96)).isFalse();
		assertThat(service.isNaturalLight(LocalDateTime.of(2026, 11, 8, 6, 30), 35.11, 128.96)).isTrue();
		assertThat(service.isNaturalLight(LocalDateTime.of(2026, 11, 8, 18, 0), 35.11, 128.96)).isFalse();
	}

	@Test
	void ASOS를_저장하고_맑은_시간을_판단한다() {
		PublicDataService.CollectResult r = service.collectWeather(DAY, DAY, 159);
		assertThat(r.hoursSaved()).isEqualTo(3);
		assertThat(service.hours(DAY, 159)).hasSize(3);
		assertThat(service.isClear(LocalDateTime.of(2026, 11, 7, 8, 30), 159)).contains(true);
		assertThat(service.isClear(LocalDateTime.of(2026, 11, 7, 9, 30), 159)).contains(false);
		assertThat(service.isClear(LocalDateTime.of(2026, 11, 7, 15, 0), 159)).isEmpty();
		// 다시 받아도 중복 저장하지 않는다
		assertThat(service.collectWeather(DAY, DAY, 159).hoursSaved()).isZero();
	}

	@Test
	void 오늘_이후는_받지_않는다() {
		LocalDate today = LocalDate.now(SunService.SEOUL);
		PublicDataService.CollectResult r = service.collectWeather(today, today.plusDays(3), 159);
		assertThat(r.daysFetched()).isZero();
	}

	@Test
	void 수집은_관리자만_조회는_회원만() throws Exception {
		LoginMember normal = new LoginMember(memberService.create("wuser", "password123", "회원", Role.USER));
		LoginMember admin = new LoginMember(memberService.create("wadmin", "password123", "관리자", Role.ADMIN));
		mvc.perform(post("/api/admin/weather/collect").param("from", "2026-11-07").with(csrf()).with(user(normal)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/admin/weather/collect").param("from", "2026-11-07").with(csrf()).with(user(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hoursSaved").value(3));
		mvc.perform(get("/api/weather/hours").param("date", "2026-11-07").with(user(normal)))
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].clear").value(true));
		mvc.perform(get("/api/sun-events/compare").param("date", "2026-11-08")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/admin/weather/collect").param("from", "2026-11-07").param("to", "2026-11-01")
				.with(csrf()).with(user(admin))).andExpect(status().isBadRequest());
	}

	@Test
	void 키가_없으면_출몰시각은_비어_있다() {
		when(api.hasKasiKey()).thenReturn(false);
		assertThat(service.sunEvent(LocalDate.of(2027, 1, 1), 35.11, 128.96)).isEmpty();
		assertThat(service.compare(LocalDate.of(2027, 1, 1), 35.11, 128.96).kasiAvailable()).isFalse();
	}
}
