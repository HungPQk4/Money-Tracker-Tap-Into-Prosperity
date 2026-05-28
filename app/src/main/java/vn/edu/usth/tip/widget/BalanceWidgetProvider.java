package vn.edu.usth.tip.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.R;
import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.models.Wallet;
import vn.edu.usth.tip.ui.activities.MainActivity;

public class BalanceWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        final Context appContext = context.getApplicationContext();
        final PendingResult result = goAsync();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(appContext);

                // 1. Tổng tài sản
                List<Wallet> wallets = db.walletDao().getAllWalletsSync();
                long totalAssets = 0;
                if (wallets != null) {
                    for (Wallet w : wallets) {
                        if (w.isIncludedInTotal()) totalAssets += w.getBalanceVnd();
                    }
                }

                // 2. Tài sản ròng = totalAssets - nợ + cho vay
                long iOwe     = db.debtLoanDao().getTotalIOweSync();
                long owedToMe = db.debtLoanDao().getTotalOwedToMeSync();
                long netWorth = totalAssets - iOwe + owedToMe;

                // 3. Giao dịch gần nhất (tối đa 3)
                List<Transaction> recentTxs = db.transactionDao().getRecentTransactionsSync(3);

                // 4. Format số tiền Locale vi-VN
                NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
                nf.setMaximumFractionDigits(0);
                String assetsText   = nf.format(totalAssets) + " VND";
                String netWorthText = nf.format(netWorth) + " VND";

                // 5. PendingIntent (requestCode=0): click toàn bộ widget → mở MainActivity
                Intent launchMain = new Intent(appContext, MainActivity.class);
                launchMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent piMain = PendingIntent.getActivity(appContext, 0, launchMain,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                // 6. Cập nhật từng widget instance
                for (int id : appWidgetIds) {
                    RemoteViews views = new RemoteViews(
                            appContext.getPackageName(), R.layout.widget_balance);

                    views.setTextViewText(R.id.widget_total_assets, assetsText);
                    views.setTextViewText(R.id.widget_net_worth, netWorthText);
                    views.setOnClickPendingIntent(R.id.widget_root, piMain);

                    // Reset tất cả row về GONE trước khi điền data mới
                    views.setViewVisibility(R.id.widget_tx_row_1, View.GONE);
                    views.setViewVisibility(R.id.widget_tx_row_2, View.GONE);
                    views.setViewVisibility(R.id.widget_tx_row_3, View.GONE);

                    if (recentTxs != null && !recentTxs.isEmpty()) {
                        views.setViewVisibility(R.id.widget_tx_divider, View.VISIBLE);
                        views.setViewVisibility(R.id.widget_tx_section, View.VISIBLE);

                        int[] iconIds   = { R.id.widget_tx_icon_1,   R.id.widget_tx_icon_2,   R.id.widget_tx_icon_3   };
                        int[] nameIds   = { R.id.widget_tx_name_1,   R.id.widget_tx_name_2,   R.id.widget_tx_name_3   };
                        int[] amountIds = { R.id.widget_tx_amount_1, R.id.widget_tx_amount_2, R.id.widget_tx_amount_3 };
                        int[] rowIds    = { R.id.widget_tx_row_1,    R.id.widget_tx_row_2,    R.id.widget_tx_row_3    };

                        for (int i = 0; i < recentTxs.size() && i < 3; i++) {
                            Transaction tx = recentTxs.get(i);
                            views.setViewVisibility(rowIds[i], View.VISIBLE);

                            boolean isIncome   = tx.getType() == Transaction.Type.INCOME;
                            boolean isTransfer = tx.getType() == Transaction.Type.TRANSFER;

                            // Icon: mũi tên lên (xanh) hoặc mũi tên xuống (đỏ)
                            views.setImageViewResource(iconIds[i],
                                    isIncome ? R.drawable.ic_widget_income
                                             : R.drawable.ic_widget_expense);

                            // Tên danh mục
                            String name = (tx.getCategory() != null && !tx.getCategory().isEmpty())
                                    ? tx.getCategory() : "—";
                            views.setTextViewText(nameIds[i], name);

                            // Số tiền có dấu + / - / →
                            String prefix = isIncome ? "+" : (isTransfer ? "→" : "-");
                            views.setTextViewText(amountIds[i],
                                    prefix + nf.format(tx.getAmountVnd()) + " VND");

                            // Màu: xanh lá (thu) · đỏ (chi) · xám (chuyển)
                            int color = isIncome   ? 0xFF22C55E
                                      : isTransfer ? 0xFF6B7280
                                      :              0xFFEF4444;
                            views.setTextColor(amountIds[i], color);
                        }
                    } else {
                        views.setViewVisibility(R.id.widget_tx_divider, View.GONE);
                        views.setViewVisibility(R.id.widget_tx_section, View.GONE);
                    }

                    mgr.updateAppWidget(id, views);
                }
            } finally {
                result.finish();
            }
        });
    }

    /**
     * Gửi broadcast ép tất cả widget instance làm mới ngay lập tức.
     * Phải gọi từ bên trong executor lambda SAU KHI Room đã ghi xong,
     * nếu không widget query DB sẽ thấy dữ liệu cũ (race condition).
     */
    public static void sendUpdateBroadcast(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(
                new ComponentName(context, BalanceWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        Intent intent = new Intent(context, BalanceWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(intent);
    }
}
