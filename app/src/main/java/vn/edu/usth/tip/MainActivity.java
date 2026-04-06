package vn.edu.usth.tip;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private NavController navController;

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
                // Safety guard – should never be tapped
                return false;
            }

            if (id == R.id.goalsFragment) {
                // Show popup so user can pick: Goals / Budgets / Debts
                android.widget.PopupMenu popup = new android.widget.PopupMenu(
                        this,
                        bottomNav.findViewById(R.id.goalsFragment)
                );
                popup.getMenuInflater().inflate(R.menu.menu_goals_dropdown, popup.getMenu());
                popup.setOnMenuItemClickListener(menuItem ->
                        NavigationUI.onNavDestinationSelected(menuItem, navController)
                );
                popup.show();
                // Return false so the bottom nav doesn't visually select Goals immediately
                return false;
            }

            // For all other tabs, use the standard NavigationUI behavior
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        // ── Floating Action Button → New Transaction ──────────────────────
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> navController.navigate(R.id.newTransactionFragment));
    }
}