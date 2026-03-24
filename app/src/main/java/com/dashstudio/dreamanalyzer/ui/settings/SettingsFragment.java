package com.dashstudio.dreamanalyzer.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.dashstudio.dreamanalyzer.data.LocalDataRepository;
import com.dashstudio.dreamanalyzer.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        setupActions();
        refreshLoginStatus();
        return binding.getRoot();
    }

    private void setupActions() {
        binding.btnRegister.setOnClickListener(v -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            String message = repository.register(
                    binding.etUsername.getText().toString().trim(),
                    binding.etPassword.getText().toString()
            );
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        binding.btnLogin.setOnClickListener(v -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            String message = repository.login(
                    binding.etUsername.getText().toString().trim(),
                    binding.etPassword.getText().toString()
            );
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            refreshLoginStatus();
        });

        binding.btnLogout.setOnClickListener(v -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            repository.logout();
            Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show();
            refreshLoginStatus();
        });

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
        });
    }

    private void refreshLoginStatus() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        binding.tvLoginStatus.setText("当前：" + repository.getLoggedInUser());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
