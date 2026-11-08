package com.plantspot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.member.LoginMember;
import com.plantspot.member.MemberService;
import com.plantspot.member.Role;
import com.plantspot.plant.PlantConverter;
import com.plantspot.plant.PlantSpecies;
import com.plantspot.plant.PlantSpeciesRepository;
import com.plantspot.support.TestBuildings;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	PlantSpeciesRepository plants;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	KoreanFont font;

	@Autowired
	com.plantspot.weather.WeatherHourRepository weatherHours;

	LoginMember owner;
	String room;

	@BeforeEach
	void setUp() throws Exception {
		TestBuildings.insertHadan(jdbc);
		owner = new LoginMember(memberService.create("downer", "password123", "주인", Role.USER));
		PlantSpecies p = PlantSpecies.manual("리포트식물");
		p.adjust("리포트식물", new PlantConverter.Converted(300, 1500, 0, 180, null, null, 10));
		long plantId = plants.save(p).getId();
		String house = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"name\":\"우리 집\",\"floor\":2}")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		room = mvc.perform(post(house + "/rooms").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"거실","shape":[{"x":0,"y":0},{"x":400,"y":0},{"x":400,"y":300},{"x":0,"y":300}],"orientationDeg":0,
				 "winterMinTemp":8}""")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		mvc.perform(post(room + "/windows").contentType(MediaType.APPLICATION_JSON)
				.content("{\"wallIndex\":2,\"offset\":100,\"width\":200,\"height\":150,\"sillHeight\":60}")
				.with(csrf()).with(user(owner))).andExpect(status().isCreated());
		String roomId = room.substring(room.lastIndexOf('/') + 1);
		mvc.perform(post("/api/my-plants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"plantId\":" + plantId + ",\"nickname\":\"거실 화분\",\"roomId\":" + roomId + ",\"x\":200,\"y\":150}")
				.with(csrf()).with(user(owner))).andExpect(status().isCreated());
	}

	@Test
	void 리포트_PDF() throws Exception {
		boolean fontAvailable;
		try {
			font.path();
			fontAvailable = true;
		} catch (RuntimeException e) {
			fontAvailable = false;
		}
		Assumptions.assumeTrue(fontAvailable, "한글 글꼴이 없는 환경");
		byte[] pdf = mvc.perform(get(room + "/report").with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")))
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			String text = new PDFTextStripper().getText(doc);
			assertThat(text).contains("햇빛", "하단동", "동지", "리포트식물", "거실 화분");
			assertThat(text).contains("겨울 실내 최저 8.0"); // 식물 겨울 최저 10도 경고
			assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(2);
		}
	}

	@Test
	void 계절별_배치도_PNG() throws Exception {
		byte[] png = mvc.perform(get(room + "/sunlight/image").param("season", "SUMMER_SOLSTICE").param("download", "true")
				.with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("summer_solstice.png")))
				.andReturn().getResponse().getContentAsByteArray();
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(img.getWidth()).isBetween(850, 950);
		mvc.perform(get(room + "/sunlight/image").param("mode", "level").with(user(owner)))
				.andExpect(header().string("Content-Disposition", Matchers.startsWith("inline")));
	}

	@Test
	void 칸별_햇빛_CSV() throws Exception {
		String csv = mvc.perform(get(room + "/sunlight/csv").param("season", "WINTER_SOLSTICE").with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", Matchers.startsWith("text/csv")))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).startsWith("﻿label,date,ix,iy");
		assertThat(csv.lines().count()).isEqualTo(1 + 16 * 12);
		assertThat(csv.lines().skip(1).findFirst().orElseThrow()).startsWith("동지,");
	}

	@Test
	void 남의_방은_내려받을_수_없다() throws Exception {
		LoginMember other = new LoginMember(memberService.create("dother", "password123", "남", Role.USER));
		mvc.perform(get(room + "/report").with(user(other))).andExpect(status().isForbidden());
		mvc.perform(get(room + "/sunlight/csv").with(user(other))).andExpect(status().isForbidden());
		mvc.perform(get(room + "/sunlight/image")).andExpect(status().isUnauthorized());
	}

	@Test
	void 기상_CSV() throws Exception {
		weatherHours.save(new com.plantspot.weather.WeatherHour(159, "부산", java.time.LocalDateTime.of(2026, 11, 7, 9, 0),
				14.0, 50.0, 1.0, 1.1, 2.0, null));
		String csv = mvc.perform(get("/api/weather/hours/csv").param("from", "2026-11-07").param("to", "2026-11-07")
				.with(user(owner))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv.lines().count()).isEqualTo(2);
		mvc.perform(get("/api/weather/hours/csv").param("from", "2026-11-07").param("to", "2026-01-01").with(user(owner)))
				.andExpect(status().isBadRequest());
	}
}
