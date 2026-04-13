package vn.edu.usth.tip.models;

public class Debt {
    public enum Type { DEBT, LOAN }

    private String id;
    private String name;
    private long amount;
    private Type type;
    private long dueDateMs;

    public Debt(String id, String name, long amount, Type type, long dueDateMs) {
        this.id = id;
        this.name = name;
        this.amount = amount;
        this.type = type;
        this.dueDateMs = dueDateMs;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public long getAmount() { return amount; }
    public Type getType() { return type; }
    public long getDueDateMs() { return dueDateMs; }
}
