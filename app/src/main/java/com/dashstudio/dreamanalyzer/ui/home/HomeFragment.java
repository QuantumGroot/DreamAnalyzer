package com.dashstudio.dreamanalyzer.ui.home;

import android.app.AlertDialog;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.dashstudio.dreamanalyzer.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.dashstudio.dreamanalyzer.data.LocalDataRepository;
import com.dashstudio.dreamanalyzer.databinding.FragmentHomeBinding;

import java.io.File;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        setupActions();
        return binding.getRoot();
    }

    private void setupActions() {
        binding.ivLastImage.setOnClickListener(v -> {
            String path = extractImagePath(binding.tvLastImageHint.getText().toString());
            if (path == null || path.isEmpty()) {
                return;
            }
            File file = new File(path);
            if (!file.exists()) {
                return;
            }

            ImageView preview = new ImageView(requireContext());
            preview.setImageBitmap(BitmapFactory.decodeFile(path));
            preview.setAdjustViewBounds(true);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

            new AlertDialog.Builder(requireContext())
                    .setTitle("最近生成的图片")
                    .setView(preview)
                    .setPositiveButton("关闭", null)
                    .show();
        });

        binding.btnQuickPost.setOnClickListener(v -> {
            LocalDataRepository repository = new LocalDataRepository(requireContext());
            String emotion = repository.getLastEmotion();
            String suggestion = repository.getLastSuggestion();
            String imagePath = repository.getLastImageStyle();

            String title = "此刻心情记录：" + emotion;
            String content = "我当前的情绪状态：" + emotion + "\n\n"
                    + suggestion + "\n\n"
                    + "关联图片：" + imagePath;
            repository.savePendingPostDraft(title, content);

            BottomNavigationView nav = requireActivity().findViewById(R.id.nav_view);
            nav.setSelectedItemId(R.id.navigation_community);

            // 进入社区后再打开独立发布页
            nav.post(() -> {
                androidx.navigation.NavController navController = androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_activity_main);
                navController.navigate(R.id.navigation_publish_blog);
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        String emotionCn = toChineseEmotion(repository.getLastEmotion());
        binding.tvLastEmotion.setText(emotionCn);

        String suggestion = repository.getLastSuggestion();
        String detailed = "昨晚主要情绪：" + emotionCn + "\n"
                + suggestion + "\n"
                + "今日建议执行清单：\n"
                + "1) 睡前30分钟停止刷屏；\n"
                + "2) 保持卧室安静与微暗；\n"
                + "3) 若连续两天偏负向，晚间增加10分钟放松训练。";
        binding.tvTrendAndSuggestion.setText(detailed);

        String lastImagePath = repository.getLastImageStyle();
        File imageFile = new File(lastImagePath == null ? "" : lastImagePath);
        if (imageFile.exists()) {
            binding.ivLastImage.setImageBitmap(BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
            binding.tvLastImageHint.setText("图片路径：" + imageFile.getAbsolutePath() + "\n点击图片可放大");
        } else {
            binding.ivLastImage.setImageResource(android.R.drawable.ic_menu_gallery);
            binding.tvLastImageHint.setText("暂无可预览图片，完成一次推理后可在此显示。\n点击图片可放大");
        }
    }

    private String extractImagePath(String hintText) {
        String prefix = "图片路径：";
        if (hintText != null && hintText.startsWith(prefix)) {
            int end = hintText.indexOf("\n");
            if (end > prefix.length()) {
                return hintText.substring(prefix.length(), end).trim();
            }
            return hintText.substring(prefix.length()).trim();
        }
        return "";
    }

    private String toChineseEmotion(String raw) {
        if (raw == null) {
            return "中性";
        }
        String v = raw.toLowerCase();
        if (v.contains("joy") || v.contains("happy")) return "喜悦";
        if (v.contains("sadness") || v.contains("sad")) return "悲伤";
        if (v.contains("anger") || v.contains("angry")) return "愤怒";
        if (v.contains("disgust")) return "厌恶";
        if (v.contains("fear")) return "恐惧";
        if (v.contains("surprise")) return "惊讶";
        if (v.contains("neutral")) return "中性";
        return raw;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
