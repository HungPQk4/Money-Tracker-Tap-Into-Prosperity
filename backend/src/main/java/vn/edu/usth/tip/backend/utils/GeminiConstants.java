package vn.edu.usth.tip.backend.utils;

public final class GeminiConstants {

    private GeminiConstants() {}

    /** Temperature 0 = fully deterministic — used for structured data extraction (invoice OCR). */
    public static final double TEMPERATURE_DETERMINISTIC = 0.0;

    /** Slight creativity for natural-language generation (AI insights). */
    public static final double TEMPERATURE_CREATIVE = 0.4;

    public static final int CONNECT_TIMEOUT_SECONDS = 15;
    public static final int READ_TIMEOUT_SECONDS    = 30;
}
