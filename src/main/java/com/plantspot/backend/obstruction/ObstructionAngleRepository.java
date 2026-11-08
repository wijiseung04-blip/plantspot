package com.plantspot.backend.obstruction;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ObstructionAngleRepository extends JpaRepository<ObstructionAngle, Long> {

	List<ObstructionAngle> findByHouseIdOrderByAzimuthBinAsc(Long houseId);

	List<ObstructionAngle> findByHouseIdAndSource(Long houseId, ObstructionSource source);

	/**
	 * 엔티티 단위로 지운다. (벌크 delete 쿼리는 영속성 컨텍스트에 지운 행이 남아,
	 * SQLite가 번호를 다시 쓸 때 같은 id 충돌이 난다.)
	 */
	default void deleteByHouseAndSource(Long houseId, ObstructionSource source) {
		deleteAll(findByHouseIdAndSource(houseId, source));
		flush();
	}

	default void deleteByHouse(Long houseId) {
		deleteAll(findByHouseIdOrderByAzimuthBinAsc(houseId));
		flush();
	}
}
