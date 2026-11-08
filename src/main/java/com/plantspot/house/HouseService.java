package com.plantspot.house;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plantspot.building.Building;
import com.plantspot.building.BuildingService;
import com.plantspot.common.ForbiddenException;
import com.plantspot.common.NotFoundException;
import com.plantspot.house.HouseRequests.CreateHouse;
import com.plantspot.house.HouseRequests.SaveRoom;
import com.plantspot.house.HouseRequests.SaveWindow;
import com.plantspot.house.HouseRequests.UpdateHouse;
import com.plantspot.house.HouseResponses.HouseResponse;
import com.plantspot.house.HouseResponses.RoomResponse;
import com.plantspot.house.HouseResponses.WindowResponse;
import com.plantspot.member.LoginMember;
import com.plantspot.member.MemberService;

/** 집·방·창문 등록. 본인(또는 관리자)만 보고 고칠 수 있다. */
@Service
@Transactional(readOnly = true)
public class HouseService {

	private static final Logger log = LoggerFactory.getLogger(HouseService.class);

	public static final double DEFAULT_FLOOR_HEIGHT = 3.0;
	public static final double DEFAULT_CEILING_HEIGHT = 230;
	/** 방 최소 넓이 1㎡ (cm²) */
	private static final double MIN_ROOM_AREA = 10_000;

	private final HouseRepository houses;
	private final RoomRepository rooms;
	private final RoomWindowRepository windows;
	private final BuildingService buildingService;
	private final MemberService memberService;

	public HouseService(HouseRepository houses, RoomRepository rooms, RoomWindowRepository windows,
			BuildingService buildingService, MemberService memberService) {
		this.houses = houses;
		this.rooms = rooms;
		this.windows = windows;
		this.buildingService = buildingService;
		this.memberService = memberService;
	}

	// ----- 집 -----

	@Transactional
	public HouseResponse createHouse(LoginMember me, CreateHouse req) {
		Building building = buildingService.findByAddress(req.dong(), req.jibun()).get(0);
		House house = houses.save(new House(memberService.get(me.getId()), building, req.name().trim(), req.floor(),
				req.floorHeight() != null ? req.floorHeight() : DEFAULT_FLOOR_HEIGHT));
		log.info("집 등록: {} {} {} (회원 {})", house.getId(), req.dong(), req.jibun(), me.getUsername());
		return HouseResponse.of(house);
	}

	public List<HouseResponse> myHouses(LoginMember me) {
		return houses.findByMemberIdOrderByIdAsc(me.getId()).stream().map(HouseResponse::of).toList();
	}

	public HouseResponse house(LoginMember me, Long houseId) {
		return HouseResponse.of(loadHouse(me, houseId));
	}

	/** 다른 서비스가 트랜잭션 안에서 쓰는 엔티티 조회 (본인 확인 포함). */
	public House loadHouse(LoginMember me, Long houseId) {
		House house = houses.findById(houseId).orElseThrow(() -> new NotFoundException("집을 찾을 수 없습니다."));
		checkOwner(me, house);
		return house;
	}

	@Transactional
	public HouseResponse updateHouse(LoginMember me, Long houseId, UpdateHouse req) {
		House house = loadHouse(me, houseId);
		house.update(req.name().trim(), req.floor(),
				req.floorHeight() != null ? req.floorHeight() : house.getFloorHeight());
		return HouseResponse.of(house);
	}

	@Transactional
	public void deleteHouse(LoginMember me, Long houseId) {
		houses.delete(loadHouse(me, houseId));
		log.info("집 삭제: {} (회원 {})", houseId, me.getUsername());
	}

	// ----- 방 -----

	@Transactional
	public RoomResponse createRoom(LoginMember me, Long houseId, SaveRoom req) {
		House house = loadHouse(me, houseId);
		validateShape(req.shape());
		Room room = new Room(house, req.name().trim(), req.shape(), req.orientationDeg(), ceiling(req),
				req.curtain() != null ? req.curtain() : CurtainType.NONE);
		house.getRooms().add(room);
		return RoomResponse.of(rooms.save(room));
	}

	public List<RoomResponse> rooms(LoginMember me, Long houseId) {
		return loadHouse(me, houseId).getRooms().stream().map(RoomResponse::of).toList();
	}

	public RoomResponse room(LoginMember me, Long roomId) {
		return RoomResponse.of(loadRoom(me, roomId));
	}

	public Room loadRoom(LoginMember me, Long roomId) {
		Room room = rooms.findById(roomId).orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다."));
		checkOwner(me, room.getHouse());
		return room;
	}

	@Transactional
	public RoomResponse updateRoom(LoginMember me, Long roomId, SaveRoom req) {
		Room room = loadRoom(me, roomId);
		validateShape(req.shape());
		room.update(req.name().trim(), req.shape(), req.orientationDeg(), ceiling(req),
				req.curtain() != null ? req.curtain() : room.getCurtain());
		// 모양이 바뀌어도 기존 창문이 벽 안에 들어가는지 다시 확인하고 방향을 다시 계산
		for (RoomWindow w : room.getWindows()) {
			validateWindow(room, w.getWallIndex(), w.getOffset(), w.getWidth(), w.getHeight(), w.getSillHeight());
			w.recalculateAzimuth();
		}
		return RoomResponse.of(room);
	}

	@Transactional
	public void deleteRoom(LoginMember me, Long roomId) {
		Room room = loadRoom(me, roomId);
		room.getHouse().getRooms().remove(room);
	}

	@Transactional
	public void attachFloorPlan(LoginMember me, Long roomId, Long fileId) {
		loadRoom(me, roomId).attachFloorPlan(fileId);
	}

	// ----- 창문 -----

	@Transactional
	public WindowResponse createWindow(LoginMember me, Long roomId, SaveWindow req) {
		Room room = loadRoom(me, roomId);
		validateWindow(room, req.wallIndex(), req.offset(), req.width(), req.height(), req.sillHeight());
		RoomWindow window = new RoomWindow(room, req.wallIndex(), req.offset(), req.width(), req.height(),
				req.sillHeight());
		room.getWindows().add(window);
		return WindowResponse.of(windows.save(window));
	}

	public List<WindowResponse> windows(LoginMember me, Long roomId) {
		return loadRoom(me, roomId).getWindows().stream().map(WindowResponse::of).toList();
	}

	public RoomWindow loadWindow(LoginMember me, Long windowId) {
		RoomWindow window = windows.findById(windowId)
				.orElseThrow(() -> new NotFoundException("창문을 찾을 수 없습니다."));
		checkOwner(me, window.getRoom().getHouse());
		return window;
	}

	@Transactional
	public WindowResponse updateWindow(LoginMember me, Long windowId, SaveWindow req) {
		RoomWindow window = loadWindow(me, windowId);
		validateWindow(window.getRoom(), req.wallIndex(), req.offset(), req.width(), req.height(), req.sillHeight());
		window.update(req.wallIndex(), req.offset(), req.width(), req.height(), req.sillHeight());
		return WindowResponse.of(window);
	}

	@Transactional
	public void deleteWindow(LoginMember me, Long windowId) {
		RoomWindow window = loadWindow(me, windowId);
		window.getRoom().getWindows().remove(window);
	}

	// ----- 검사 -----

	private static void checkOwner(LoginMember me, House house) {
		if (!house.isOwnedBy(me.getId()) && !me.isAdmin()) {
			throw new ForbiddenException("다른 회원의 집입니다.");
		}
	}

	private static double ceiling(SaveRoom req) {
		return req.ceilingHeight() != null ? req.ceilingHeight() : DEFAULT_CEILING_HEIGHT;
	}

	static void validateShape(List<Point> shape) {
		if (!Shape.isSimple(shape)) {
			throw new IllegalArgumentException("방 모양의 벽이 서로 교차합니다.");
		}
		if (Math.abs(Shape.signedArea(shape)) < MIN_ROOM_AREA) {
			throw new IllegalArgumentException("방 넓이가 너무 작습니다(1㎡ 이상).");
		}
	}

	static void validateWindow(Room room, int wallIndex, double offset, double width, double height,
			double sillHeight) {
		List<Point> points = room.points();
		if (wallIndex >= points.size()) {
			throw new IllegalArgumentException("벽 번호가 잘못되었습니다(0~" + (points.size() - 1) + ").");
		}
		double wallLength = Shape.wallLength(points, wallIndex);
		if (offset + width > wallLength + 0.5) {
			throw new IllegalArgumentException(String.format("창문이 벽 길이(%.0fcm)를 넘습니다.", wallLength));
		}
		if (sillHeight + height > room.getCeilingHeight() + 0.5) {
			throw new IllegalArgumentException(
					String.format("창문 윗부분이 천장 높이(%.0fcm)보다 높습니다.", room.getCeilingHeight()));
		}
	}
}
