package com.springboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;


/** 예전 DB(check 제약에 TERRAIN 없음)를 고치고 기존 행을 남기는지 확인 */
@SpringBootTest
class SchemaUpgradeTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	SchemaUpgrade upgrade;

	@Test
	void 낡은_check_제약을_고치고_행은_남긴다() {
		jdbc.execute("drop table if exists old_angle");
		jdbc.execute("create table old_angle (id integer, source varchar(10) not null check ((source in ('BUILDING','SENSOR'))), primary key (id))");
		jdbc.update("insert into old_angle (source) values ('SENSOR')");

		upgrade.upgradeEnumCheck("old_angle", "source", List.of("BUILDING", "TERRAIN", "SENSOR"));

		jdbc.update("insert into old_angle (source) values ('TERRAIN')");
		assertThat(jdbc.queryForList("select source from old_angle order by id", String.class))
				.containsExactly("SENSOR", "TERRAIN");
		assertThat(jdbc.queryForObject("select sql from sqlite_master where name = 'old_angle'", String.class))
				.contains("'TERRAIN'");
		jdbc.execute("drop table old_angle");
	}

	@Test
	void 지금_obstruction_angle_은_TERRAIN_을_받는다() {
		assertThat(jdbc.queryForObject("select sql from sqlite_master where name = 'obstruction_angle'", String.class))
				.contains("TERRAIN");
	}
}
