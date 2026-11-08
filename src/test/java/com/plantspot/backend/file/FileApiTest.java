package com.plantspot.backend.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

import com.plantspot.backend.member.LoginMember;
import com.plantspot.backend.member.MemberService;
import com.plantspot.backend.member.Role;
import com.plantspot.backend.support.TestBuildings;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FileApiTest {

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
	JdbcTemplate jdbc;

	LoginMember owner;
	LoginMember other;
	LoginMember admin;

	@BeforeEach
	void setUp() {
		owner = new LoginMember(memberService.create("fowner", "password123", "주인", Role.USER));
		other = new LoginMember(memberService.create("fother", "password123", "남", Role.USER));
		admin = new LoginMember(memberService.create("fadmin", "password123", "관리자", Role.ADMIN));
	}

	private MockMultipartFile csv() {
		return new MockMultipartFile("file", "측정 11월.csv", "text/csv",
				"time,window,inside\n2026-11-08T09:00,812,240\n".getBytes(StandardCharsets.UTF_8));
	}

	private long upload(MockMultipartFile file, String category, LoginMember who) throws Exception {
		String location = mvc.perform(multipart("/api/files").file(file).param("category", category)
				.with(csrf()).with(user(who)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	@Test
	void 올리고_목록_보고_내려받는다() throws Exception {
		long id = upload(csv(), "SENSOR_LOG", owner);

		mvc.perform(get("/api/files").with(user(owner)))
				.andExpect(jsonPath("$[0].originalName").value("측정 11월.csv"))
				.andExpect(jsonPath("$[0].contentType").value("text/csv"));
		mvc.perform(get("/api/files/{id}/content", id).with(user(owner)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("UTF-8''")))
				.andExpect(content().string(Matchers.startsWith("time,window,inside")));
		try (var files = Files.list(uploadDir)) {
			assertThat(files.map(p -> p.getFileName().toString())).anyMatch(n -> n.endsWith(".csv"));
		}
	}

	@Test
	void 남의_파일은_못_받고_관리자는_받는다() throws Exception {
		long id = upload(csv(), "SENSOR_LOG", owner);
		mvc.perform(get("/api/files/{id}/content", id).with(user(other))).andExpect(status().isForbidden());
		mvc.perform(get("/api/files/{id}/content", id).with(user(admin))).andExpect(status().isOk());
	}

	@Test
	void 종류에_맞지_않는_확장자는_거절() throws Exception {
		MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] { 1 });
		mvc.perform(multipart("/api/files").file(exe).param("category", "PLANT_PHOTO").with(csrf()).with(user(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("식물 사진")));
		MockMultipartFile empty = new MockMultipartFile("file", "a.csv", "text/csv", new byte[0]);
		mvc.perform(multipart("/api/files").file(empty).param("category", "SENSOR_LOG").with(csrf()).with(user(owner)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 파일이_없으면_400_비회원은_401() throws Exception {
		mvc.perform(multipart("/api/files").param("category", "SENSOR_LOG").with(csrf()).with(user(owner)))
				.andExpect(status().isBadRequest());
		mvc.perform(multipart("/api/files").file(csv()).param("category", "SENSOR_LOG").with(csrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 경로가_섞인_파일_이름은_이름만_남긴다() throws Exception {
		MockMultipartFile f = new MockMultipartFile("file", "C:\\Users\\me\\..\\plan.png", "image/png", new byte[] { 1, 2 });
		long id = upload(f, "PLANT_PHOTO", owner);
		mvc.perform(get("/api/files/{id}", id).with(user(owner)))
				.andExpect(jsonPath("$.originalName").value("plan.png"))
				.andExpect(jsonPath("$.contentType").value("image/png"));
	}

	@Test
	void 평면도를_방에_올리면_방에_연결된다() throws Exception {
		TestBuildings.insertHadan(jdbc);
		String house = mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dong\":\"하단동\",\"jibun\":\"1160-6\",\"name\":\"집\",\"floor\":1}").with(csrf()).with(user(owner)))
				.andReturn().getResponse().getHeader("Location");
		String room = mvc.perform(post(house + "/rooms").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"방","shape":[{"x":0,"y":0},{"x":300,"y":0},{"x":300,"y":300},{"x":0,"y":300}],"orientationDeg":0}""")
				.with(csrf()).with(user(owner))).andReturn().getResponse().getHeader("Location");
		String roomId = room.substring(room.lastIndexOf('/') + 1);

		MockMultipartFile png = new MockMultipartFile("file", "plan.png", "image/png", new byte[] { 1, 2, 3 });
		String fileId = mvc.perform(multipart("/api/files").file(png).param("category", "FLOOR_PLAN")
				.param("roomId", roomId).with(csrf()).with(user(owner)))
				.andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location").replace("/api/files/", "");
		mvc.perform(get(room).with(user(owner))).andExpect(jsonPath("$.floorPlanFileId").value(Long.parseLong(fileId)));

		// 남의 방에는 못 올린다
		mvc.perform(multipart("/api/files").file(png).param("category", "FLOOR_PLAN").param("roomId", roomId)
				.with(csrf()).with(user(other))).andExpect(status().isForbidden());
	}

	@Test
	void 삭제하면_목록과_디스크에서_사라진다() throws Exception {
		long id = upload(csv(), "SENSOR_LOG", owner);
		mvc.perform(delete("/api/files/{id}", id).with(csrf()).with(user(owner))).andExpect(status().isNoContent());
		mvc.perform(get("/api/files/{id}", id).with(user(owner))).andExpect(status().isNotFound());
	}

}
