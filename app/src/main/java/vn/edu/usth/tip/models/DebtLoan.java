package vn.edu.usth.tip.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "debt_loans")
public class DebtLoan {

    public static final int TYPE_I_OWE = 0; // Mình nợ
    public static final int TYPE_LENT = 1;  // Cho vay

    @PrimaryKey
    @NonNull
    private String id;
    private String personName;
    private String reason;
    private long amount;
    private long dueDate; // Timestamp
    private int type;
    private boolean isSynced = false;

    public DebtLoan(@NonNull String id, String personName, String reason, long amount, long dueDate, int type) {
        this.id = id;
        this.personName = personName;
        this.reason = reason;
        this.amount = amount;
        this.dueDate = dueDate;
        this.type = type;
        this.isSynced = false;
    }

    @NonNull
    public String getId() { return id; }
    public String getPersonName() { return personName; }
    public String getReason() { return reason; }
    public long getAmount() { return amount; }
    public long getDueDate() { return dueDate; }
    public int getType() { return type; }
    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
}
