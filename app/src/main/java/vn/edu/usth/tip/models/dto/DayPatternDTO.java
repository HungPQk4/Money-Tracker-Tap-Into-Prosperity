package vn.edu.usth.tip.models.dto;

public class DayPatternDTO {
    public String dayOfWeek;  // "0"=CN, "1"=Hai, ..., "6"=Bảy (từ strftime('%w'))
    public double avgSpend;   // Trung bình chi tiêu ngày đó
}
