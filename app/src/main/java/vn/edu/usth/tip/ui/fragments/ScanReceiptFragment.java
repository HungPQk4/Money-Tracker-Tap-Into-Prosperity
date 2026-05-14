package vn.edu.usth.tip.ui.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import vn.edu.usth.tip.R;
import vn.edu.usth.tip.network.InvoiceApi;
import vn.edu.usth.tip.network.RetrofitClient;
import vn.edu.usth.tip.network.requests.InvoiceAnalysisRequest;
import vn.edu.usth.tip.network.responses.InvoiceAnalysisResponse;
import vn.edu.usth.tip.utils.InvoiceParser;
import vn.edu.usth.tip.utils.TokenManager;

public class ScanReceiptFragment extends Fragment {

    private View layoutLoading;
    private View scanLaser;
    private PreviewView cameraPreview;

    private ImageCapture imageCapture;
    private Camera camera;
    private boolean torchEnabled = false;
    private Uri currentPhotoUri;

    // ── Launcher: yêu cầu quyền Camera ──────────────────────────────────────
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(requireContext(),
                            "Cần cấp quyền Camera để quét hóa đơn", Toast.LENGTH_LONG).show();
                    Navigation.findNavController(requireView()).navigateUp();
                }
            });

    // ── Launcher: yêu cầu quyền đọc ảnh (gallery) ───────────────────────────
    private final ActivityResultLauncher<String> galleryPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) {
                    this.pickImageLauncher.launch("image/*");
                } else {
                    Toast.makeText(requireContext(),
                            "Cần cấp quyền truy cập ảnh", Toast.LENGTH_SHORT).show();
                }
            });

    // ── Launcher: chọn ảnh từ thư viện ─────────────────────────────────────
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (!isAdded()) return;
                if (uri != null) {
                    currentPhotoUri = uri;
                    // Gallery flow: hiện loading rồi mới chạy OCR
                    showLoading(true);
                    runOcrOnUri(uri);
                }
            });

    public ScanReceiptFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan_receipt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutLoading = view.findViewById(R.id.layout_loading);
        scanLaser     = view.findViewById(R.id.scan_laser);
        cameraPreview = view.findViewById(R.id.camera_preview);

        startLaserAnimation();

        // Back
        view.findViewById(R.id.btn_back)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        // Flash / Torch — Bước 1 OCR.txt
        view.findViewById(R.id.btn_flash).setOnClickListener(v -> {
            if (camera != null) {
                torchEnabled = !torchEnabled;
                camera.getCameraControl().enableTorch(torchEnabled);
            }
        });

        // Shutter — Bước 2 OCR.txt
        view.findViewById(R.id.btn_capture).setOnClickListener(v -> capturePhoto());

        // Gallery — Bước 2 OCR.txt
        view.findViewById(R.id.btn_gallery).setOnClickListener(v -> openGallery());

        // Nhập tay — bỏ qua OCR, vào ExtractInvoice với field trống
        view.findViewById(R.id.btn_manual).setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_scanReceipt_to_extractInvoice));

        // Bước 1 — kiểm tra quyền Camera trước khi khởi động preview
        if (hasCameraPermission()) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // ── CameraX: khởi tạo preview + ImageCapture — Bước 1 OCR.txt ──────────
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());

        future.addListener(() -> {
            // Guard: Fragment có thể đã bị detach trước khi callback chạy
            if (!isAdded() || getView() == null) return;

            try {
                ProcessCameraProvider provider = future.get();
                provider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                camera = provider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );
            } catch (ExecutionException | InterruptedException e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(),
                            "Không thể khởi động camera", Toast.LENGTH_SHORT).show();
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    // ── Chụp ảnh → file tạm → OCR — Bước 2 OCR.txt ─────────────────────────
    private void capturePhoto() {
        if (imageCapture == null) return;

        File outputFile;
        try {
            outputFile = File.createTempFile("receipt_", ".jpg", requireContext().getCacheDir());
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Lỗi tạo file tạm", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hiện loading ngay khi nhấn shutter — Bước 2 OCR.txt
        showLoading(true);

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        imageCapture.takePicture(options,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        currentPhotoUri = Uri.fromFile(outputFile);
                        // Loading đã hiện từ capturePhoto(); gọi OCR trực tiếp
                        runOcrOnUri(currentPhotoUri);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        showLoading(false);
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "Chụp ảnh thất bại: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // ── Mở thư viện ảnh — Bước 2 OCR.txt ───────────────────────────────────
    private void openGallery() {
        String permission = android.os.Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED) {
            pickImageLauncher.launch("image/*");
        } else {
            galleryPermissionLauncher.launch(permission);
        }
    }

    // ── ML Kit OCR — Bước 2: Extract raw text ─────────────────────────────
    // Caller chịu trách nhiệm gọi showLoading(true) trước khi gọi hàm này.
    private void runOcrOnUri(Uri uri) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(requireContext(), uri);
        } catch (IOException e) {
            showLoading(false);
            if (isAdded()) {
                Toast.makeText(requireContext(), "Không đọc được ảnh",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String rawOcr = visionText.getText();
                    android.util.Log.d("OCR_DEBUG", "══════ RAW OCR TEXT ══════\n" + rawOcr);

                    if (rawOcr == null || rawOcr.trim().isEmpty()) {
                        showLoading(false);
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "Không tìm thấy văn bản trên hóa đơn",
                                    Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    // Bước 3: Gửi rawText lên Spring Boot → Gemini LLM
                    analyzeWithLLM(rawOcr);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "OCR thất bại: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Bước 3: Gọi API phân tích bằng LLM ─────────────────────────────────
    private void analyzeWithLLM(String rawOcrText) {
        // Loading vẫn đang hiển thị từ runOcrOnUri
        TokenManager tokenManager = new TokenManager(requireContext());
        InvoiceApi invoiceApi = RetrofitClient.createService(InvoiceApi.class, tokenManager);

        InvoiceAnalysisRequest request = new InvoiceAnalysisRequest(rawOcrText);
        invoiceApi.analyzeInvoice(request).enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(retrofit2.Call<InvoiceAnalysisResponse> call,
                                   retrofit2.Response<InvoiceAnalysisResponse> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    InvoiceAnalysisResponse result = response.body();
                    android.util.Log.d("OCR_DEBUG",
                            "══════ LLM RESULT: amount=" + result.getAmount()
                                    + " shop=" + result.getShopName()
                                    + " date=" + result.getDate()
                                    + " note=" + result.getNote());
                    navigateToExtract(
                            result.getAmount(),
                            result.getShopName(),
                            result.getDate(),
                            result.getNote()
                    );
                } else {
                    // Fallback: dùng InvoiceParser local nếu API thất bại
                    android.util.Log.w("OCR_DEBUG", "LLM API failed, falling back to local parser");
                    fallbackLocalParser(rawOcrText);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<InvoiceAnalysisResponse> call, Throwable t) {
                if (!isAdded()) return;
                // Fallback: dùng InvoiceParser local nếu mất mạng
                android.util.Log.w("OCR_DEBUG", "LLM API error: " + t.getMessage()
                        + ", falling back to local parser");
                fallbackLocalParser(rawOcrText);
            }
        });
    }

    // ── Fallback: InvoiceParser local (khi không có mạng / API lỗi) ─────────
    private void fallbackLocalParser(String rawOcrText) {
        InvoiceParser.ParseResult result = InvoiceParser.parse(rawOcrText);
        android.util.Log.d("OCR_DEBUG",
                "══════ LOCAL FALLBACK: amount=" + result.amount + " note=" + result.note);
        navigateToExtract(result.amount, "", "", result.note);
    }

    // ── Navigate → ExtractInvoiceFragment — Bước 4 ──────────────────────────
    private void navigateToExtract(long amount, String shopName, String date, String note) {
        showLoading(false);
        if (!isAdded() || getView() == null) return;
        Bundle args = new Bundle();
        args.putLong("ocr_amount", amount);
        args.putString("ocr_shop_name", shopName != null ? shopName : "");
        args.putString("ocr_date", date != null ? date : "");
        args.putString("ocr_note", note != null ? note : "");
        args.putString("ocr_photo_uri", currentPhotoUri != null ? currentPhotoUri.toString() : null);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_scanReceipt_to_extractInvoice, args);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showLoading(boolean show) {
        if (layoutLoading != null) {
            layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void startLaserAnimation() {
        if (scanLaser == null) return;
        // RELATIVE_TO_PARENT ở đây là CardView (camera_container),
        // nên laser chỉ chạy trong vùng preview — Bước 1 OCR.txt
        TranslateAnimation anim = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, -0.05f,
                Animation.RELATIVE_TO_PARENT, 0.85f
        );
        anim.setDuration(1500);
        anim.setRepeatCount(Animation.INFINITE);
        anim.setRepeatMode(Animation.REVERSE);
        scanLaser.startAnimation(anim);
    }
}
