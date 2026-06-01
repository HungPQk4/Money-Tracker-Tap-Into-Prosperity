package vn.edu.usth.tip.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.databinding.ActivityLoginBinding;
import vn.edu.usth.tip.utils.SyncPrefs;
import vn.edu.usth.tip.viewmodels.LoginViewModel;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setupListeners();
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getLoginSuccess().observe(this, response -> {
            viewModel.setLoading(false);
            binding.btnLogin.setEnabled(true);

            String oldUserId = viewModel.getCurrentUserId();
            String newUserId = response.getUserId() != null ? response.getUserId().toString() : null;
            // userChanged: explicit account switch (oldUserId known and different).
            // noCredentials: oldUserId was wiped (session expiry/crash) but DB may still have
            // stale rows — clear as a safety net even though setupSessionExpiry already did so.
            boolean userChanged   = oldUserId != null && !oldUserId.equals(newUserId);
            boolean noCredentials = oldUserId == null;

            if (userChanged || noCredentials) {
                // Wipe stale local data on background thread before entering MainActivity
                // so the new user never sees data belonging to a previous account.
                android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
                pd.setMessage("Đang chuẩn bị dữ liệu...");
                pd.setCancelable(false);
                pd.show();
                android.content.Context appCtx = getApplicationContext();
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    AppDatabase.getDatabase(appCtx).clearAllTables();
                    SyncPrefs.clearAll(appCtx);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        pd.dismiss();
                        viewModel.saveAuthData(response);
                        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    });
                });
            } else {
                // Same account re-login — DB is already scoped to this user, no clear needed.
                viewModel.saveAuthData(response);
                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }
        });

        viewModel.getLoginError().observe(this, error -> {
            viewModel.setLoading(false);
            binding.btnLogin.setEnabled(true);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            binding.btnLogin.setEnabled(!isLoading);
            // Có thể thêm ProgressDialog ở đây nếu cần
        });
    }

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            } else {
                viewModel.login(email, password);
            }
        });

        binding.tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(this, "Chức năng đang phát triển", Toast.LENGTH_SHORT).show();
        });

        binding.tvSignup.setOnClickListener(v -> {
            Intent intent = new Intent(this, SignupActivity.class);
            startActivity(intent);
        });

        binding.btnGoogle.setOnClickListener(v -> {
            Toast.makeText(this, "Đăng nhập Google đang phát triển", Toast.LENGTH_SHORT).show();
        });
    }
}
