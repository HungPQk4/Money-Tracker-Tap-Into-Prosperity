package vn.edu.usth.tip.models;

import androidx.room.TypeConverter;

public class Converters {

    @TypeConverter
    public static Transaction.Type typeFromString(String value) {
        if (value == null) return null;
        try {
            return Transaction.Type.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Transaction.Type.EXPENSE;
        }
    }

    @TypeConverter
    public static String typeToString(Transaction.Type type) {
        if (type == null) return null;
        return type.name();
    }

    @TypeConverter
    public static Wallet.Type walletTypeFromString(String value) {
        if (value == null) return null;
        try {
            return Wallet.Type.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Wallet.Type.OTHER;
        }
    }

    @TypeConverter
    public static String walletTypeToString(Wallet.Type type) {
        if (type == null) return null;
        return type.name();
    }
}
