package com.springboot.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.springboot.dto.HouseRequests.CreateHouse;
import com.springboot.dto.HouseRequests.SaveRoom;
import com.springboot.dto.HouseRequests.SaveWindow;
import com.springboot.dto.HouseRequests.UpdateHouse;
import com.springboot.dto.HouseResponses.HouseResponse;
import com.springboot.dto.HouseResponses.RoomResponse;
import com.springboot.dto.HouseResponses.WindowResponse;
import com.springboot.service.HouseService;
import com.springboot.security.LoginMember;

import jakarta.validation.Valid;

/** 집·방·창문 REST API (회원 전용). [필수: REST 자원 중심 URL, 3계층] */
@RestController
public class HouseApiController {

	private final HouseService service;

	public HouseApiController(HouseService service) {
		this.service = service;
	}

	// ----- 집 -----

	@PostMapping("/api/houses")
	public ResponseEntity<HouseResponse> createHouse(@AuthenticationPrincipal LoginMember me,
			@Valid @RequestBody CreateHouse req) {
		HouseResponse house = service.createHouse(me, req);
		return ResponseEntity.created(URI.create("/api/houses/" + house.id())).body(house);
	}

	@GetMapping("/api/houses")
	public List<HouseResponse> myHouses(@AuthenticationPrincipal LoginMember me) {
		return service.myHouses(me);
	}

	@GetMapping("/api/houses/{id}")
	public HouseResponse house(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		return service.house(me, id);
	}

	@PutMapping("/api/houses/{id}")
	public HouseResponse updateHouse(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@Valid @RequestBody UpdateHouse req) {
		return service.updateHouse(me, id, req);
	}

	@DeleteMapping("/api/houses/{id}")
	public ResponseEntity<Void> deleteHouse(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		service.deleteHouse(me, id);
		return ResponseEntity.noContent().build();
	}

	// ----- 방 -----

	@PostMapping("/api/houses/{id}/rooms")
	public ResponseEntity<RoomResponse> createRoom(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@Valid @RequestBody SaveRoom req) {
		RoomResponse room = service.createRoom(me, id, req);
		return ResponseEntity.created(URI.create("/api/rooms/" + room.id())).body(room);
	}

	@GetMapping("/api/houses/{id}/rooms")
	public List<RoomResponse> rooms(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		return service.rooms(me, id);
	}

	@GetMapping("/api/rooms/{id}")
	public RoomResponse room(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		return service.room(me, id);
	}

	@PutMapping("/api/rooms/{id}")
	public RoomResponse updateRoom(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@Valid @RequestBody SaveRoom req) {
		return service.updateRoom(me, id, req);
	}

	@DeleteMapping("/api/rooms/{id}")
	public ResponseEntity<Void> deleteRoom(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		service.deleteRoom(me, id);
		return ResponseEntity.noContent().build();
	}

	// ----- 창문 -----

	/** 안쪽 창 선 (발코니 + 거실처럼 창이 두 겹인 방) */
	@PutMapping("/api/rooms/{id}/inner-glass")
	public RoomResponse setInnerGlass(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@Valid @RequestBody com.springboot.dto.HouseRequests.SaveInnerGlass req) {
		return service.setInnerGlass(me, id, req);
	}

	@DeleteMapping("/api/rooms/{id}/inner-glass")
	public RoomResponse clearInnerGlass(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		return service.clearInnerGlass(me, id);
	}

	@PostMapping("/api/rooms/{id}/windows")
	public ResponseEntity<WindowResponse> createWindow(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@Valid @RequestBody SaveWindow req) {
		WindowResponse window = service.createWindow(me, id, req);
		return ResponseEntity.created(URI.create("/api/windows/" + window.id())).body(window);
	}

	@GetMapping("/api/rooms/{id}/windows")
	public List<WindowResponse> windows(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		return service.windows(me, id);
	}

	@PutMapping("/api/windows/{id}")
	public WindowResponse updateWindow(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			@Valid @RequestBody SaveWindow req) {
		return service.updateWindow(me, id, req);
	}

	@DeleteMapping("/api/windows/{id}")
	public ResponseEntity<Void> deleteWindow(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id) {
		service.deleteWindow(me, id);
		return ResponseEntity.noContent().build();
	}
}
