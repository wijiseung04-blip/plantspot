package com.springboot.dto;

import com.springboot.calculator.Shape;
import com.springboot.domain.CurtainType;
import com.springboot.domain.House;
import com.springboot.domain.Point;
import com.springboot.domain.Room;
import com.springboot.domain.RoomWindow;

import java.util.List;

import com.springboot.domain.HeightSource;

/** 집·방·창문 응답. 트랜잭션 안(서비스)에서 만든다. */
public final class HouseResponses {

	private HouseResponses() {
	}

	public record HouseResponse(Long id, String name, String dong, String jibun, int floor, double floorHeight,
			double latitude, double longitude, Double buildingHeight, HeightSource buildingHeightSource,
			int roomCount, double unitPosition, Double buildingAxis,
			/** 건물에서 고를 수 있는 가장 높은 층 (모르면 null) */ Integer maxFloor,
			/** 층수를 확인하지 못했거나 이미 범위를 넘은 집일 때 안내 */ String floorWarning) {

		public static HouseResponse of(House h) {
			Double axis = null;
			if (h.getBuilding() != null) {
				double a = com.springboot.calculator.BuildingFacade.longAxis(h.getBuilding().getFootprintWkt());
				axis = Double.isNaN(a) ? null : a;
			}
			return new HouseResponse(h.getId(), h.getName(), h.getDong(), h.getJibun(), h.getFloor(),
					h.getFloorHeight(), h.getLatitude(), h.getLongitude(),
					h.getBuilding() != null ? h.getBuilding().getHeight() : null,
					h.getBuilding() != null ? h.getBuilding().getHeightSource() : null, h.getRooms().size(),
					h.unitPosition(), axis, com.springboot.calculator.FloorRule.maxFloor(h.getBuilding()),
					floorWarning(h));
		}

		private static String floorWarning(House h) {
			String over = com.springboot.calculator.FloorRule.violation(h.getBuilding(), h.getFloor());
			if (over != null) {
				return "등록된 층(" + h.getFloor() + "층)이 건물 범위를 넘습니다. " + over;
			}
			return h.getBuilding() == null ? null : com.springboot.calculator.FloorRule.warning(h.getBuilding());
		}
	}

	public record RoomResponse(Long id, Long houseId, String name, List<Point> shape, double orientationDeg,
			double ceilingHeight, CurtainType curtain, String curtainLabel, double areaM2,
			Double winterMinTemp, List<WindowResponse> windows, InnerGlassView innerGlass) {

		public static RoomResponse of(Room r) {
			List<Point> points = r.points();
			return new RoomResponse(r.getId(), r.getHouse().getId(), r.getName(), points, r.getOrientationDeg(),
					r.getCeilingHeight(), r.getCurtain(), r.getCurtain().getLabel(),
					Math.round(Math.abs(Shape.signedArea(points)) / 10_000.0 * 100) / 100.0,
					r.getWinterMinTemp(), r.getWindows().stream().map(WindowResponse::of).toList(),
					r.hasInnerGlass() ? new InnerGlassView(r.getInnerGlassWall(), r.getInnerGlassDistance(),
							r.getInnerGlassTransmittance()) : null);
		}
	}

	/** 안쪽 창 선 (발코니 + 거실) */
	public record InnerGlassView(int wallIndex, double distance, double transmittance) {
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
