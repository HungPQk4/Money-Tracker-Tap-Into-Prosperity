package vn.edu.usth.tip.ui.activities;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.utils.TokenManager;
import vn.edu.usth.tip.viewmodels.AccountViewModel;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AccountViewModel accountViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ── Setup Jetpack Navigation ──────────────────────────────────────
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) return;

        navController = navHostFragment.getNavController();
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);

        // Link bottom nav with nav controller (top-level destinations)
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Disable the empty placeholder item in the center (FAB space)
        bottomNav.getMenu().findItem(R.id.placeholder).setEnabled(false);

        // Override item selected: Goals tab shows a dropdown
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.placeholder) {
                return false;
            }

            if (id == R.id.goalsFragment) {
                android.view.ContextThemeWrapper wrapper = new android.view.ContextThemeWrapper(this, R.style.DarkPopupMenuStyle);
                android.widget.PopupMenu popup = new android.widget.PopupMenu(
                        wrapper,
                        bottomNav.findViewById(R.id.goalsFragment)
                );
                popup.getMenuInflater().inflate(R.menu.menu_goals_dropdown, popup.getMenu());
                popup.setOnMenuItemClickListener(menuItem ->
                        NavigationUI.onNavDestinationSelected(menuItem, navController)
                );
                popup.show();
                return false;
            }

            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        // ── Floating Action Button → New Transaction ──────────────────────
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> navController.navigate(R.id.newTransactionFragment));

        // ── Lắng nghe token hết hạn → tự động logout ─────────────────────
        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        accountViewModel.getSessionExpired().observe(this, expired -> {
            if (expired != null && expired) {
                // Xóa token đã lưu
                new TokenManager(this).clear();

                // Reset cờ để tránh trigger nhiều lần
                accountViewModel.clearSessionExpired();

                // Thông báo cho user
                android.widget.Toast.makeText(
                        this,
                        "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại",
                        android.widget.Toast.LENGTH_LONG
                ).show();

                // Chuyển về màn hình Login và xóa toàn bộ back stack
                navController.navigate(R.id.action_global_loginFragment,
                        null,
                        new androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                                .build()
                );
            }
        });
    }
}