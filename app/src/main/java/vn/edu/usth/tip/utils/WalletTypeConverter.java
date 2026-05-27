package vn.edu.usth.tip.utils;

import vn.edu.usth.tip.models.Wallet;

public final class WalletTypeConverter {

    private WalletTypeConverter() {}

    public static String toApiString(Wallet.Type type) {
        if (type == null) return "cash";
        switch (type) {
            case BANK:       return "bank";
            case EWALLET:    return "e_wallet";
            case INVESTMENT: return "investment";
            default:         return "cash";
        }
    }

    public static Wallet.Type fromApiString(String type, Wallet.Type defaultType) {
        if (type == null) return defaultType;
        switch (type.toLowerCase()) {
            case "bank":       return Wallet.Type.BANK;
            case "cash":       return Wallet.Type.CASH;
            case "e_wallet":   return Wallet.Type.EWALLET;
            case "investment": return Wallet.Type.INVESTMENT;
            default:           return defaultType;
        }
    }
}
