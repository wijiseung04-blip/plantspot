package com.plantspot.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantspot.address.AddressService;
import com.plantspot.address.AddressService.AddressResult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 도로명주소·지번주소로 건물 찾기 (비회원 간단 예측에서도 쓰므로 공개). */
@RestController
public class AddressApiController {

	private final AddressService service;

	public AddressApiController(AddressService service) {
		this.service = service;
	}

	/** 예: GET /api/address/search?query=낙동대로 550 */
	@GetMapping("/api/address/search")
	public List<AddressResult> search(@RequestParam("query") @NotBlank(message = "주소를 입력하세요.")
			@Size(max = 100, message = "주소는 100자 이하입니다.") String query) {
		return service.search(query);
	}
}
