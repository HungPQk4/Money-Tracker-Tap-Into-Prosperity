package vn.edu.usth.tip.models.dto;

public class DailySpendDTO {
    public int dayNum;      // 1..31, kết quả từ CAST(strftime('%d',...) AS INTEGER)
    public long totalVnd;   // Tổng chi tiêu trong ngày đó (chưa cộng dồn)
}
