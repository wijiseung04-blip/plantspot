package com.plantspot.backend.house;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.member.MemberService;
import com.plantspot.backend.member.Role;
import com.plantspot.backend.support.TestBuildings;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HouseApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberService memberService;

	@Autowired
	JdbcTemplate jdbc;

	LoginMember owner;
	LoginMember other;

	@BeforeEach
	void setUp() {
		TestBuildings.insertHadan(jdbc);
		owner = new LoginMember(memberService.create("owner1", "password123", "주인", Role.USER));
		other = new LoginMember(memberService.create("other1", "password123", "남", Role.USER));
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body, LoginMember who) {
		return b.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()).with(user(who));
	}

	private long createHouse() throws Exception {
		String location = mvc.perform(json(post("/api/houses"),
				"{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"name\":\"우리 집\",\"floor\":3}", owner))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.buildingHeight").value(18.75))
				.andExpect(jsonPath("$.floorHeight").value(3.0))
				.andReturn().getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private long createRoom(long houseId) throws Exception {
		String location = mvc.perform(json(post("/api/houses/{id}/rooms", houseId), """
				{"name":"거실","shape":[{"x":0,"y":0},{"x":400,"y":0},{"x":400,"y":300},{"x":0,"y":300}],
				 "orientationDeg":0,"curtain":"SHEER"}""", owner))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.areaM2").value(12.0))
				.andExpect(jsonPath("$.ceilingHeight").value(230.0))
				.andReturn().getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	@Test
	void 동_지번으로_집을_등록하고_조회한다() throws Exception {
		long houseId = createHouse();
		mvc.perform(get("/api/houses").with(user(owner)))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].jibun").value("1160-6"));
		mvc.perform(get("/api/houses/{id}", houseId).with(user(other))).andExpect(status().isForbidden());
		mvc.perform(get("/api/houses").with(user(other))).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 없는_지번은_404_빈_값은_400() throws Exception {
		mvc.perform(json(post("/api/houses"), "{\"dong\":\"하단동\",\"jibun\":\"1\",\"name\":\"집\",\"floor\":1}", owner))
				.andExpect(status().isNotFound());
		mvc.perform(json(post("/api/houses"), "{\"dong\":\"\",\"jibun\":\"1\",\"name\":\"집\",\"floor\":0}", owner))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("동을 선택하세요.")))
				.andExpect(jsonPath("$.message", Matchers.containsString("층은 1 이상입니다.")));
	}

	@Test
	void 방과_창문을_등록하면_창_방향을_계산한다() throws Exception {
		long roomId = createRoom(createHouse());
		mvc.perform(json(post("/api/rooms/{id}/windows", roomId),
				"{\"wallIndex\":2,\"offset\":100,\"width\":180,\"height\":150,\"sillHeight\":60}", owner))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", Matchers.startsWith("/api/windows/")))
				.andExpect(jsonPath("$.azimuthDeg").value(180.0))
				.andExpect(jsonPath("$.direction").value("남향"));

		// 평면도를 90° 돌리면(위쪽 = 동) 남쪽 벽이 서향이 된다
		mvc.perform(json(put("/api/rooms/{id}", roomId), """
				{"name":"거실","shape":[{"x":0,"y":0},{"x":400,"y":0},{"x":400,"y":300},{"x":0,"y":300}],
				 "orientationDeg":90}""", owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.windows[0].direction").value("서향"))
				.andExpect(jsonPath("$.curtain").value("SHEER"));
	}

	@Test
	void 벽보다_긴_창문과_천장보다_높은_창문은_거절() throws Exception {
		long roomId = createRoom(createHouse());
		mvc.perform(json(post("/api/rooms/{id}/windows", roomId),
				"{\"wallIndex\":1,\"offset\":200,\"width\":180,\"height\":150,\"sillHeight\":60}", owner))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("벽 길이(300cm)")));
		mvc.perform(json(post("/api/rooms/{id}/windows", roomId),
				"{\"wallIndex\":0,\"offset\":0,\"width\":100,\"height\":200,\"sillHeight\":60}", owner))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("천장")));
		mvc.perform(json(post("/api/rooms/{id}/windows", roomId),
				"{\"wallIndex\":9,\"offset\":0,\"width\":100,\"height\":100,\"sillHeight\":60}", owner))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 꼬인_방_모양은_거절() throws Exception {
		long houseId = createHouse();
		mvc.perform(json(post("/api/houses/{id}/rooms", houseId), """
				{"name":"이상한 방","shape":[{"x":0,"y":0},{"x":400,"y":300},{"x":400,"y":0},{"x":0,"y":300}],
				 "orientationDeg":0}""", owner))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("방 모양의 벽이 서로 교차합니다."));
		mvc.perform(json(post("/api/houses/{id}/rooms", houseId),
				"{\"name\":\"점 두개\",\"shape\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":1}],\"orientationDeg\":0}", owner))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 남의_방에는_창문을_못_단다() throws Exception {
		long roomId = createRoom(createHouse());
		mvc.perform(json(post("/api/rooms/{id}/windows", roomId),
				"{\"wallIndex\":2,\"offset\":100,\"width\":180,\"height\":150,\"sillHeight\":60}", other))
				.andExpect(status().isForbidden());
	}

	@Test
	void 집을_지우면_방과_창문도_지워진다() throws Exception {
		long houseId = createHouse();
		long roomId = createRoom(houseId);
		mvc.perform(json(post("/api/rooms/{id}/windows", roomId),
				"{\"wallIndex\":2,\"offset\":100,\"width\":180,\"height\":150,\"sillHeight\":60}", owner))
				.andExpect(status().isCreated());
		mvc.perform(delete("/api/houses/{id}", houseId).with(csrf()).with(user(owner)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/rooms/{id}", roomId).with(user(owner))).andExpect(status().isNotFound());
	}

}
