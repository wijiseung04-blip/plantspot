package com.plantspot.house;

import java.util.List;

import com.plantspot.building.HeightSource;

/** 집·방·창문 응답. 트랜잭션 안(서비스)에서 만든다. */
public final class HouseResponses {

	private HouseResponses() {
	}

	public record HouseResponse(Long id, String name, String dong, String jibun, int floor, double floorHeight,
			double latitude, double longitude, Double buildingHeight, HeightSource buildingHeightSource,
			int roomCount) {

		public static HouseResponse of(House h) {
			return new HouseResponse(h.getId(), h.getName(), h.getDong(), h.getJibun(), h.getFloor(),
					h.getFloorHeight(), h.getLatitude(), h.getLongitude(),
					h.getBuilding() != null ? h.getBuilding().getHeight() : null,
					h.getBuilding() != null ? h.getBuilding().getHeightSource() : null, h.getRooms().size());
		}
	}

	public record RoomResponse(Long id, Long houseId, String name, List<Point> shape, double orientationDeg,
			double ceilingHeight, CurtainType curtain, String curtainLabel, double areaM2, Long floorPlanFileId,
			List<WindowResponse> windows) {

		public static RoomResponse of(Room r) {
			List<Point> points = r.points();
			return new RoomResponse(r.getId(), r.getHouse().getId(), r.getName(), points, r.getOrientationDeg(),
					r.getCeilingHeight(), r.getCurtain(), r.getCurtain().getLabel(),
					Math.round(Math.abs(Shape.signedArea(points)) / 10_000.0 * 100) / 100.0, r.getFloorPlanFileId(),
					r.getWindows().stream().map(WindowResponse::of).toList());
		}
	}

	public record WindowResponse(Long id, Long roomId, int wallIndex, double offset, double width, double height,
			double sillHeight, double azimuthDeg, String direction) {

		public static WindowResponse of(RoomWindow w) {
			return new WindowResponse(w.getId(), w.getRoom().getId(), w.getWallIndex(), w.getOffset(), w.getWidth(),
					w.getHeight(), w.getSillHeight(), Math.round(w.getAzimuthDeg() * 10) / 10.0,
					Shape.directionName(w.getAzimuthDeg()));
		}
	}
}
