package vn.edu.usth.tip.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import vn.edu.usth.tip.network.requests.AccountRequest;
import vn.edu.usth.tip.network.responses.AccountResponse;
import vn.edu.usth.tip.repositories.AccountRepository;

import java.util.List;

public class AccountViewModel extends AndroidViewModel {

    private final AccountRepository repository;

    private final MutableLiveData<List<AccountResponse>> accountsData = new MutableLiveData<>();
    private final MutableLiveData<AccountResponse> createdAccountData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public AccountViewModel(@NonNull Application application) {
        super(application);
        repository = new AccountRepository(application);
    }

    public LiveData<List<AccountResponse>> getAccountsData() {
        return accountsData;
    }

    public LiveData<AccountResponse> getCreatedAccountData() {
        return createdAccountData;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void loadAccounts() {
        isLoading.setValue(true);
        repository.fetchAllAccounts(accountsData, errorMessage);
        isLoading.setValue(false);
    }

    public void createAccount(AccountRequest request) {
        isLoading.setValue(true);
        repository.createAccount(request, createdAccountData, errorMessage);
        isLoading.setValue(false);
    }
}
