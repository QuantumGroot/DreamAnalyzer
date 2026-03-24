package com.dashstudio.dreamanalyzer.ui.browse;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.dashstudio.dreamanalyzer.data.EegModelLocalEngine;
import com.dashstudio.dreamanalyzer.data.LocalDataRepository;
import com.dashstudio.dreamanalyzer.databinding.FragmentBrowseBinding;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BrowseFragment extends Fragment {

    private FragmentBrowseBinding binding;
    private String selectedEdfName = "未选择文件";

    private final ActivityResultLauncher<Intent> pickEdfLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            EegModelLocalEngine engine = new EegModelLocalEngine(requireContext());
                            selectedEdfName = engine.importEdf(uri);
                            binding.tvSelectedEdf.setText("已导入：" + selectedEdfName + "（已保存到本地 data_EDF）");
                            binding.tvPrecheckHint.setText("文件已导入，可直接点击运行。轻量模式会自动生成CSV。");
                        } catch (IOException e) {
                            Toast.makeText(requireContext(), "导入EDF失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBrowseBinding.inflate(inflater, container, false);

        EegModelLocalEngine engine = new EegModelLocalEngine(requireContext());
        boolean seeded = engine.ensureSeedDataFromAssetsIfNeeded();
        binding.tvPrecheckHint.setText(seeded
                ? "模型素材已初始化到 app 私有目录"
                : "未检测到 assets/EEG_Model，若要自动初始化请迁移目录到 assets");

        setupDaySpinner();
        setupActions();
        restoreLastGeneration();
        refreshHistory();

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            restoreLastGeneration();
            refreshHistory();
        }
    }

    private void setupDaySpinner() {
        List<String> dayOptions = Arrays.asList("今天", "昨天", "前天", "本周任意一天");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                dayOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerDay.setAdapter(adapter);
    }

    private void setupActions() {
        binding.btnPickEdf.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            pickEdfLauncher.launch(intent);
        });

        binding.btnAnalyzeGenerate.setOnClickListener(v -> {
            String selectedDay = String.valueOf(binding.spinnerDay.getSelectedItem());
            if ("未选择文件".equals(selectedEdfName)) {
                Toast.makeText(requireContext(), "请先上传EDF文件", Toast.LENGTH_SHORT).show();
                return;
            }

            EegModelLocalEngine engine = new EegModelLocalEngine(requireContext());

            String strategyMode = getSelectedStrategyMode();
            PythonReport report = runPythonModels(selectedEdfName, strategyMode);
            if (report != null) {
                binding.tvPrecheckHint.setText(report.summaryText);
                if (!report.ok) {
                    return;
                }
            }

            EegModelLocalEngine.PrecheckResult postCheck = engine.precheck(selectedEdfName);
            if (!postCheck.ok) {
                binding.tvPrecheckHint.setText((report == null ? "" : report.summaryText + "\n") + "运行后检查失败：\n" + postCheck.message);
                return;
            }

            try {
                EegModelLocalEngine.AnalysisResult result = engine.analyzeFromCsvByEdfName(selectedEdfName);
                binding.tvGenerationResult.setText(result.summary + "\n随机图片：" + result.imagePath + "\n饼图：" + result.pieChartPath);
                renderResultImages(result.imagePath, result.pieChartPath);

                if (result.ok) {
                    LocalDataRepository repository = new LocalDataRepository(requireContext());
                    String detailedSuggestion = buildDetailedSuggestion(result.dominantEmotion);
                    repository.saveLatestAnalysis(
                            result.dominantEmotion,
                            detailedSuggestion,
                            result.imagePath,
                            result.pieChartPath,
                            result.summary
                    );
                    repository.addGeneratedImageRecord(selectedDay, result.dominantEmotion, "对应图库随机抽取", selectedEdfName);
                    refreshHistory();
                }
            } catch (IOException e) {
                Toast.makeText(requireContext(), "读取CSV失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private PythonReport runPythonModels(String edfName, String mode) {
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(requireContext()));
            }
            Python py = Python.getInstance();
            PyObject bridge = py.getModule("android_eeg_bridge");
            String basePath = new File(requireContext().getFilesDir(), "EEG_Model").getAbsolutePath();

            PyObject result;
            try {
                result = bridge.callAttr("run_pipeline_mode", basePath, edfName, mode);
            } catch (Exception ignore) {
                // 兼容旧版桥接函数
                result = bridge.callAttr("run_pipeline", basePath, edfName);
            }
            return parsePythonReport(result, mode);
        } catch (Exception e) {
            return new PythonReport("Python运行失败：" + e.getMessage(), false);
        }
    }

    private PythonReport parsePythonReport(PyObject result, String mode) {
        if (result == null) {
            return new PythonReport("Python返回空结果", false);
        }

        // 新结构（run_pipeline_mode）
        String strategy = getPyValue(result, "strategy", "");
        String depText = getPyValue(result, "dependency_text", "");
        String m1Msg = getPyValue(result, "model1_message", "");
        String m2Msg = getPyValue(result, "model2_message", "");
        String okValue = getPyValue(result, "ok", "false");
        boolean ok = "True".equalsIgnoreCase(okValue) || "true".equalsIgnoreCase(okValue);

        if (!strategy.isEmpty() || !depText.isEmpty() || !m1Msg.isEmpty() || !m2Msg.isEmpty()) {
            if (strategy.isEmpty()) strategy = mode;
            if (depText.isEmpty()) depText = "旧版桥接，无依赖详情";
            if (m1Msg.isEmpty()) m1Msg = "Model_1信息缺失";
            if (m2Msg.isEmpty()) m2Msg = "Model_2信息缺失";

            String summary = "Python策略：" + strategy + "\n" +
                    "依赖：" + depText + "\n" +
                    m1Msg + "\n" + m2Msg;
            return new PythonReport(summary, ok);
        }

        // 旧结构（run_pipeline）兼容
        String oldOk = getPyValue(result, "ok", "true");
        boolean oldSuccess = "True".equalsIgnoreCase(oldOk) || "true".equalsIgnoreCase(oldOk);
        String oldSummary = "Python策略：" + mode + "（兼容旧版桥接）\n" +
                "依赖：旧版桥接，无依赖详情\n" +
                "Model_1/Model_2 已执行（旧版返回）";
        return new PythonReport(oldSummary, oldSuccess);
    }

    private String getPyValue(PyObject dict, String key, String fallback) {
        try {
            PyObject value = dict.get(key);
            return value == null ? fallback : value.toString();
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private String getSelectedStrategyMode() {
        int checked = binding.rgStrategy.getCheckedRadioButtonId();
        if (checked == binding.rbStrategyReal.getId()) {
            return "real";
        } else if (checked == binding.rbStrategyLight.getId()) {
            return "light";
        }
        return "auto";
    }

    private void updatePrecheckHint() {
        if ("未选择文件".equals(selectedEdfName)) {
            return;
        }
        EegModelLocalEngine engine = new EegModelLocalEngine(requireContext());
        EegModelLocalEngine.PrecheckResult precheck = engine.precheck(selectedEdfName);
        binding.tvPrecheckHint.setText(precheck.ok ? ("预检查通过：" + precheck.message) : ("预检查失败：\n" + precheck.message));
    }

    private void renderResultImages(String imagePath, String piePath) {
        if (imagePath != null && !imagePath.isEmpty() && new File(imagePath).exists()) {
            binding.ivResultImage.setImageBitmap(BitmapFactory.decodeFile(imagePath));
        }
        if (piePath != null && !piePath.isEmpty() && new File(piePath).exists()) {
            binding.ivPieChart.setImageBitmap(BitmapFactory.decodeFile(piePath));
        }
    }

    private void restoreLastGeneration() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        String lastSummary = repository.getLastGenerationSummary();
        String lastImagePath = repository.getLastImageStyle();
        String lastPiePath = repository.getLastPiePath();

        if (lastSummary != null && !lastSummary.trim().isEmpty()) {
            binding.tvGenerationResult.setText(lastSummary + "\n随机图片：" + lastImagePath + "\n饼图：" + lastPiePath);
        }
        renderResultImages(lastImagePath, lastPiePath);
    }

    private String buildDetailedSuggestion(String dominantEmotion) {
        String emotion = dominantEmotion == null ? "neutral" : dominantEmotion.toLowerCase();

        String advice;
        if (emotion.contains("joy") || emotion.contains("happy")) {
            advice = "建议：保持当前作息节律，可在睡前加入10分钟轻拉伸，巩固积极状态。";
        } else if (emotion.contains("sadness") || emotion.contains("sad")) {
            advice = "建议：今晚尽量提前入睡，减少睡前信息刺激，早晨补充自然光照。";
        } else if (emotion.contains("anger") || emotion.contains("angry")) {
            advice = "建议：睡前进行5-8分钟腹式呼吸，避免激烈讨论与高唤醒内容。";
        } else if (emotion.contains("fear")) {
            advice = "建议：可使用白噪声或放松音乐，降低入睡前警觉性。";
        } else if (emotion.contains("disgust")) {
            advice = "建议：关注卧室整洁和气味舒适度，减少环境不适带来的负面感受。";
        } else if (emotion.contains("surprise")) {
            advice = "建议：保持规律作息，避免临睡前临时高强度活动造成节律波动。";
        } else {
            advice = "建议：继续保持规律作息，睡前减少蓝光暴露并适度放松。";
        }

        String trend = "趋势：结合近几次结果，建议关注“主要情绪是否连续3次偏负向”，若连续出现可适当调整作息与白天活动节律。";
        return advice + "\n" + trend;
    }

    private void refreshHistory() {
        LocalDataRepository repository = new LocalDataRepository(requireContext());
        List<LocalDataRepository.ImageRecord> records = repository.getGeneratedImageRecords();
        if (records.isEmpty()) {
            binding.tvImageHistory.setText("暂无记录");
            return;
        }

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(records.size(), 10); i++) {
            LocalDataRepository.ImageRecord record = records.get(i);
            lines.add((i + 1) + ". [" + record.createdAt + "] "
                    + record.day + " | " + record.emotion + " | " + record.style + " | " + record.sourceEdfName);
        }
        binding.tvImageHistory.setText(joinLines(lines));
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i));
            if (i < lines.size() - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class PythonReport {
        final String summaryText;
        final boolean ok;

        PythonReport(String summaryText, boolean ok) {
            this.summaryText = summaryText;
            this.ok = ok;
        }
    }
}
