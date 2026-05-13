package vn.edu.usth.tip.utils;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bóc tách số tiền VND và tên cửa hàng từ văn bản thô do ML Kit OCR trả về.
 *
 * Chiến lược parseAmount — 4 bước:
 *   Bước 0  PRE-PASS     — Thu thập customer-payment amounts từ dòng blacklisted.
 *   Bước 1  WHITELIST     — Tìm amount cùng dòng / dòng kế với từ khóa tổng tiền.
 *   Bước 1.5 DEFERRED     — Nếu có whitelist keyword nhưng không tìm thấy amount
 *            gần keyword → quét toàn bộ text, lấy số cuối cùng hợp lệ.
 *   Bước 2  FALLBACK      — Số formatted/spaced CUỐI CÙNG trên dòng sạch.
 */
public class InvoiceParser {

    private static final String TAG = "InvoiceParser";

    public static class ParseResult {
        public final long amount;
        public final String note;

        public ParseResult(long amount, String note) {
            this.amount = amount;
            this.note = note;
        }
    }

    // ── BLACKLIST ────────────────────────────────────────────────────────────
    private static final Pattern BLACKLIST = Pattern.compile(
            "ti[eề]n\\s*m[aặ]t"
                    + "|kh[aá]ch"
                    + "|tr[aả]\\s*l[aạ]i"
                    + "|th[oố]i"
                    + "|(?<!\\p{L})m[aã](?!\\p{L})"
                    + "|nh[aâ]n\\s*vi[eê]n"
                    + "|(?<!\\p{L})id(?!\\p{L})"
                    + "|s[oố]\\s*l[uư][oợ]ng"
                    + "|\\bVAT\\b"
                    + "|thu[eế]"
                    + "|gi[aả]m\\s*gi[aá]"
                    + "|chi[eế]t\\s*kh[aấ]u"
                    + "|\\bx\\d",                       // "x1", "x2" — quantity notation
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // ── WHITELIST ────────────────────────────────────────────────────────────
    private static final Pattern WHITELIST = Pattern.compile(
            "t[oổ]ng"
                    + "|c[oộ]ng"
                    + "|th[aà]nh\\s*ti[eề]n"
                    + "|thanh\\s*to[áa]n"
                    + "|g\\s*ti[eề]n"
                    + "|ti[eề]n\\s*h[aà]ng"
                    + "|ph[aả]i\\s*tr[aả]"
                    + "|total|grand\\s*total|amount\\s*due",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // ── PAYMENT_INDICATOR ────────────────────────────────────────────────────
    private static final Pattern PAYMENT_INDICATOR = Pattern.compile(
            "thanh\\s*to[áa]n|ph[aả]i\\s*tr[aả]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // ═══ 2 TẦNG REGEX MỚI (Khắt khe hơn) ════════════════════════════════════
    
    // Tầng 1: Số có dấu phân cách phẩy/chấm (VD: 300.000, 1,250,000)
    private static final Pattern FORMATTED_AMOUNT = Pattern.compile(
            "\\d{1,3}(?:[.,·]\\d{3})+"
    );

    // Tầng 2: Số bất kỳ (kể cả viết liền hay có khoảng trắng) có đi kèm đơn vị tiền tệ (VD: 300000đ, 50000 VND, 49 300 đ)
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile(
            "\\d+(?:[\\s.,·]\\d+)*\\s*(?:vnd|vn[đd]|đ|d)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern PATTERN_SHOP_LINE = Pattern.compile(
            ".*[\\p{L}]{2,}.*"
    );

    private static final long MIN_AMOUNT = 1000;

    // ═══════════════════════════════════════════════════════════════════════════

    public static ParseResult parse(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return new ParseResult(0, "");
        }
        long amount = parseAmount(rawText);
        String note = parseShopName(rawText);
        return new ParseResult(amount, note);
    }

    // ═══ CORE ═══════════════════════════════════════════════════════════════

    private static long parseAmount(String text) {
        String[] lines = text.split("\\n");

        Log.d(TAG, "── parseAmount: " + lines.length + " lines ──");

        /* ── Bước 0: Thu thập customer-payment amounts ── */
        Set<Long> blacklistAmounts = new HashSet<>();
        boolean hasWhitelistKeyword = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (BLACKLIST.matcher(line).find()) {
                Log.d(TAG, "  [BL] line " + i + ": \"" + line + "\"");
                collectAllAmounts(line, blacklistAmounts);
                if (i + 1 < lines.length) {
                    collectAllAmounts(lines[i + 1].trim(), blacklistAmounts);
                }
            }
            if (WHITELIST.matcher(line).find()) {
                hasWhitelistKeyword = true;
            }
        }
        Log.d(TAG, "  blacklistAmounts=" + blacklistAmounts);
        Log.d(TAG, "  hasWhitelistKeyword=" + hasWhitelistKeyword);

        /* ── Bước 1: Whitelist scan — first-match wins ── */
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (BLACKLIST.matcher(line).find()) continue;
            if (!WHITELIST.matcher(line).find()) continue;

            Log.d(TAG, "  [WL] line " + i + ": \"" + line + "\"");

            long amount = extractWhitelistAmount(line);
            if (amount <= 0 && i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                Log.d(TAG, "    → lookahead: \"" + nextLine + "\"");
                amount = extractWhitelistAmount(nextLine);
            }
            if (amount <= 0) {
                Log.d(TAG, "    → no amount found, continue");
                continue;
            }

            if (PAYMENT_INDICATOR.matcher(line).find()
                    && blacklistAmounts.contains(amount)) {
                Log.d(TAG, "    → PAYMENT_INDICATOR + blacklisted " + amount + ", skip");
                continue;
            }

            Log.d(TAG, "  ✓ Bước 1 → " + amount);
            return amount;
        }

        /* ── Bước 1.5: DEFERRED MATCH ── */
        // Khi ML Kit tách label và amount thành 2 block khác nhau (layout cột),
        // whitelist keyword ở dòng riêng, amount ở dòng xa → Bước 1 không bắt được.
        // → Quét toàn bộ text, thu thập tất cả amounts trên dòng sạch,
        //   trả về số cuối cùng hợp lệ (tổng tiền thường ở cuối hóa đơn).
        if (hasWhitelistKeyword) {
            Log.d(TAG, "  [DEFERRED] whitelist keyword found but no amount near it");
            long lastValid = 0;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (BLACKLIST.matcher(line).find()) continue;

                long val = extractFirstFormatted(line);
                if (val == 0) val = extractCurrencyAmount(line);

                if (val >= MIN_AMOUNT && !blacklistAmounts.contains(val)) {
                    Log.d(TAG, "    candidate line " + i + ": \"" + line + "\" → " + val);
                    lastValid = val;
                }
            }
            if (lastValid > 0) {
                Log.d(TAG, "  ✓ Bước 1.5 → " + lastValid);
                return lastValid;
            }
        }

        /* ── Bước 2: Fallback — CUỐI CÙNG trên dòng sạch ── */
        // Tổng tiền thường ở cuối hóa đơn → lấy last-match.
        Log.d(TAG, "  [FALLBACK]");
        long lastFallback = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (BLACKLIST.matcher(line).find()) continue;

            long val = extractFirstFormatted(line);
            if (val == 0) val = extractCurrencyAmount(line);

            if (val >= MIN_AMOUNT && !blacklistAmounts.contains(val)) {
                Log.d(TAG, "    fallback candidate line " + i + ": \"" + line + "\" → " + val);
                lastFallback = val;
            }
        }
        if (lastFallback > 0) {
            Log.d(TAG, "  ✓ Bước 2 → " + lastFallback);
            return lastFallback;
        }

        Log.d(TAG, "  ✗ Không tìm thấy số tiền");
        return 0;
    }

    // ═══ EXTRACTION HELPERS ═════════════════════════════════════════════════

    private static long extractWhitelistAmount(String line) {
        long val = extractFirstFormatted(line);
        if (val >= MIN_AMOUNT) return val;
        val = extractCurrencyAmount(line);
        if (val >= MIN_AMOUNT) return val;
        return 0;
    }

    private static long extractFirstFormatted(String line) {
        Matcher m = FORMATTED_AMOUNT.matcher(line);
        while (m.find()) {
            long val = cleanNumber(m.group());
            if (val >= MIN_AMOUNT) return val;
        }
        return 0;
    }

    private static long extractCurrencyAmount(String line) {
        Matcher m = CURRENCY_AMOUNT.matcher(line);
        while (m.find()) {
            long val = cleanNumber(m.group());
            if (val >= MIN_AMOUNT) return val;
        }
        return 0;
    }

    private static void collectAllAmounts(String line, Set<Long> target) {
        Matcher m1 = FORMATTED_AMOUNT.matcher(line);
        while (m1.find()) target.add(cleanNumber(m1.group()));
        Matcher m2 = CURRENCY_AMOUNT.matcher(line);
        while (m2.find()) target.add(cleanNumber(m2.group()));
    }

    // ═══════════════════════════════════════════════════════════════════════════

    private static String parseShopName(String text) {
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 2) continue;
            if (PATTERN_SHOP_LINE.matcher(trimmed).matches()) {
                if (!trimmed.matches("[\\d/\\-:. ]+")) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    private static long cleanNumber(String raw) {
        if (raw == null) return 0;
        String digits = raw.replaceAll("[^\\d]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
