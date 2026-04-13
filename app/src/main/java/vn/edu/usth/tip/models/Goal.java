package vn.edu.usth.tip.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "goals")
public class Goal {
    
    @PrimaryKey
    @NonNull
    private String id;
    
    private String name;
    private String emoji;
    private long targetAmount;
    private long savedAmount;
    private long targetDateMs;
    private String colorHex;

    public Goal(@NonNull String id, String name, String emoji, long targetAmount, long savedAmount, long targetDateMs, String colorHex) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.targetDateMs = targetDateMs;
        this.colorHex = colorHex;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public long getTargetAmount() { return targetAmount; }
    public void setTargetAmount(long targetAmount) { this.targetAmount = targetAmount; }

    public long getSavedAmount() { return savedAmount; }
    public void setSavedAmount(long savedAmount) { this.savedAmount = savedAmount; }

    public long getTargetDateMs() { return targetDateMs; }
    public void setTargetDateMs(long targetDateMs) { this.targetDateMs = targetDateMs; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
}
