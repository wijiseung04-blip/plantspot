-- MySQL 처음 설정 (root 계정으로 한 번 실행)
-- MySQL Workbench 또는 명령줄: mysql -u root -p < backend/sql/create-database.sql
-- 비밀번호는 바꿔서 쓰고, 같은 값을 backend/src/main/resources/application-local.properties 에 넣는다.

CREATE DATABASE IF NOT EXISTS plantspot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'plantspot'@'localhost' IDENTIFIED BY '여기에_DB_비밀번호';
GRANT ALL PRIVILEGES ON plantspot.* TO 'plantspot'@'localhost';
FLUSH PRIVILEGES;

-- 테이블은 backend 서버를 처음 실행하면 JPA가 만든다 (spring.jpa.hibernate.ddl-auto=update).
-- 제출용 DB 파일 만들기: mysqldump -u plantspot -p plantspot > plantspot.sql
