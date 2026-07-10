package vn.edu.usth.tip.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

import vn.edu.usth.tip.repositories.BudgetsRepository;
import vn.edu.usth.tip.repositories.DebtsRepository;
import vn.edu.usth.tip.repositories.GoalsRepository;
import vn.edu.usth.tip.repositories.TransactionRepository;

/**
 * Background worker: full multi-device sync (push → pull delta in FK order).
 * Order: push → categories → accounts → budgets → goals → transactions.
 *
 * Uses WorkManager with CONNECTED constraint + EXPONENTIAL backoff.
 * doWork() MUST be synchronous — all network calls use Retrofit .execute().
 */
public class TransactionSyncWorker extends Worker {

    public TransactionSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        TransactionRepository txRepo      = new TransactionRepository(ctx);
        BudgetsRepository     budgetRepo  = new BudgetsRepository(ctx);
        GoalsRepository       goalRepo    = new GoalsRepository(ctx);
        DebtsRepository       debtRepo    = new DebtsRepository(ctx);
        try {
            // Phase 1: push unsynced transactions, pull categories + accounts (master data)
            boolean retry = txRepo.fullSyncPhase1();
            if (retry) return Result.retry();

            // Mỗi entity master: PUSH thay đổi cục bộ (create/update/delete) TRƯỚC, rồi PULL delta.
            retry = budgetRepo.pushUnsyncedBlocking(); if (retry) return Result.retry();
            retry = budgetRepo.syncDeltaBlocking();    if (retry) return Result.retry();

            retry = goalRepo.pushUnsyncedBlocking();   if (retry) return Result.retry();
            retry = goalRepo.syncDeltaBlocking();      if (retry) return Result.retry();

            retry = debtRepo.pushUnsyncedBlocking();   if (retry) return Result.retry();
            retry = debtRepo.syncDeltaBlocking();      if (retry) return Result.retry();

            // Pull transactions last (depends on categories + accounts)
            retry = txRepo.pullDeltaTransactionsSync();
            if (retry) return Result.retry();

            return Result.success();
        } catch (IOException e) {
            android.util.Log.w("TxSyncWorker", "Network error, will retry: " + e.getMessage());
            return Result.retry();
        } catch (Exception e) {
            android.util.Log.e("TxSyncWorker", "Non-recoverable sync error", e);
            return Result.failure();
        }
    }
}
