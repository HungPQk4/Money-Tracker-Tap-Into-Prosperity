package vn.edu.usth.tip.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import vn.edu.usth.tip.network.responses.AuthResponse;
import vn.edu.usth.tip.repositories.AuthRepository;
import vn.edu.usth.tip.utils.TokenManager;

public class LoginViewModel extends AndroidViewModel {
    private AuthRepository repository;
    private TokenManager tokenManager;

    private MutableLiveData<AuthResponse> loginSuccess = new MutableLiveData<>();
    private MutableLiveData<String> loginError = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public LoginViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository();
        tokenManager = new TokenManager(application);
    }

    public LiveData<AuthResponse> getLoginSuccess() { return loginSuccess; }
    public LiveData<String> getLoginError() { return loginError; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    public void login(String email, String password) {
        isLoading.setValue(true);
        repository.login(email, password, loginSuccess, loginError);
        // Lưu ý: Trong thực tế, repository nên xử lý việc tắt isLoading 
        // ở đây ta đơn giản hóa để quan sát
    }

    public void saveAuthData(AuthResponse response) {
        tokenManager.saveToken(response.getToken(), response.getFullName());
    }
}
