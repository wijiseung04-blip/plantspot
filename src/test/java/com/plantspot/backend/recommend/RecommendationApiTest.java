package com.plantspot.backend.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.member.MemberService;
import com.plantspot.backend.member.Role;
import com.plantspot.backend.plant.PlantConverter;
import com.plantspot.backend.plant.PlantSpecies;
import com.plantspot.backend.plant.PlantSpeciesRepository;
import com.plantspot.backend.support.TestBuildings;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationApiTest {

	@TempDir
	static Path uploadDir;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("app.upload-dir", () -> uploadDir.toString());
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	PlantSpeciesRepository plants;

	@Autowired
	JdbcTemplate jdbc;

	LoginMember owner;
	LoginMember other;
	String room;
	long sunPlant;
	long shadePlant;
	long impossible;

	private long plant(String name, int minLux, int maxLux, int minDirect, Integer maxDirect) {
		PlantSpecies p = PlantSpecies.manual(name);
		p.adjust(name, new PlantConverter.Converted(minLux, maxLux, minDirect, maxDirect, null, null, null));
		return plants.save(p).getId();
	}

	@BeforeEach
	void setUp() throws Exception {
		TestBuildings.insertHadan(jdbc);
		owner = new LoginMember(memberService.create("rowner", "password123", "주인", Role.USER));
		other = new LoginMember(memberService.create("rother", "password123", "남", Role.USER));
		sunPlant = plant("양지식물", 1500, 10000, 120, null);
		shadePlant = plant("음지식물", 300, 800, 0, 60);
		impossible = plant("불가능식물", 30000, 50000, 600, null);
		String house = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"name\":\"집\",\"floor\":5}")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		room = mvc.perform(post(house + "/rooms").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"거실","shape":[{"x":0,"y":0},{"x":400,"y":0},{"x":400,"y":400},{"x":0,"y":400}],"orientationDeg":0}""")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		mvc.perform(post(room + "/windows").contentType(MediaType.APPLICATION_JSON)
				.content("{\"wallIndex\":2,\"offset\":100,\"width\":200,\"height\":150,\"sillHeight\":60}")
				.with(csrf()).with(user(owner))).andExpect(status().isCreated());
	}

	@Test
	void 추천_순위와_자리() throws Exception {
		String body = mvc.perform(get(room + "/recommendations").with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(3))
				.andExpect(jsonPath("$.items[2].name").value("불가능식물"))
				.andReturn().getResponse().getContentAsString();
		// 음지식물 자리는 직사광이 적고(최대 60분), 양지식물 자리는 직사광이 2시간 이상 (동지 기준)
		double[] sunSpot = winterSpot(body, "양지식물");
		double[] shadeSpot = winterSpot(body, "음지식물");
		String winter = mvc.perform(get(room + "/sunlight").param("season", "WINTER_SOLSTICE").with(user(owner)))
				.andReturn().getResponse().getContentAsString();
		assertThat(directAt(winter, sunSpot[0], sunSpot[1])).isGreaterThanOrEqualTo(120);
		assertThat(directAt(winter, shadeSpot[0], shadeSpot[1])).isLessThanOrEqualTo(60);
		assertThat(jdbc.queryForObject("select count(*) from recommendation", Integer.class)).isPositive();

		mvc.perform(get(room + "/recommendations/{p}", shadePlant).with(user(owner)))
				.andExpect(jsonPath("$.seasons.WINTER_SOLSTICE.length()").value(256))
				.andExpect(jsonPath("$.name").value("음지식물"));
		mvc.perform(get(room + "/recommendations").with(user(other))).andExpect(status().isForbidden());
	}

	/** 고정 자리(season 없음) 또는 동지 자리 */
	private static double[] winterSpot(String json, String name) {
		java.util.List<java.util.Map<String, Object>> spots = JsonPath.read(json, "$.items[?(@.name=='" + name + "')].spots[*]");
		for (var spot : spots) {
			if (spot.get("season") == null || "WINTER_SOLSTICE".equals(spot.get("season"))) {
				return new double[] { ((Number) spot.get("x")).doubleValue(), ((Number) spot.get("y")).doubleValue() };
			}
		}
		throw new AssertionError("동지 자리가 없습니다: " + name);
	}

	private static int directAt(String sunlightJson, Number x, Number y) {
		java.util.List<Integer> v = JsonPath.read(sunlightJson,
				"$.cells[?(@.x==" + x.doubleValue() + " && @.y==" + y.doubleValue() + ")].directMinutes");
		return v.get(0);
	}

	@Test
	void 방이나_변환값이_바뀌면_추천을_다시_계산한다() throws Exception {
		mvc.perform(get(room + "/recommendations").with(user(owner))).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from recommendation", Integer.class)).isPositive();
		mvc.perform(post(room + "/windows").contentType(MediaType.APPLICATION_JSON)
				.content("{\"wallIndex\":1,\"offset\":50,\"width\":100,\"height\":120,\"sillHeight\":80}")
				.with(csrf()).with(user(owner))).andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("select count(*) from recommendation", Integer.class)).isZero();

		mvc.perform(get(room + "/recommendations").with(user(owner))).andExpect(status().isOk());
		LoginMember admin = new LoginMember(memberService.create("radmin", "password123", "관리자", Role.ADMIN));
		mvc.perform(put("/api/admin/plants/{id}/conversion", shadePlant).contentType(MediaType.APPLICATION_JSON)
				.content("{\"minLux\":300,\"maxLux\":900,\"minDirectMinutes\":0}").with(csrf()).with(user(admin)))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from recommendation", Integer.class)).isZero();
	}

	@Test
	void 창문_없는_방은_추천할_수_없다() throws Exception {
		String house = mvc.perform(get("/api/houses").with(user(owner))).andReturn().getResponse().getContentAsString();
		Object houseId = JsonPath.read(house, "$[0].id");
		String bare = mvc.perform(post("/api/houses/{id}/rooms", houseId).contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"창고","shape":[{"x":0,"y":0},{"x":200,"y":0},{"x":200,"y":200},{"x":0,"y":200}],"orientationDeg":0}""")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		mvc.perform(get(bare + "/recommendations").with(user(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("창문")));
	}

	@Test
	void 내_식물을_놓고_옮기고_알림을_받는다() throws Exception {
		String roomId = room.substring(room.lastIndexOf('/') + 1);
		// 음지식물을 창 바로 앞(직사광 많은 곳)에 놓음 → 알림
		String created = mvc.perform(post("/api/my-plants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"plantId\":" + shadePlant + ",\"nickname\":\"창가 음지\",\"roomId\":" + roomId + ",\"x\":200,\"y\":340}")
				.with(csrf()).with(user(owner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.currentScore").isNumber())
				.andReturn().getResponse().getHeader("Location");
		mvc.perform(post("/api/my-plants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"이름모를 식물\"}").with(csrf()).with(user(owner))).andExpect(status().isCreated());

		mvc.perform(get("/api/my-plants/alerts").with(user(owner)))
				.andExpect(jsonPath("$[?(@.nickname=='창가 음지')].type", Matchers.hasItem("MOVE")));

		mvc.perform(patch(created + "/position").contentType(MediaType.APPLICATION_JSON)
				.content("{\"roomId\":" + roomId + ",\"x\":200,\"y\":40}").with(csrf()).with(user(owner)))
				.andExpect(jsonPath("$.y").value(40.0));
		mvc.perform(get("/api/my-plants").with(user(owner))).andExpect(jsonPath("$.length()").value(2));
		mvc.perform(get("/api/my-plants").with(user(other))).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(put(created).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"남의 것\"}")
				.with(csrf()).with(user(other))).andExpect(status().isForbidden());

		// 방을 지우면 자리만 비운다
		mvc.perform(delete(room).with(csrf()).with(user(owner))).andExpect(status().isNoContent());
		mvc.perform(get("/api/my-plants").with(user(owner)))
				.andExpect(jsonPath("$[0].roomId").doesNotExist())
				.andExpect(jsonPath("$[0].plantName").value("음지식물"));
	}

	@Test
	void 남의_사진은_내_식물에_붙일_수_없다() throws Exception {
		MockMultipartFile png = new MockMultipartFile("file", "p.png", "image/png", new byte[] { 1 });
		String loc = mvc.perform(multipart("/api/files").file(png).param("category", "PLANT_PHOTO").with(csrf()).with(user(other)))
				.andReturn().getResponse().getHeader("Location");
		String fileId = loc.substring(loc.lastIndexOf('/') + 1);
		mvc.perform(post("/api/my-plants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"사진\",\"photoFileId\":" + fileId + "}").with(csrf()).with(user(owner)))
				.andExpect(status().isForbidden());
	}

	@Test
	void 비회원_간단_예측() throws Exception {
		mvc.perform(post("/api/preview").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"floor\":3,\"windowAzimuth\":180}").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.direction").value("남향"))
				.andExpect(jsonPath("$.seasons.length()").value(4))
				.andExpect(jsonPath("$.seasons[3].label").value("동지"))
				.andExpect(jsonPath("$.seasons[3].windowSunMinutes", Matchers.greaterThan(300)))
				.andExpect(jsonPath("$.skyline.length()").value(72))
				.andExpect(jsonPath("$.plants.length()").value(3));
		mvc.perform(post("/api/preview").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"floor\":0}").with(csrf()))
				.andExpect(status().isBadRequest());
	}

}
