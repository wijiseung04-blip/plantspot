package com.plantspot.backend.geo;

/**
 * EPSG:5186 (Korea 2000 / Central Belt 2010, 미터) ↔ 위경도(도) 변환.
 * 타원체 GRS80, 원점 위도 38°, 중앙 자오선 127°, 축척 1.0, 가산값 동 200,000m / 북 600,000m.
 * Snyder(1987) 『Map Projections』 횡메르카토르 공식. GRS80과 WGS84 차이는 1mm 미만이라 같은 것으로 본다.
 */
public final class TmConverter {

	private static final double A = 6378137.0;
	private static final double F = 1 / 298.257222101;
	private static final double E2 = F * (2 - F);
	private static final double EP2 = E2 / (1 - E2);
	private static final double K0 = 1.0;
	private static final double LAT0 = Math.toRadians(38.0);
	private static final double LON0 = Math.toRadians(127.0);
	private static final double FALSE_EASTING = 200_000.0;
	private static final double FALSE_NORTHING = 600_000.0;
	private static final double M0 = meridianArc(LAT0);

	private TmConverter() {
	}

	/** EPSG:5186 (x=동쪽, y=북쪽) → 위경도. */
	public static LatLon toLatLon(double x, double y) {
		double m = M0 + (y - FALSE_NORTHING) / K0;
		double mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256));
		double sq = Math.sqrt(1 - E2);
		double e1 = (1 - sq) / (1 + sq);

		double phi1 = mu
				+ (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
				+ (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
				+ (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu)
				+ (1097 * Math.pow(e1, 4) / 512) * Math.sin(8 * mu);

		double sin1 = Math.sin(phi1);
		double cos1 = Math.cos(phi1);
		double tan1 = Math.tan(phi1);
		double c1 = EP2 * cos1 * cos1;
		double t1 = tan1 * tan1;
		double n1 = A / Math.sqrt(1 - E2 * sin1 * sin1);
		double r1 = A * (1 - E2) / Math.pow(1 - E2 * sin1 * sin1, 1.5);
		double d = (x - FALSE_EASTING) / (n1 * K0);

		double lat = phi1 - (n1 * tan1 / r1) * (d * d / 2
				- (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * Math.pow(d, 4) / 24
				+ (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * Math.pow(d, 6) / 720);
		double lon = LON0 + (d
				- (1 + 2 * t1 + c1) * Math.pow(d, 3) / 6
				+ (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * Math.pow(d, 5) / 120) / cos1;

		return new LatLon(Math.toDegrees(lat), Math.toDegrees(lon));
	}

	/** 위경도 → EPSG:5186. 위경도로 받은 위치를 건물 좌표와 비교할 때 쓴다. */
	public static Tm fromLatLon(double latitudeDeg, double longitudeDeg) {
		double lat = Math.toRadians(latitudeDeg);
		double lon = Math.toRadians(longitudeDeg);
		double sin = Math.sin(lat);
		double cos = Math.cos(lat);
		double tan = Math.tan(lat);

		double n = A / Math.sqrt(1 - E2 * sin * sin);
		double t = tan * tan;
		double c = EP2 * cos * cos;
		double a = (lon - LON0) * cos;
		double m = meridianArc(lat);

		double x = FALSE_EASTING + K0 * n * (a
				+ (1 - t + c) * Math.pow(a, 3) / 6
				+ (5 - 18 * t + t * t + 72 * c - 58 * EP2) * Math.pow(a, 5) / 120);
		double y = FALSE_NORTHING + K0 * (m - M0 + n * tan * (a * a / 2
				+ (5 - t + 9 * c + 4 * c * c) * Math.pow(a, 4) / 24
				+ (61 - 58 * t + t * t + 600 * c - 330 * EP2) * Math.pow(a, 6) / 720));
		return new Tm(x, y);
	}

	/** 적도에서 위도 phi까지의 자오선 호 길이(m). */
	private static double meridianArc(double phi) {
		double e4 = E2 * E2;
		double e6 = e4 * E2;
		return A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi
				- (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * phi)
				+ (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * phi)
				- (35 * e6 / 3072) * Math.sin(6 * phi));
	}

	public record LatLon(double latitude, double longitude) {
	}

	public record Tm(double x, double y) {
	}
}
