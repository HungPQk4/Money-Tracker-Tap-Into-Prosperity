package vn.edu.usth.tip.insights.engine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.edu.usth.tip.insights.models.Insight;
import vn.edu.usth.tip.insights.models.InsightPriority;
import vn.edu.usth.tip.insights.models.InsightType;
import vn.edu.usth.tip.models.DebtLoan;

public class DebtLoanAdvisor {

    private static final long ONE_DAY_MS  = 24L * 60 * 60 * 1000;
    private static final long SEVEN_DAYS_MS = 7 * ONE_DAY_MS;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));

    public List<Insight> advise(List<DebtLoan> debtLoans) {
        List<Insight> results = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (DebtLoan dl : debtLoans) {
            Insight insight = buildInsight(dl, now);
            if (insight != null) results.add(insight);
        }
        return results;
    }

    private Insight buildInsight(DebtLoan dl, long now) {
        long dueDate  = dl.getDueDate();
        long amount   = dl.getAmount();
        String name   = dl.getPersonName();
        String reason = dl.getReason();
        String amtStr = Insight.formatVnd(amount);
        String dueDateStr = (dueDate > 0) ? DATE_FMT.format(new Date(dueDate)) : null;

        Insight insight = new Insight();
        insight.debtDueDateMs     = dueDate;
        insight.personName        = name;
        insight.projectedAmountVnd = amount;
        if (reason != null && !reason.isEmpty()) {
            insight.categoryName = reason;
        }

        if (dl.getType() == DebtLoan.TYPE_I_OWE) {
            buildIOweInsight(insight, name, amtStr, dueDate, dueDateStr, now);
        } else {
            buildLentInsight(insight, name, amtStr, dueDate, dueDateStr, now);
        }
        return insight;
    }

    private void buildIOweInsight(Insight i, String name, String amtStr,
                                   long dueDate, String dueDateStr, long now) {
        if (dueDate > 0 && dueDate < now) {
            // Đã quá hạn trả
            long daysOverdue = (now - dueDate) / ONE_DAY_MS;
            i.type     = InsightType.DEBT_OVERDUE;
            i.priority = InsightPriority.HIGH;
            i.title    = "⚠️ Quá hạn: Bạn còn nợ " + name;
            i.body     = "Bạn nợ " + amtStr + " của " + name + " đã quá hạn "
                    + daysOverdue + " ngày (hạn: " + dueDateStr + "). Hãy liên hệ và thanh toán sớm.";
        } else if (dueDate > 0 && dueDate - now <= SEVEN_DAYS_MS) {
            // Sắp đến hạn trong 7 ngày
            int daysLeft = (int) Math.max(0, (dueDate - now) / ONE_DAY_MS);
            i.type     = InsightType.DEBT_DUE_SOON;
            i.priority = InsightPriority.HIGH;
            i.title    = "⏰ Sắp đến hạn trả nợ cho " + name;
            i.body     = "Còn " + daysLeft + " ngày để thanh toán " + amtStr
                    + " cho " + name + " (hạn: " + dueDateStr + ").";
        } else if (dueDate > 0) {
            // Có hạn nhưng còn xa
            i.type     = InsightType.DEBT_DUE_SOON;
            i.priority = InsightPriority.MEDIUM;
            i.title    = "Nhắc nhở: Bạn đang nợ " + name;
            i.body     = "Số tiền " + amtStr + " cần thanh toán cho " + name
                    + " trước ngày " + dueDateStr + ".";
        } else {
            // Không có hạn
            i.type     = InsightType.DEBT_DUE_SOON;
            i.priority = InsightPriority.MEDIUM;
            i.title    = "Nhắc nhở: Bạn đang nợ " + name;
            i.body     = "Bạn đang nợ " + name + " số tiền " + amtStr + " (chưa đặt hạn trả).";
        }
    }

    private void buildLentInsight(Insight i, String name, String amtStr,
                                   long dueDate, String dueDateStr, long now) {
        if (dueDate > 0 && dueDate < now) {
            // Đã quá hạn thu hồi
            long daysOverdue = (now - dueDate) / ONE_DAY_MS;
            i.type     = InsightType.LOAN_REMINDER;
            i.priority = InsightPriority.MEDIUM;
            i.title    = name + " chưa hoàn trả khoản vay";
            i.body     = name + " nợ bạn " + amtStr + " và đã quá hạn "
                    + daysOverdue + " ngày (hạn: " + dueDateStr + ").";
        } else if (dueDate > 0 && dueDate - now <= SEVEN_DAYS_MS) {
            // Sắp đến hạn nhận lại tiền
            int daysLeft = (int) Math.max(0, (dueDate - now) / ONE_DAY_MS);
            i.type     = InsightType.LOAN_REMINDER;
            i.priority = InsightPriority.MEDIUM;
            i.title    = "Khoản cho " + name + " vay sắp đến hạn";
            i.body     = name + " sắp đến hạn trả bạn " + amtStr
                    + " (còn " + daysLeft + " ngày, hạn: " + dueDateStr + ").";
        } else if (dueDate > 0) {
            // Có hạn còn xa
            i.type     = InsightType.LOAN_REMINDER;
            i.priority = InsightPriority.LOW;
            i.title    = "Theo dõi: " + name + " đang nợ bạn";
            i.body     = name + " đang nợ bạn " + amtStr + ", hạn trả: " + dueDateStr + ".";
        } else {
            // Không có hạn
            i.type     = InsightType.LOAN_REMINDER;
            i.priority = InsightPriority.LOW;
            i.title    = "Theo dõi: " + name + " đang nợ bạn";
            i.body     = name + " đang nợ bạn " + amtStr + " (chưa đặt hạn trả).";
        }
    }
}
