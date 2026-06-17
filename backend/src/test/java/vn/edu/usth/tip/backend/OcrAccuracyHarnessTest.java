package vn.edu.usth.tip.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.usth.tip.backend.dto.InvoiceAnalysisResponse;
import vn.edu.usth.tip.backend.services.GeminiService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workstream D — Hybrid OCR field-level accuracy (lightweight, user-run).
 *
 * Disabled unless GEMINI_API_KEY is set, so it never runs in the normal suite, never
 * costs API calls, and never needs network. See docs/eval/README.md for how to supply
 * receipts + ground truth. Prints an OCR_BENCH_BEGIN…OCR_BENCH_END block to paste into
 * the thesis table "Hybrid OCR field-level accuracy".
 */
@SpringBootTest(properties = {"gemini.api.key=${GEMINI_API_KEY:test}"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class OcrAccuracyHarnessTest {

    private static final Path EVAL_DIR = Path.of("..", "docs", "eval");
    private static final Path CSV      = EVAL_DIR.resolve("ocr_receipts.csv");
    private static final Path RECEIPTS = EVAL_DIR.resolve("receipts");

    @Autowired private GeminiService geminiService;

    @Test
    void measureHybridOcrAccuracy() throws Exception {
        List<String> lines = Files.readAllLines(CSV);
        assertThat(lines).as("ocr_receipts.csv has a header + at least one row").hasSizeGreaterThan(1);

        int n = 0, amountOk = 0, dateOk = 0, dateDenom = 0, shopOk = 0, usable = 0, fallback = 0;
        StringBuilder rows = new StringBuilder();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] c = line.split(",", 4);
            if (c.length < 4) continue;
            String file = c[0].trim();
            long  gtAmount = Long.parseLong(c[1].trim());
            String gtDate  = c[2].trim();
            String gtShop  = c[3].trim();

            String ocrText = Files.readString(RECEIPTS.resolve(file));
            InvoiceAnalysisResponse r = geminiService.analyzeInvoice(ocrText);

            boolean aOk = r.getAmount() == gtAmount;
            boolean dOk = !gtDate.isEmpty() && gtDate.equals(r.getDate());
            boolean sOk = fuzzyEq(gtShop, r.getShopName());
            boolean use = aOk && (dOk || sOk);

            n++;
            if (aOk) amountOk++;
            if (!gtDate.isEmpty()) { dateDenom++; if (dOk) dateOk++; }
            if (sOk) shopOk++;
            if (use) usable++;
            if (!r.isSuccess()) fallback++;

            rows.append(String.format("%-22s amount:%-5s date:%-5s shop:%-5s %s%n",
                    file, aOk ? "OK" : "x", dOk ? "OK" : "x", sOk ? "OK" : "x",
                    r.isSuccess() ? "" : "(fallback) " + r.getErrorMessage()));
        }

        StringBuilder out = new StringBuilder("\n==== OCR_BENCH_BEGIN ====\n");
        out.append("receipts N=").append(n).append('\n').append(rows).append('\n');
        out.append(String.format("Field      Correct/N      Accuracy%n"));
        out.append(String.format("amount     %d/%d          %.1f%%%n", amountOk, n, pct(amountOk, n)));
        out.append(String.format("date       %d/%d          %.1f%%%n", dateOk, dateDenom, pct(dateOk, dateDenom)));
        out.append(String.format("shopName   %d/%d          %.1f%%%n", shopOk, n, pct(shopOk, n)));
        out.append(String.format("usable pre-fill rate: %d/%d  %.1f%%%n", usable, n, pct(usable, n)));
        out.append(String.format("fallback rate:        %d/%d  %.1f%%%n", fallback, n, pct(fallback, n)));
        out.append("==== OCR_BENCH_END ====\n");
        System.out.println(out);
    }

    /** Diacritic/spacing-insensitive containment match, either direction. */
    private static boolean fuzzyEq(String gt, String pred) {
        if (gt == null || pred == null) return false;
        String a = norm(gt), b = norm(pred);
        return !a.isEmpty() && !b.isEmpty() && (b.contains(a) || a.contains(b));
    }

    private static String norm(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return d.toLowerCase().replaceAll("\\s+", "").trim();
    }

    private static double pct(int num, int denom) { return denom == 0 ? 0.0 : 100.0 * num / denom; }
}
