package com.springboot.config;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.springboot.domain.ObstructionSource;

/**
 * 예전에 만든 DB 파일 고치기. Hibernate 는 enum 칸에 check (source in ('BUILDING','SENSOR')) 를 만들고,
 * ddl-auto=update 는 이 제약을 바꾸지 않는다 → 새 값(TERRAIN)을 넣으면 실패한다.
 * 제약이 낡았으면 같은 구조의 새 테이블로 옮겨 담는다(실측값 등 기존 행 보존).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaUpgrade implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SchemaUpgrade.class);

	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;

	public SchemaUpgrade(JdbcTemplate jdbc, TransactionTemplate tx) {
		this.jdbc = jdbc;
		this.tx = tx;
	}

	@Override
	public void run(ApplicationArguments args) {
		upgradeEnumCheck("obstruction_angle", "source",
				Arrays.stream(ObstructionSource.values()).map(Enum::name).toList());
	}

	void upgradeEnumCheck(String table, String column, List<String> values) {
		List<String> ddl = jdbc.queryForList("select sql from sqlite_master where type = 'table' and name = ?",
				String.class, table);
		if (ddl.isEmpty() || ddl.get(0) == null) {
			return;
		}
		String fixed = rewriteCheck(ddl.get(0), column, values);
		if (fixed == null) {
			return;
		}
		String temp = table + "_upgrade";
		tx.executeWithoutResult(s -> {
			jdbc.execute("drop table if exists " + temp);
			jdbc.execute(fixed.replaceFirst("(?i)create table\\s+\"?" + table + "\"?", "create table " + temp));
			jdbc.execute("insert into " + temp + " select * from " + table);
			jdbc.execute("drop table " + table);
			jdbc.execute("alter table " + temp + " rename to " + table);
		});
		log.info("DB 고침: {}.{} 에 허용 값 {} 반영", table, column, values);
	}

	/** check ((column in ('A','B'))) 의 목록이 values 와 다르면 고친 DDL, 같거나 제약이 없으면 null. */
	static String rewriteCheck(String ddl, String column, List<String> values) {
		Matcher m = Pattern.compile("(?i)(check\\s*\\(\\s*\\(?\\s*" + column + "\\s+in\\s*\\()([^)]*)(\\))").matcher(ddl);
		if (!m.find()) {
			return null;
		}
		List<String> current = Arrays.stream(m.group(2).split(",")).map(v -> v.trim().replace("'", "")).toList();
		if (current.containsAll(values)) {
			return null;
		}
		String list = values.stream().map(v -> "'" + v + "'").collect(Collectors.joining(","));
		return ddl.substring(0, m.start(2)) + list + ddl.substring(m.end(2));
	}
}
