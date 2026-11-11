package com.springboot.calculator;

import java.util.Arrays;

/**
 * 메모리에 올린 지형 격자 (EPSG:5186). 점 사이 높이는 주변 네 점으로 이중 선형 보간한다.
 * 값이 없는 점(아직 안 받음)이나 격자 밖은 NaN.
 */
public final class TerrainModel {

	private final double originX;
	private final double originY;
	private final double spacing;
	private final int nx;
	private final int ny;
	private final double[] z;

	public TerrainModel(double originX, double originY, double spacing, int nx, int ny) {
		this.originX = originX;
		this.originY = originY;
		this.spacing = spacing;
		this.nx = nx;
		this.ny = ny;
		this.z = new double[nx * ny];
		Arrays.fill(z, Double.NaN);
	}

	public void set(int ix, int iy, double elevation) {
		if (ix >= 0 && iy >= 0 && ix < nx && iy < ny) {
			z[iy * nx + ix] = elevation;
		}
	}

	public double spacing() {
		return spacing;
	}

	/** 땅 높이(해발 m). 모르면 NaN. */
	public double elevationAt(double x, double y) {
		double fx = (x - originX) / spacing;
		double fy = (y - originY) / spacing;
		int ix = (int) Math.floor(fx);
		int iy = (int) Math.floor(fy);
		if (ix < 0 || iy < 0 || ix >= nx - 1 || iy >= ny - 1) {
			return Double.NaN;
		}
		double tx = fx - ix;
		double ty = fy - iy;
		double a = z[iy * nx + ix];
		double b = z[iy * nx + ix + 1];
		double c = z[(iy + 1) * nx + ix];
		double d = z[(iy + 1) * nx + ix + 1];
		return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
	}
}
