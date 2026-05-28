package vn.edu.usth.tip.ui.fragments;

import vn.edu.usth.tip.models.Transaction;
import vn.edu.usth.tip.utils.MoneyFormat;
import vn.edu.usth.tip.viewmodels.AppViewModel;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import vn.edu.usth.tip.R;

public class TransactionDetailSheet extends BottomSheetDialogFragment {

    public interface OnTransactionActionListener {
        void onEdit(Transaction tx);
        void onDelete(Transaction tx);
    }

    private Transaction tx;
    private OnTransactionActionListener actionListener;
    
    private ImageView ivPhoto;
    private CardView cardAddPhoto;
    private CardView cardPhotoView;

    // Light Mode Colors
    private static final int COLOR_EXPENSE_BG   = Color.parseColor("#FEE2E2");
    private static final int COLOR_INCOME_BG    = Color.parseColor("#DCFCE7");
    private static final int COLOR_TRANSFER_BG  = Color.parseColor("#F3E8FF");
    private static final int COLOR_EXPENSE_TEXT = Color.parseColor("#DC2626");
    private static final int COLOR_INCOME_TEXT  = Color.parseColor("#16A34A");
    private static final int COLOR_TRANSFER_TEXT= Color.parseColor("#9333EA");
    private static final int COLOR_ICON_EXPENSE = Color.parseColor("#FEF2F2");
    private static final int COLOR_ICON_INCOME  = Color.parseColor("#F0FDF4");
    private static final int COLOR_ICON_XFER    = Color.parseColor("#FAF5FF");

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    saveImageToInternalStorage(uri);
                }
            });

    public static TransactionDetailSheet newInstance(
            Transaction tx,
            OnTransactionActionListener listener) {
        TransactionDetailSheet sheet = new TransactionDetailSheet();
        sheet.tx = tx;
        sheet.actionListener = listener;
        return sheet;
    }

    @Override
    public int getTheme() {
        return com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_transaction_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (tx == null) { dismiss(); return; }

        int amountColor, badgeBg, iconBg;
        String typeLabel;
        if (tx.getType() == Transaction.Type.INCOME) {
            amountColor = COLOR_INCOME_TEXT;
            badgeBg     = COLOR_INCOME_BG;
            iconBg      = COLOR_ICON_INCOME;
            typeLabel   = "Thu nhập";
        } else if (tx.getType() == Transaction.Type.TRANSFER) {
            amountColor = COLOR_TRANSFER_TEXT;
            badgeBg     = COLOR_TRANSFER_BG;
            iconBg      = COLOR_ICON_XFER;
            typeLabel   = "Chuyển khoản";
        } else {
            amountColor = COLOR_EXPENSE_TEXT;
            badgeBg     = COLOR_EXPENSE_BG;
            iconBg      = COLOR_ICON_EXPENSE;
            typeLabel   = "Chi tiêu";
        }

        // Photo section logic
        cardAddPhoto = view.findViewById(R.id.card_add_photo);
        cardPhotoView = view.findViewById(R.id.card_photo_view);
        ivPhoto = view.findViewById(R.id.iv_transaction_photo);
        View btnEditPhoto = view.findViewById(R.id.btn_edit_photo);

        updatePhotoUI();

        cardAddPhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnEditPhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        
        ivPhoto.setOnClickListener(v -> showFullScreenImage());

        // Standard logic
        CardView cardIcon = view.findViewById(R.id.card_detail_icon);
        cardIcon.setCardBackgroundColor(iconBg);

        ((TextView) view.findViewById(R.id.tv_detail_icon)).setText(tx.getIcon());

        TextView tvAmount = view.findViewById(R.id.tv_detail_amount);
        tvAmount.setText(MoneyFormat.styled(tx.getFormattedAmount()));
        tvAmount.setTextColor(amountColor);

        ((TextView) view.findViewById(R.id.tv_detail_title)).setText(tx.getTitle());

        CardView cardType = view.findViewById(R.id.card_detail_type);
        cardType.setCardBackgroundColor(badgeBg);
        TextView tvType = view.findViewById(R.id.tv_detail_type);
        tvType.setText(typeLabel);
        tvType.setTextColor(amountColor);

        ((TextView) view.findViewById(R.id.tv_detail_category)).setText(tx.getCategory());
        ((TextView) view.findViewById(R.id.tv_detail_wallet)).setText(tx.getWalletName());

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault());
        String dateStr = sdf.format(new Date(tx.getTimestampMs()));
        ((TextView) view.findViewById(R.id.tv_detail_datetime)).setText(dateStr);

        String note = tx.getNote();
        ((TextView) view.findViewById(R.id.tv_detail_note)).setText(
                (note == null || note.isEmpty()) ? "Không có ghi chú" : note);

        view.findViewById(R.id.btn_detail_edit).setOnClickListener(v -> {
            dismiss();
            if (actionListener != null) actionListener.onEdit(tx);
        });

        view.findViewById(R.id.btn_detail_delete).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Xóa giao dịch")
                        .setMessage("Bạn có chắc muốn xóa giao dịch \"" + tx.getTitle() + "\"?")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            if (actionListener != null) actionListener.onDelete(tx);
                            dismiss();
                        })
                        .show()
        );
    }

    private void updatePhotoUI() {
        if (tx.getPhotoUri() != null && !tx.getPhotoUri().isEmpty()) {
            cardAddPhoto.setVisibility(View.GONE);
            cardPhotoView.setVisibility(View.VISIBLE);
            ivPhoto.setImageURI(Uri.parse(tx.getPhotoUri()));
        } else {
            cardAddPhoto.setVisibility(View.VISIBLE);
            cardPhotoView.setVisibility(View.GONE);
        }
    }

    private void showFullScreenImage() {
        if (tx.getPhotoUri() == null || tx.getPhotoUri().isEmpty()) return;
        
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        ImageView fullImageView = new ImageView(requireContext());
        fullImageView.setImageURI(Uri.parse(tx.getPhotoUri()));
        fullImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullImageView.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(fullImageView);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        dialog.show();
    }

    private void saveImageToInternalStorage(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            File file = new File(requireContext().getFilesDir(), "tx_photo_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
            is.close();
            fos.close();
            
            String newUri = Uri.fromFile(file).toString();
            tx.setPhotoUri(newUri);
            
            updatePhotoUI();
            
            AppViewModel appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
            appViewModel.updateTransaction(tx);
            
            Toast.makeText(requireContext(), "Đã lưu ảnh đính kèm", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("TransactionDetail", "saveAttachmentPhoto failed: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Không thể lưu ảnh", Toast.LENGTH_SHORT).show();
        }
    }
}
