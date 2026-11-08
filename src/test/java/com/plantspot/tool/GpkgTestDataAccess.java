package com.plantspot.tool;

import java.nio.file.Path;

/** 다른 패키지 테스트에서 GpkgTestData를 쓰기 위한 통로. */
public final class GpkgTestDataAccess {

	private GpkgTestDataAccess() {
	}

	public static void createGpkg(Path file) throws Exception {
		GpkgTestData.createGpkg(file);
	}
}
