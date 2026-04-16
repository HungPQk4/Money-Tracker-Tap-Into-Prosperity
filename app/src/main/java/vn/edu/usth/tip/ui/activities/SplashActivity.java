package vn.edu.usth.tip.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import vn.edu.usth.tip.utils.TokenManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TokenManager tokenManager = new TokenManager(this);
        
        Intent intent;
        if (tokenManager.getToken() != null) {
            // Đã đăng nhập -> Vào màn hình chính
            intent = new Intent(this, MainActivity.class);
        } else {
            // Chưa đăng nhập -> Vào màn hình đăng nhập
            intent = new Intent(this, LoginActivity.class);
        }
        
        startActivity(intent);
        finish();
    }
}
