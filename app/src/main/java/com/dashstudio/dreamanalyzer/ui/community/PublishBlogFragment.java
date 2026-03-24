package com.dashstudio.dreamanalyzer.ui.community;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.dashstudio.dreamanalyzer.R;
import com.dashstudio.dreamanalyzer.data.LocalDataRepository;
import com.dashstudio.dreamanalyzer.databinding.FragmentPublishBlogBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PublishBlogFragment extends Fragment {

    private FragmentPublishBlogBinding binding;
    private String selectedImagePath = "";

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            selectedImagePath = persistSelectedImage(uri);
                            binding.ivBlogImage.setImageBitmap(BitmapFactory.decodeFile(selectedImagePath));
                        } catch (IOException e) {
                            Toast.makeText(requireContext(), "导入图片失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPublishBlogBinding.inflate(inflater, container, false);
        setupActions();
        restoreDraft();
        return binding.getRoot();
    }

    private void setupActions() {
        binding.layoutPickImage.setOnClickListener(v -> openImagePicker());
        binding.tvPublish.setOnClickListener(v -> publishNow());
        binding.tvBack.setOnClickListener(v -> onTryLeave());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        onTryLeave();
                    }
                });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }

    private String persistSelectedImage(Uri uri) throws IOException {
        File dir = new File(requireContext().getFilesDir(), "blog_images");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");

        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) {
                throw new IOException("读取图片失败");
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        }
        return out.getAbsolutePath();
    }

    private void restoreDraft() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        LocalDataRepository.PostDraft draft = repository.getPendingPostDraft();
        if (draft != null) {
            if (draft.title != null) {
                binding.etBlogTitle.setText(draft.title);
            }
            if (draft.content != null) {
                binding.etBlogContent.setText(draft.content);
            }
            if (draft.imagePath != null && !draft.imagePath.trim().isEmpty()) {
                File img = new File(draft.imagePath);
                if (img.exists()) {
                    selectedImagePath = img.getAbsolutePath();
                    binding.ivBlogImage.setImageBitmap(BitmapFactory.decodeFile(selectedImagePath));
                }
            }
        }
    }

    private void publishNow() {
        String title = binding.etBlogTitle.getText().toString().trim();
        String content = binding.etBlogContent.getText().toString().trim();

        if (title.isEmpty()) {
            title = "未命名博客";
        }
        if (content.isEmpty()) {
            content = "（空内容）";
        }

        if (!selectedImagePath.isEmpty()) {
            content = content + "\n\n图片：" + selectedImagePath;
        }

        LocalDataRepository repository = new LocalDataRepository(requireContext());
        repository.addPost(title, content);
        repository.clearPendingPostDraft();

        Toast.makeText(requireContext(), "发布成功", Toast.LENGTH_SHORT).show();
        navigateToCommunity();
    }

    private void onTryLeave() {
        String title = binding.etBlogTitle.getText().toString().trim();
        String content = binding.etBlogContent.getText().toString().trim();
        boolean hasInput = !title.isEmpty() || !content.isEmpty() || !selectedImagePath.isEmpty();

        if (!hasInput) {
            navigateToCommunity();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("是否保留草稿？")
                .setMessage("返回后可在下次继续编辑")
                .setNegativeButton("不保留", (d, w) -> {
                    LocalDataRepository repository = new LocalDataRepository(requireContext());
                    repository.clearPendingPostDraft();
                    navigateToCommunity();
                })
                .setPositiveButton("保留", (d, w) -> {
                    LocalDataRepository repository = new LocalDataRepository(requireContext());
                    repository.savePendingPostDraft(title, content, selectedImagePath);
                    navigateToCommunity();
                })
                .show();
    }

    private void navigateToCommunity() {
        NavController navController = Navigation.findNavController(binding.getRoot());
        navController.popBackStack(R.id.navigation_community, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
