package vn.edu.usth.tip.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import vn.edu.usth.tip.network.responses.DashboardSummary;
import vn.edu.usth.tip.network.responses.TransactionResponse;
import vn.edu.usth.tip.repositories.DashboardRepository;

import java.util.List;

public class DashboardViewModel extends AndroidViewModel {
    private final DashboardRepository repository;

    private final MutableLiveData<DashboardSummary> summaryData = new MutableLiveData<>();
    private final MutableLiveData<List<TransactionResponse>> recentTransactionsData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        this.repository = new DashboardRepository(application);
    }

    public LiveData<DashboardSummary> getSummaryData() {
        return summaryData;
    }

    public LiveData<List<TransactionResponse>> getRecentTransactionsData() {
        return recentTransactionsData;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void loadDashboardSummary() {
        isLoading.setValue(true);
        repository.fetchSummary(summaryData, errorMessage);
        isLoading.setValue(false); // In real-world, we'd wait for API callback or use coroutines/RxJava for proper loading state.
    }

    public void loadRecentTransactions(String period) {
        isLoading.setValue(true);
        repository.fetchRecentTransactions(period, recentTransactionsData, errorMessage);
        isLoading.setValue(false);
    }
}
