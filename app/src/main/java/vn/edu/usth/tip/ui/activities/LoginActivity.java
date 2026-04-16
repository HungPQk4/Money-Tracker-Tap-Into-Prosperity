package vn.edu.usth.tip.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.usth.tip.databinding.ActivityLoginBinding;
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
            viewModel.saveAuthData(response);
            Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
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
