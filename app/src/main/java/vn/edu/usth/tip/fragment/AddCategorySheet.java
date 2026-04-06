package vn.edu.usth.tip.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.UUID;

import vn.edu.usth.tip.Category;
import vn.edu.usth.tip.R;

public class AddCategorySheet extends BottomSheetDialogFragment {

    public interface OnCategoryAddedListener {
        void onCategoryAdded(Category category);
    }

    private OnCategoryAddedListener listener;
    private String selectedEmoji = "⭐";
    private TextView tvPreviewIcon;
    private EditText etName;

    public static AddCategorySheet newInstance(OnCategoryAddedListener listener) {
        AddCategorySheet fragment = new AddCategorySheet();
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_category, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvPreviewIcon = view.findViewById(R.id.tv_category_icon_preview);
        etName = view.findViewById(R.id.et_category_name);

        setupEmojiClicks(view);

        view.findViewById(R.id.btn_add_category_save).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(getContext(), "Vui lòng nhập tên danh mục", Toast.LENGTH_SHORT).show();
                return;
            }

            Category newCat = new Category(UUID.randomUUID().toString(), name, selectedEmoji);
            if (listener != null) listener.onCategoryAdded(newCat);
            dismiss();
        });
    }

    private void setupEmojiClicks(View root) {
        GridLayout grid = root.findViewById(R.id.grid_emojis);

        if (grid != null) {
            for (int i = 0; i < grid.getChildCount(); i++) {
                View child = grid.getChildAt(i);
                if (child instanceof TextView) {
                    child.setOnClickListener(v -> {
                        selectedEmoji = ((TextView) v).getText().toString();
                        tvPreviewIcon.setText(selectedEmoji);
                    });
                }
            }
        }
    }
}
