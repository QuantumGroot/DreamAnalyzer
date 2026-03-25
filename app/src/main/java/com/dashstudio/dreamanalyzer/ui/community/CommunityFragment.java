package com.dashstudio.dreamanalyzer.ui.community;

import android.graphics.BitmapFactory;
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
import com.dashstudio.dreamanalyzer.databinding.ItemPostBinding;

import java.io.File;
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
            binding.tvCommunityHint.setText("点击右下角 + 发布图文博客");
        }
    }

    private void refreshPosts() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        List<LocalDataRepository.PostRecord> posts = repository.getPosts();

        binding.layoutPostList.removeAllViews();

        if (posts.isEmpty()) {
            binding.tvPostEmpty.setVisibility(View.VISIBLE);
            binding.layoutPostList.addView(binding.tvPostEmpty);
            return;
        }

        binding.tvPostEmpty.setVisibility(View.GONE);

        List<LocalDataRepository.PostRecord> topPosts = new ArrayList<>();
        for (int i = 0; i < Math.min(posts.size(), 20); i++) {
            topPosts.add(posts.get(i));
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < topPosts.size(); i++) {
            LocalDataRepository.PostRecord post = topPosts.get(i);
            ItemPostBinding item = ItemPostBinding.inflate(inflater, binding.layoutPostList, false);

            item.tvPostTitle.setText(post.title);

            String imagePath = extractImagePath(post.content);
            String contentText = removeImageLine(post.content);
            item.tvPostContent.setText(contentText);
            item.tvPostTime.setText("时间：" + post.createdAt);

            if (!imagePath.isEmpty()) {
                File file = new File(imagePath);
                if (file.exists()) {
                    item.ivPostImage.setVisibility(View.VISIBLE);
                    item.ivPostImage.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
                } else {
                    item.ivPostImage.setVisibility(View.GONE);
                }
            } else {
                item.ivPostImage.setVisibility(View.GONE);
            }

            binding.layoutPostList.addView(item.getRoot());
        }
    }

    private String extractImagePath(String content) {
        if (content == null) {
            return "";
        }
        String tag = "图片：";
        int idx = content.lastIndexOf(tag);
        if (idx < 0) {
            return "";
        }
        String tail = content.substring(idx + tag.length()).trim();
        int lineBreak = tail.indexOf("\n");
        if (lineBreak >= 0) {
            return tail.substring(0, lineBreak).trim();
        }
        return tail;
    }

    private String removeImageLine(String content) {
        if (content == null) {
            return "";
        }
        String[] lines = content.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.trim().startsWith("图片：")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        return sb.toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
