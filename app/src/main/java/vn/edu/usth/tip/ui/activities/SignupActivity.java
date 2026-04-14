package vn.edu.usth.tip.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import vn.edu.usth.tip.databinding.ActivitySignupBinding;
import vn.edu.usth.tip.viewmodels.LoginViewModel; // We use the same ViewModel for Auth

public class SignupActivity extends AppCompatActivity {

    private ActivitySignupBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setupListeners();
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getLoginSuccess().observe(this, response -> {
            binding.btnSignup.setEnabled(true);
            viewModel.saveAuthData(response);
            Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
            
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        viewModel.getLoginError().observe(this, error -> {
            binding.btnSignup.setEnabled(true);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.tvLogin.setOnClickListener(v -> finish());

        binding.btnSignup.setOnClickListener(v -> {
            String fullName = binding.etFullName.getText().toString().trim();
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Mật khẩu không khớp", Toast.LENGTH_SHORT).show();
                return;
            }

            binding.btnSignup.setEnabled(false);
            viewModel.register(email, password, fullName);
        });
    }
}
