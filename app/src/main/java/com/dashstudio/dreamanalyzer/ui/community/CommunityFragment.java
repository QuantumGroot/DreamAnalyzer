package com.dashstudio.dreamanalyzer.ui.community;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.dashstudio.dreamanalyzer.R;
import com.dashstudio.dreamanalyzer.data.LocalDataRepository;
import com.dashstudio.dreamanalyzer.databinding.FragmentCommunityBinding;

import java.util.ArrayList;
import java.util.List;

public class CommunityFragment extends Fragment {

    private FragmentCommunityBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCommunityBinding.inflate(inflater, container, false);
        setupActions();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHint();
        refreshPosts();
    }

    private void setupActions() {
        binding.fabAddPost.setOnClickListener(v -> openPublishPage());
    }

    private void openPublishPage() {
        NavController navController = Navigation.findNavController(binding.getRoot());
        navController.navigate(R.id.navigation_publish_blog);
    }

    private void refreshHint() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        LocalDataRepository.PostDraft draft = repository.getPendingPostDraft();
        if (draft != null && !draft.isEmpty()) {
            binding.tvCommunityHint.setText("检测到未发布草稿，点击右下角 + 可继续编辑");
        } else {
            binding.tvCommunityHint.setText("点击右下角 + 发布图文博客（本地存储）");
        }
    }

    private void refreshPosts() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        List<LocalDataRepository.PostRecord> posts = repository.getPosts();
        if (posts.isEmpty()) {
            binding.tvPostHistory.setText("暂无博客");
            return;
        }

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(posts.size(), 20); i++) {
            LocalDataRepository.PostRecord post = posts.get(i);
            lines.add((i + 1) + ". " + post.title + "\n"
                    + post.content + "\n"
                    + "时间：" + post.createdAt);
        }
        binding.tvPostHistory.setText(joinLines(lines));
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i));
            if (i < lines.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
