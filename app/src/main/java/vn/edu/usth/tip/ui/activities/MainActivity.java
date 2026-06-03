package vn.edu.usth.tip.ui.activities;

import vn.edu.usth.tip.AppDatabase;
import vn.edu.usth.tip.R;
import vn.edu.usth.tip.utils.AnimationConstants;
import vn.edu.usth.tip.utils.AnimUtils;
import vn.edu.usth.tip.utils.SessionManager;
import vn.edu.usth.tip.utils.SyncPrefs;
import vn.edu.usth.tip.utils.TokenManager;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

// Bỏ import BottomAppBar vì bar giờ là LinearLayout phẳng (xem activity_main.xml).
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private NavController navController;

    // Bar giờ là LinearLayout (không còn BottomAppBar). Khai báo dạng View
    // để tất cả method animate()/setPadding()/setLayoutParams()/setVisibility()...
    // dùng API của lớp View cơ sở, chạy y nguyên không cần đổi gì khác.
    private View bottomAppBar;
    private FloatingActionButton fab;
    private LinearLayout navHome, navStats, navGoals, navWallet;
    private LinearLayout activeTab = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) return;
        navController = navHostFragment.getNavController();

        bottomAppBar = findViewById(R.id.bottomAppBar);
        fab = findViewById(R.id.fab);

        applyWindowInsets();
        setupBottomNav();
        setupFab();
        setupSessionExpiry();
        handleWidgetIntent(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        handleWidgetIntent(intent);
    }

    private void handleWidgetIntent(android.content.Intent intent) {
        if (intent != null && intent.getBooleanExtra("open_new_transaction", false)) {
            intent.removeExtra("open_new_transaction");
            navController.navigate(R.id.newTransactionFragment);
        }
    }

    /**
     * Inset handling đúng cách cho edge-to-edge:
     * - Root: chừa khoảng cho status bar trên + cutout 2 bên, KHÔNG chừa dưới.
     * - Bar: tự cao 68dp + chiều cao gesture bar, padding bottom = gesture bar.
     *   → Bar trắng tràn xuống đáy màn hình, icon/label/FAB nằm phía trên gesture bar.
     */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        // Đổi từ BottomAppBar → View vì bar giờ là LinearLayout.
        View bar = findViewById(R.id.bottomAppBar);
        final int baseHeightPx = (int) (68 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(bar, (v, insets) -> {
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = baseHeightPx + nav.bottom;
            v.setLayoutParams(lp);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), nav.bottom);
            return insets;
        });
    }

    private void setupBottomNav() {
        navHome   = findViewById(R.id.navHome);
        navStats  = findViewById(R.id.navStats);
        navGoals  = findViewById(R.id.navGoals);
        navWallet = findViewById(R.id.navWallet);

        NavOptions tabOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build();

        navHome.setOnClickListener(v ->
                navController.navigate(R.id.dashboardFragment, null, tabOptions));
        navStats.setOnClickListener(v ->
                navController.navigate(R.id.analyticsFragment, null, tabOptions));
        navWallet.setOnClickListener(v ->
                navController.navigate(R.id.walletManagementFragment, null, tabOptions));
        navGoals.setOnClickListener(v -> {
            android.view.ContextThemeWrapper wrapper =
                    new android.view.ContextThemeWrapper(this, R.style.LightPopupMenuStyle);
            android.widget.PopupMenu popup = new android.widget.PopupMenu(wrapper, navGoals);
            popup.getMenuInflater().inflate(R.menu.menu_goals_dropdown, popup.getMenu());
            popup.setOnMenuItemClickListener(item ->
                    NavigationUI.onNavDestinationSelected(item, navController));
            popup.show();
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            updateNavSelection(destId);
            updateBottomBarVisibility(destId);
        });
    }

    private void updateNavSelection(int destId) {
        boolean homeActive   = destId == R.id.dashboardFragment;
        boolean statsActive  = destId == R.id.analyticsFragment;
        boolean goalsActive  = destId == R.id.goalsFragment
                || destId == R.id.budgetsFragment
                || destId == R.id.debtsFragment;
        boolean walletActive = destId == R.id.walletManagementFragment
                || destId == R.id.profileFragment;

        LinearLayout newActive = homeActive   ? navHome
                : statsActive  ? navStats
                : goalsActive  ? navGoals
                : walletActive ? navWallet
                : null;

        if (newActive != null && newActive != activeTab) {
            if (activeTab != null) deactivateTab(activeTab);
            activateTab(newActive);
            activeTab = newActive;
        }

        navHome.setActivated(homeActive);
        navStats.setActivated(statsActive);
        navGoals.setActivated(goalsActive);
        navWallet.setActivated(walletActive);
    }

    private void activateTab(LinearLayout tab) {
        tab.animate().cancel();
        View icon = tab.getChildAt(0);
        if (icon != null) icon.animate().cancel();

        tab.animate()
                .translationY(dpToPx(-7f))
                .setDuration(AnimationConstants.NAV_SLIDE_UP_DURATION_MS)
                .setInterpolator(new OvershootInterpolator(AnimationConstants.NAV_OVERSHOOT_TENSION))
                .withEndAction(() -> tab.animate()
                        .translationY(0f)
                        .setDuration(AnimationConstants.NAV_SLIDE_DOWN_DURATION_MS)
                        .setInterpolator(new DecelerateInterpolator(AnimationConstants.NAV_DECELERATE_FACTOR))
                        .start())
                .start();

        if (icon != null) {
            icon.animate()
                    .scaleX(1.4f).scaleY(1.4f)
                    .setDuration(AnimationConstants.NAV_ICON_SCALE_DURATION_MS)
                    .setInterpolator(new OvershootInterpolator(AnimationConstants.NAV_ICON_OVERSHOOT_TENSION))
                    .withEndAction(() -> icon.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(AnimationConstants.NAV_ICON_RESET_DURATION_MS)
                            .setInterpolator(new DecelerateInterpolator())
                            .start())
                    .start();
        }
    }

    private void deactivateTab(LinearLayout tab) {
        tab.animate().cancel();
        View icon = tab.getChildAt(0);
        if (icon != null) {
            icon.animate().cancel();
            icon.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(AnimationConstants.NAV_DEACTIVATE_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        tab.animate()
                .translationY(0f)
                .setDuration(AnimationConstants.NAV_DEACTIVATE_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void updateBottomBarVisibility(int destId) {
        boolean hide = destId == R.id.newTransactionFragment
                || destId == R.id.scanReceiptFragment;
        if (hide) {
            float slideY = bottomAppBar.getHeight() > 0
                    ? bottomAppBar.getHeight()
                    : dpToPx(68f);
            bottomAppBar.animate()
                    .translationY(slideY)
                    .setDuration(230)
                    .setInterpolator(new AccelerateInterpolator(1.5f))
                    .withEndAction(() -> {
                        bottomAppBar.setVisibility(View.GONE);
                        bottomAppBar.setTranslationY(0);
                    })
                    .start();
            fab.hide();
        } else if (bottomAppBar.getVisibility() != View.VISIBLE) {
            bottomAppBar.setTranslationY(dpToPx(68f));
            bottomAppBar.setVisibility(View.VISIBLE);
            bottomAppBar.animate()
                    .translationY(0)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(1.5f))
                    .start();
            fab.show();
        }
    }

    private void setupFab() {
        fab.setOnClickListener(v ->
                AnimUtils.bounceClick(v, () -> navController.navigate(R.id.newTransactionFragment)));
    }

    private void setupSessionExpiry() {
        SessionManager.getInstance().getSessionExpiredEvent().observe(this, expired -> {
            if (expired == null || !expired) return;
            SessionManager.getInstance().clearSessionExpired();
            // Clear DB + cursors on background thread so the next login always starts
            // from a completely clean state, regardless of which account logs in next.
            android.content.Context appCtx = getApplicationContext();
            // Huỷ tất cả request đang bay ngay lập tức (tránh zombie response).
            vn.edu.usth.tip.network.RetrofitClient.cancelAllRequests();
            // Cancel pending sync worker trước khi xóa DB.
            androidx.work.WorkManager.getInstance(appCtx).cancelUniqueWork("TxSync");
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase.getDatabase(appCtx).clearAllTables();
                SyncPrefs.clearAll(appCtx);
                new Handler(Looper.getMainLooper()).post(() -> {
                    TokenManager.getOrCreate(appCtx).clear();
                    android.widget.Toast.makeText(
                            appCtx,
                            "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại",
                            android.widget.Toast.LENGTH_LONG
                    ).show();
                    Intent intent = new Intent(appCtx, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    appCtx.startActivity(intent);
                });
            });
        });
    }
}