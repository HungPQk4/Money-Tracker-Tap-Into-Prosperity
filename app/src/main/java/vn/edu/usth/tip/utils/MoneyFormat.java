package vn.edu.usth.tip.utils;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.Locale;

/**
 * Central VND formatter.
 *
 * Đơn vị tiền tệ hiển thị SAU số tiền: "1.500.000 VND"
 *
 * formatShort — viết tắt CHỈ từ 1 nghìn tỷ (1.000.000.000.000) trở lên.
 *   1.500.000         → "1.500.000 VND"         (full)
 *   999.999.999.999   → "999.999.999.999 VND"   (full)
 *   1.500.000.000.000 → "1.5 Nghìn Tỷ VND"      (rút gọn)
 *
 * formatFull — luôn hiển thị đầy đủ, không rút gọn.
 *   1.500.000 → "1.500.000 VND"
 */
public final class MoneyFormat {

    private MoneyFormat() {}

    public static String formatShort(long amount) {
        boolean negative = amount < 0;
        long abs = Math.abs(amount);
        String body;
        if (abs >= 1_000_000_000_000L) {
            body = compact(abs / 1_000_000_000_000.0) + " Nghìn Tỷ";
        } else {
            body = String.format(Locale.US, "%,d", abs).replace(",", ".");
        }
        return (negative ? "-" : "") + body + " VND";
    }

    public static String formatFull(long amount) {
        boolean negative = amount < 0;
        long abs = Math.abs(amount);
        String body = String.format(Locale.US, "%,d", abs).replace(",", ".");
        return (negative ? "-" : "") + body + " VND";
    }

    // "1.0" → "1", "1.5" → "1.5"
    private static String compact(double val) {
        String s = String.format(Locale.US, "%.1f", val);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Returns a SpannableString where " VND" is half-size and non-bold. */
    public static SpannableString styled(String formatted) {
        SpannableString ss = new SpannableString(formatted);
        int vndStart = formatted.lastIndexOf(" VND");
        if (vndStart >= 0) {
            ss.setSpan(new RelativeSizeSpan(0.5f), vndStart, formatted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new StyleSpan(Typeface.NORMAL), vndStart, formatted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    public static SpannableString formatShortStyled(long amount) {
        return styled(formatShort(amount));
    }

    public static SpannableString formatFullStyled(long amount) {
        return styled(formatFull(amount));
    }
}
