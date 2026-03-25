package com.dashstudio.dreamanalyzer.ui.settings;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.dashstudio.dreamanalyzer.data.LocalDataRepository;
import com.dashstudio.dreamanalyzer.databinding.FragmentSettingsBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    private final ActivityResultLauncher<Intent> pickAvatarLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            String path = persistAvatar(uri);
                            LocalDataRepository repository = new LocalDataRepository(requireContext());
                            repository.setProfileAvatarPath(path);
                            refreshProfile();
                        } catch (IOException e) {
                            Toast.makeText(requireContext(), "头像保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> notifyPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    Toast.makeText(requireContext(), "通知权限已开启", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "未授予通知权限，提醒可能无法显示", Toast.LENGTH_SHORT).show();
                }
                applyReminderSchedule();
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        setupActions();
        refreshAll();
        return binding.getRoot();
    }

    private void setupActions() {
        binding.layoutProfile.setOnClickListener(v -> showEditProfileDialog());

        binding.btnLogin.setOnClickListener(v -> showLoginDialog());
        binding.btnRegister.setOnClickListener(v -> showRegisterDialog());

        binding.btnLogout.setOnClickListener(v -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            repository.logout();
            Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show();
            refreshAll();
        });

        binding.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            repository.setDarkModeEnabled(isChecked);
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });

        binding.switchNotify.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            repository.setNotificationEnabled(isChecked);
            maybeRequestNotificationPermission();
            applyReminderSchedule();
            Toast.makeText(requireContext(), isChecked ? "提醒已开启" : "提醒已关闭", Toast.LENGTH_SHORT).show();
        });

        binding.btnNotifyStart.setOnClickListener(v -> showTimePicker(true));
        binding.btnNotifyEnd.setOnClickListener(v -> showTimePicker(false));

        binding.btnClearImageHistory.setOnClickListener(v -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            repository.clearImageHistory();
            Toast.makeText(requireContext(), "已清除图片生成历史", Toast.LENGTH_SHORT).show();
        });

        binding.btnClearPostHistory.setOnClickListener(v -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            repository.clearPosts();
            Toast.makeText(requireContext(), "已清除社区博客记录", Toast.LENGTH_SHORT).show();
        });

        binding.btnClearAll.setOnClickListener(v -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            repository.clearCacheAndRecords();
            Toast.makeText(requireContext(), "已清除全部缓存与记录", Toast.LENGTH_SHORT).show();
            refreshAll();
        });
    }

    private void showEditProfileDialog() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("输入新的用户名");
        input.setText(repository.getProfileName());

        new AlertDialog.Builder(requireContext())
                .setTitle("编辑资料")
                .setView(input)
                .setNeutralButton("更换头像", (d, w) -> openAvatarPicker())
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        repository.setProfileName(name);
                        refreshProfile();
                    }
                })
                .show();
    }

    private void openAvatarPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        pickAvatarLauncher.launch(intent);
    }

    private String persistAvatar(Uri uri) throws IOException {
        File dir = new File(requireContext().getFilesDir(), "avatars");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "avatar_" + System.currentTimeMillis() + ".jpg");

        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) {
                throw new IOException("读取头像失败");
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        }
        return out.getAbsolutePath();
    }

    private void showLoginDialog() {
        LinearLayout layout = buildCredentialLayout();
        EditText etUser = (EditText) layout.getChildAt(0);
        EditText etPass = (EditText) layout.getChildAt(1);

        new AlertDialog.Builder(requireContext())
                .setTitle("登录")
                .setView(layout)
                .setNegativeButton("取消", null)
                .setPositiveButton("登录", (d, w) -> {
                    LocalDataRepository repository = new LocalDataRepository(requireContext());
                    String message = repository.login(etUser.getText().toString().trim(), etPass.getText().toString());
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    refreshAll();
                })
                .show();
    }

    private void showRegisterDialog() {
        LinearLayout layout = buildCredentialLayout();
        EditText etUser = (EditText) layout.getChildAt(0);
        EditText etPass = (EditText) layout.getChildAt(1);

        new AlertDialog.Builder(requireContext())
                .setTitle("注册")
                .setView(layout)
                .setNegativeButton("取消", null)
                .setPositiveButton("注册", (d, w) -> {
                    LocalDataRepository repository = new LocalDataRepository(requireContext());
                    String message = repository.register(etUser.getText().toString().trim(), etPass.getText().toString());
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    refreshAll();
                })
                .show();
    }

    private LinearLayout buildCredentialLayout() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = 32;
        layout.setPadding(p, p, p, p);

        EditText etUser = new EditText(requireContext());
        etUser.setHint("用户名");
        etUser.setInputType(InputType.TYPE_CLASS_TEXT);

        EditText etPass = new EditText(requireContext());
        etPass.setHint("密码");
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(etUser);
        layout.addView(etPass);
        return layout;
    }

    private void showTimePicker(boolean isStart) {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        String source = isStart ? repository.getNotificationStart() : repository.getNotificationEnd();
        int hour = 20;
        int minute = 0;
        try {
            String[] parts = source.split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
        }

        new TimePickerDialog(requireContext(), (view, h, m) -> {
            String newTime = String.format(Locale.getDefault(), "%02d:%02d", h, m);
            String start = repository.getNotificationStart();
            String end = repository.getNotificationEnd();
            if (isStart) {
                repository.setNotificationTimeRange(newTime, end);
            } else {
                repository.setNotificationTimeRange(start, newTime);
            }
            refreshGeneralSettings();
            applyReminderSchedule();
        }, hour, minute, true).show();
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void applyReminderSchedule() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        ReminderScheduler.schedule(
                requireContext(),
                repository.getNotificationStart(),
                repository.getNotificationEnd(),
                repository.isNotificationEnabled()
        );
    }

    private void refreshAll() {
        refreshProfile();
        refreshLoginStatus();
        refreshGeneralSettings();
    }

    private void refreshProfile() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        binding.tvProfileName.setText(repository.getProfileName());

        String avatarPath = repository.getProfileAvatarPath();
        File avatarFile = new File(avatarPath == null ? "" : avatarPath);
        if (avatarFile.exists()) {
            binding.ivAvatar.setImageBitmap(BitmapFactory.decodeFile(avatarFile.getAbsolutePath()));
        } else {
            binding.ivAvatar.setImageResource(com.dashstudio.dreamanalyzer.R.drawable.ic_avatar_morandi);
        }
    }

    private void refreshLoginStatus() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        binding.tvLoginStatus.setText("当前：" + repository.getLoggedInUser());
    }

    private void refreshGeneralSettings() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        binding.switchDarkMode.setChecked(repository.isDarkModeEnabled());
        binding.switchNotify.setChecked(repository.isNotificationEnabled());

        binding.btnNotifyStart.setText("开始 " + repository.getNotificationStart());
        binding.btnNotifyEnd.setText("结束 " + repository.getNotificationEnd());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
