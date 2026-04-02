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

import vn.edu.usth.tip.fragment.NewTransactionFragment;

public class MainActivity extends AppCompatActivity {

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

        // Setup Jetpack Navigation
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);

            NavigationUI.setupWithNavController(bottomNavigationView, navController);

            // Disable placeholder item in the middle
            bottomNavigationView.getMenu().getItem(2).setEnabled(false);

            bottomNavigationView.setOnItemSelectedListener(item -> {
                if (item.getItemId() == R.id.goalsFragment) {
                    android.widget.PopupMenu popup = new android.widget.PopupMenu(
                            MainActivity.this,
                            bottomNavigationView.findViewById(R.id.goalsFragment)
                    );
                    popup.getMenuInflater().inflate(R.menu.menu_goals_dropdown, popup.getMenu());
                    popup.setOnMenuItemClickListener(menuItem ->
                            NavigationUI.onNavDestinationSelected(menuItem, navController)
                    );
                    popup.show();
                    return false;
                } else {
                    return NavigationUI.onNavDestinationSelected(item, navController);
                }
            });
        }

        // FAB → mở NewTransactionFragment
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v ->
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.nav_host_fragment, new NewTransactionFragment())
                        .addToBackStack(null)
                        .commit()
        );
    }
}