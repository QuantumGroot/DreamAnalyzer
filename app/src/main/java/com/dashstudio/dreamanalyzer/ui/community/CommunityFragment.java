package com.dashstudio.dreamanalyzer.ui.community;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

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
        refreshPosts();
    }

    private void setupActions() {
        binding.fabAddPost.setOnClickListener(v -> showCreatePostDialog());
    }

    private void showCreatePostDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = 32;
        layout.setPadding(padding, padding, padding, padding);

        EditText titleInput = new EditText(requireContext());
        titleInput.setHint("标题");
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT);

        EditText contentInput = new EditText(requireContext());
        contentInput.setHint("内容（可附图路径描述）");
        contentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        contentInput.setMinLines(4);

        layout.addView(titleInput);
        layout.addView(contentInput);

        new AlertDialog.Builder(requireContext())
                .setTitle("发布新博客")
                .setView(layout)
                .setNegativeButton("取消", null)
                .setPositiveButton("发布", (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String content = contentInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        title = "未命名博客";
                    }
                    if (content.isEmpty()) {
                        content = "（空内容）";
                    }
                    LocalDataRepository repository = new LocalDataRepository(requireContext());
                    repository.addPost(title, content);
                    refreshPosts();
                })
                .show();
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
