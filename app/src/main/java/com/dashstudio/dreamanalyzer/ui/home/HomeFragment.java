package com.dashstudio.dreamanalyzer.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.dashstudio.dreamanalyzer.data.LocalDataRepository;
import com.dashstudio.dreamanalyzer.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        binding.tvLastEmotion.setText(repository.getLastEmotion());
        binding.tvTrendAndSuggestion.setText(repository.getLastSuggestion());
        binding.tvLastImageHint.setText("风格：" + repository.getLastImageStyle() + "（示例占位，可接入真实图片文件）");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
