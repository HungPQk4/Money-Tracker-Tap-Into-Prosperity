package vn.edu.usth.tip.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

import vn.edu.usth.tip.repositories.TransactionRepository;

/**
 * Background worker that pushes unsynced transactions to the server.
 *
 * Uses WorkManager with CONNECTED constraint so it only runs when there is
 * a network — and with EXPONENTIAL backoff so 5xx errors self-heal without
 * hammering the server.
 *
 * doWork() MUST be synchronous.  All network calls inside the Repository
 * use Retrofit .execute() (blocking) rather than .enqueue() (async).
 * Using .enqueue() here would cause doWork() to return Result.success()
 * before the API call finishes, letting the OS kill the network thread.
 */
public class TransactionSyncWorker extends Worker {

    public TransactionSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        TransactionRepository repo = new TransactionRepository(getApplicationContext());
        try {
            boolean shouldRetry = repo.pushUnsyncedBatchSync();
            return shouldRetry ? Result.retry() : Result.success();
        } catch (IOException e) {
            // Network lost mid-request — WorkManager will retry with backoff
            android.util.Log.w("TxSyncWorker", "Network error, will retry: " + e.getMessage());
            return Result.retry();
        } catch (Exception e) {
            android.util.Log.e("TxSyncWorker", "Non-recoverable sync error", e);
            return Result.failure();
        }
    }
}
