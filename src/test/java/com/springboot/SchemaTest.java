package com.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** SQLite에서 id 칸이 자동 번호(rowid)가 되려면 "id integer" 여야 한다. JDBC로 넣는 건물 데이터가 이를 필요로 한다. */
@SpringBootTest
class SchemaTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void 모든_테이블의_id는_integer() {
		List<String> sqls = jdbc.queryForList(
				"SELECT sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'", String.class);
		assertThat(sqls).isNotEmpty();
		for (String sql : sqls) {
			assertThat(sql).as(sql).containsPattern("\\bid integer\\b");
		}
	}
}
