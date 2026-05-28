package vn.edu.usth.tip.models;

import androidx.room.TypeConverter;

public class Converters {

    @TypeConverter
    public static Transaction.Type typeFromString(String value) {
        if (value == null) return Transaction.Type.EXPENSE;
        try {
            return Transaction.Type.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Transaction.Type.EXPENSE;
        }
    }

    @TypeConverter
    public static String typeToString(Transaction.Type type) {
        if (type == null) return Transaction.Type.EXPENSE.name();
        return type.name();
    }

    @TypeConverter
    public static Wallet.Type walletTypeFromString(String value) {
        if (value == null) return Wallet.Type.OTHER;
        try {
            return Wallet.Type.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Wallet.Type.OTHER;
        }
    }

    @TypeConverter
    public static String walletTypeToString(Wallet.Type type) {
        if (type == null) return Wallet.Type.OTHER.name();
        return type.name();
    }
}
