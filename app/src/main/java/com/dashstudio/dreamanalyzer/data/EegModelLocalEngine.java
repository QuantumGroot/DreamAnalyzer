package com.dashstudio.dreamanalyzer.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class EegModelLocalEngine {

    private static final String ASSET_EEG_ROOT = "EEG_Model";

    private final Context context;
    private final File baseDir;
    private final File dataEdfDir;
    private final File csvOutputDir;
    private final File pictureDir;
    private final File finalOutputDir;

    public EegModelLocalEngine(Context context) {
        this.context = context.getApplicationContext();
        this.baseDir = new File(this.context.getFilesDir(), "EEG_Model");
        this.dataEdfDir = new File(baseDir, "data_EDF");
        this.csvOutputDir = new File(baseDir, "csv_output");
        this.pictureDir = new File(baseDir, "picture");
        this.finalOutputDir = new File(baseDir, "final_output");
        ensureDirs();
    }

    private void ensureDirs() {
        //noinspection ResultOfMethodCallIgnored
        baseDir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        dataEdfDir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        csvOutputDir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        pictureDir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        finalOutputDir.mkdirs();
    }

    public boolean ensureSeedDataFromAssetsIfNeeded() {
        try {
            AssetManager am = context.getAssets();
            String[] top = am.list(ASSET_EEG_ROOT);
            if (top == null || top.length == 0) {
                return false;
            }
            // 每次启动都尝试补齐，不依赖 .seed_ready，避免你更新 assets 后旧数据不刷新。
            copyAssetFolder(am, ASSET_EEG_ROOT, baseDir, true);
            ensurePlaceholderPicturesIfNeeded();
            //noinspection ResultOfMethodCallIgnored
            new File(baseDir, ".seed_ready").createNewFile();
            return true;
        } catch (IOException e) {
            ensurePlaceholderPicturesIfNeeded();
            return false;
        }
    }

    private void copyAssetFolder(AssetManager am, String assetPath, File targetDir, boolean flattenRoot) throws IOException {
        String[] children = am.list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(am, assetPath, targetDir);
            return;
        }

        File folder = flattenRoot ? targetDir : new File(targetDir, new File(assetPath).getName());
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();

        for (String child : children) {
            copyAssetFolder(am, assetPath + "/" + child, folder, false);
        }
    }

    private void copyAssetFile(AssetManager am, String assetPath, File targetDir) throws IOException {
        String fileName = new File(assetPath).getName();
        // 跳过占位控制文件
        if (".keep".equalsIgnoreCase(fileName)) {
            return;
        }

        File outFile = new File(targetDir, fileName);
        if (outFile.exists()) {
            return;
        }

        try (InputStream in = am.open(assetPath);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }

    private void ensurePlaceholderPicturesIfNeeded() {
        String[] emotions = new String[]{"joy", "sadness", "neutral", "anger", "disgust", "fear", "surprise"};
        for (String emotion : emotions) {
            File dir = new File(pictureDir, emotion);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();

            File[] imgs = dir.listFiles(file -> {
                String n = file.getName().toLowerCase(Locale.ROOT);
                return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
            });
            if (imgs != null && imgs.length > 0) {
                continue;
            }

            File ph = new File(dir, "placeholder_" + emotion + ".png");
            if (!ph.exists()) {
                createSolidPlaceholder(ph);
            }
        }
    }

    private void createSolidPlaceholder(File file) {
        Bitmap bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        int color = Color.parseColor("#B0C1B5");
        bmp.eraseColor(color);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bmp.compress(CompressFormat.PNG, 100, out);
        } catch (IOException ignored) {
        } finally {
            bmp.recycle();
        }
    }

    private void ensurePieChartImage(Map<String, Integer> counts, File outFile) {
        int canvasW = 900;
        int canvasH = 720;
        Bitmap bmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);

        String[] keys = new String[]{"joy", "sadness", "neutral", "anger", "disgust", "fear", "surprise"};
        String[] labelsCn = new String[]{"喜悦", "悲伤", "中性", "愤怒", "厌恶", "恐惧", "惊讶"};
        int[] colors = new int[]{
                Color.parseColor("#FFD166"),
                Color.parseColor("#7DA0FA"),
                Color.parseColor("#A8A8A8"),
                Color.parseColor("#E07A5F"),
                Color.parseColor("#9C6644"),
                Color.parseColor("#8D99AE"),
                Color.parseColor("#7BC8A4")
        };

        Map<String, Integer> normalized = new HashMap<>();
        normalized.put("joy", counts.getOrDefault("joy", 0) + counts.getOrDefault("happy", 0));
        normalized.put("sadness", counts.getOrDefault("sadness", 0) + counts.getOrDefault("sad", 0));
        normalized.put("neutral", counts.getOrDefault("neutral", 0));
        normalized.put("anger", counts.getOrDefault("anger", 0) + counts.getOrDefault("angry", 0));
        normalized.put("disgust", counts.getOrDefault("disgust", 0));
        normalized.put("fear", counts.getOrDefault("fear", 0));
        normalized.put("surprise", counts.getOrDefault("surprise", 0));

        int total = 0;
        for (String k : keys) {
            total += normalized.getOrDefault(k, 0);
        }
        if (total <= 0) {
            total = 1;
        }

        Paint piePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF oval = new RectF(60, 80, 460, 480);

        float startAngle = -90f;
        for (int i = 0; i < keys.length; i++) {
            int v = normalized.getOrDefault(keys[i], 0);
            if (v <= 0) {
                continue;
            }
            float sweep = (v * 360f) / total;
            piePaint.setColor(colors[i]);
            canvas.drawArc(oval, startAngle, sweep, true, piePaint);
            startAngle += sweep;
        }

        // 环形中心
        piePaint.setColor(Color.WHITE);
        canvas.drawCircle(260, 280, 95, piePaint);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.parseColor("#4F4A45"));
        titlePaint.setTextSize(38f);
        titlePaint.setFakeBoldText(true);
        canvas.drawText("情绪分布", 560, 90, titlePaint);

        Paint legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendTextPaint.setColor(Color.parseColor("#4F4A45"));
        legendTextPaint.setTextSize(28f);

        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        int lineY = 160;
        for (int i = 0; i < keys.length; i++) {
            int v = normalized.getOrDefault(keys[i], 0);
            float pct = (v * 100f) / total;

            dotPaint.setColor(colors[i]);
            canvas.drawRoundRect(new RectF(560, lineY - 20, 600, lineY + 20), 8, 8, dotPaint);

            String text = labelsCn[i] + "  " + v + "  (" + String.format(Locale.getDefault(), "%.1f", pct) + "%)";
            canvas.drawText(text, 620, lineY + 10, legendTextPaint);
            lineY += 70;
        }

        try (FileOutputStream out = new FileOutputStream(outFile)) {
            bmp.compress(CompressFormat.PNG, 100, out);
        } catch (IOException ignored) {
        } finally {
            bmp.recycle();
        }
    }

    public String importEdf(Uri uri) throws IOException {
        DocumentFile document = DocumentFile.fromSingleUri(context, uri);
        String name = (document != null && document.getName() != null)
                ? document.getName()
                : ("upload_" + System.currentTimeMillis() + ".edf");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".edf")) {
            name = name + ".edf";
        }

        File outFile = new File(dataEdfDir, name);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) {
                throw new IOException("无法读取EDF文件");
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        return name;
    }

    public PrecheckResult precheck(String edfName) {
        String csvName = edfName.replaceAll("(?i)\\.edf$", "") + "_emotions.csv";
        File csvFile = new File(csvOutputDir, csvName);
        if (!csvFile.exists()) {
            return PrecheckResult.error("缺少CSV：" + csvName + "\n请先运行 Model_1.py 生成 csv_output。");
        }

        Map<String, Integer> counts;
        try {
            counts = countPredictions(csvFile);
        } catch (IOException e) {
            return PrecheckResult.error("CSV读取失败：" + e.getMessage());
        }
        if (counts.isEmpty()) {
            return PrecheckResult.error("CSV中没有 prediction 有效数据");
        }

        String dominant4 = getDominantEmotion(counts);
        String dominant7 = mapToPictureEmotion(dominant4);
        File emotionFolder = new File(pictureDir, dominant7);
        if (!emotionFolder.exists() || !emotionFolder.isDirectory()) {
            return PrecheckResult.error("缺少图片目录：" + emotionFolder.getAbsolutePath());
        }

        File[] imgs = emotionFolder.listFiles(file -> {
            String n = file.getName().toLowerCase(Locale.ROOT);
            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
        });
        if (imgs == null || imgs.length == 0) {
            return PrecheckResult.error("图片目录为空：" + emotionFolder.getAbsolutePath());
        }

        return PrecheckResult.success("检查通过，可执行随机抽图。");
    }

    public AnalysisResult analyzeFromCsvByEdfName(String edfName) throws IOException {
        String csvName = edfName.replaceAll("(?i)\\.edf$", "") + "_emotions.csv";
        File csvFile = new File(csvOutputDir, csvName);
        if (!csvFile.exists()) {
            return AnalysisResult.error("未找到对应CSV结果：" + csvName + "。\n请先运行 Model_1.py 生成 csv_output 后再在APP中查看结果。");
        }

        Map<String, Integer> counts = countPredictions(csvFile);
        if (counts.isEmpty()) {
            return AnalysisResult.error("CSV中没有可用预测数据");
        }

        String dominant4 = getDominantEmotion(counts);
        String dominant7 = mapToPictureEmotion(dominant4);

        File selectedImage = pickRandomImage(dominant7);
        File pieFile = new File(finalOutputDir, "emotion_distribution.png");
        ensurePieChartImage(counts, pieFile);
        String piePath = pieFile.getAbsolutePath();

        int joy = counts.getOrDefault("joy", 0) + counts.getOrDefault("happy", 0);
        int sadness = counts.getOrDefault("sadness", 0) + counts.getOrDefault("sad", 0);
        int neutral = counts.getOrDefault("neutral", 0);
        int anger = counts.getOrDefault("anger", 0) + counts.getOrDefault("angry", 0);
        int disgust = counts.getOrDefault("disgust", 0);
        int fear = counts.getOrDefault("fear", 0);
        int surprise = counts.getOrDefault("surprise", 0);

        String summary = "主要情绪：" + dominant7 + "\n"
                + "分布（7分类）：joy=" + joy
                + ", sadness=" + sadness
                + ", neutral=" + neutral
                + ", anger=" + anger
                + ", disgust=" + disgust
                + ", fear=" + fear
                + ", surprise=" + surprise;

        String chosenImagePath = selectedImage == null ? "" : selectedImage.getAbsolutePath();
        return AnalysisResult.success(dominant7, chosenImagePath, piePath, summary);
    }

    private Map<String, Integer> countPredictions(File csvFile) throws IOException {
        Map<String, Integer> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return map;
            }
            String[] cols = header.split(",");
            int predIndex = -1;
            for (int i = 0; i < cols.length; i++) {
                if ("prediction".equals(cols[i].trim())) {
                    predIndex = i;
                    break;
                }
            }
            if (predIndex < 0) {
                return map;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] arr = line.split(",");
                if (predIndex < arr.length) {
                    String label = arr[predIndex].trim().toLowerCase(Locale.ROOT);
                    map.put(label, map.getOrDefault(label, 0) + 1);
                }
            }
        }
        return map;
    }

    private String getDominantEmotion(Map<String, Integer> counts) {
        String dominant = "neutral";
        int max = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                dominant = e.getKey();
            }
        }
        return dominant;
    }

    private String mapToPictureEmotion(String emotion) {
        String e = emotion == null ? "neutral" : emotion.toLowerCase(Locale.ROOT);

        switch (e) {
            case "happy":
            case "joy":
                return "joy";
            case "sad":
            case "sadness":
                return "sadness";
            case "angry":
            case "anger":
                return "anger";
            case "disgust":
                return "disgust";
            case "fear":
                return "fear";
            case "surprise":
                return "surprise";
            case "neutral":
            default:
                return "neutral";
        }
    }

    private File pickRandomImage(String emotionFolder) {
        File primaryDir = new File(pictureDir, emotionFolder);
        File legacyDir = new File(new File(baseDir, "EEG_Model"), "picture/" + emotionFolder);

        File[] primaryFiles = listImageFiles(primaryDir);
        File[] legacyFiles = listImageFiles(legacyDir);

        File[] primaryReal = filterOutPlaceholders(primaryFiles);
        File[] legacyReal = filterOutPlaceholders(legacyFiles);

        if (primaryReal.length > 0) {
            return primaryReal[new Random().nextInt(primaryReal.length)];
        }
        if (legacyReal.length > 0) {
            return legacyReal[new Random().nextInt(legacyReal.length)];
        }

        // files 目录没有真实图时，直接从 assets 实时挑图并落地到 files。
        File assetPicked = pickImageFromAssets(emotionFolder);
        if (assetPicked != null) {
            return assetPicked;
        }

        // 若无真实图，再退回占位图
        if (primaryFiles.length > 0) {
            return primaryFiles[new Random().nextInt(primaryFiles.length)];
        }
        if (legacyFiles.length > 0) {
            return legacyFiles[new Random().nextInt(legacyFiles.length)];
        }
        return null;
    }

    private File[] listImageFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return new File[0];
        }
        File[] files = dir.listFiles(file -> {
            String n = file.getName().toLowerCase(Locale.ROOT);
            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
        });
        return files == null ? new File[0] : files;
    }

    private File pickImageFromAssets(String emotionFolder) {
        try {
            AssetManager am = context.getAssets();
            String assetDir = ASSET_EEG_ROOT + "/picture/" + emotionFolder;
            String[] names = am.list(assetDir);
            if (names == null || names.length == 0) {
                return null;
            }

            // 只挑真实图片，跳过 .keep 和 placeholder
            java.util.ArrayList<String> candidates = new java.util.ArrayList<>();
            for (String name : names) {
                String n = name.toLowerCase(Locale.ROOT);
                if ((n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                        && !n.startsWith("placeholder_")) {
                    candidates.add(name);
                }
            }
            if (candidates.isEmpty()) {
                return null;
            }

            String picked = candidates.get(new Random().nextInt(candidates.size()));
            File outDir = new File(pictureDir, emotionFolder);
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
            File out = new File(outDir, picked);
            if (!out.exists()) {
                try (InputStream in = am.open(assetDir + "/" + picked);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
            return out;
        } catch (Exception ignore) {
            return null;
        }
    }

    private File[] filterOutPlaceholders(File[] files) {
        if (files == null || files.length == 0) {
            return new File[0];
        }
        int count = 0;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!n.startsWith("placeholder_")) {
                count++;
            }
        }
        if (count == 0) {
            return new File[0];
        }
        File[] out = new File[count];
        int idx = 0;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!n.startsWith("placeholder_")) {
                out[idx++] = f;
            }
        }
        return out;
    }

    public static class PrecheckResult {
        public final boolean ok;
        public final String message;

        private PrecheckResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public static PrecheckResult success(String message) {
            return new PrecheckResult(true, message);
        }

        public static PrecheckResult error(String message) {
            return new PrecheckResult(false, message);
        }
    }

    public static class AnalysisResult {
        public final boolean ok;
        public final String dominantEmotion;
        public final String imagePath;
        public final String pieChartPath;
        public final String summary;

        private AnalysisResult(boolean ok, String dominantEmotion, String imagePath, String pieChartPath, String summary) {
            this.ok = ok;
            this.dominantEmotion = dominantEmotion;
            this.imagePath = imagePath;
            this.pieChartPath = pieChartPath;
            this.summary = summary;
        }

        public static AnalysisResult success(String dominantEmotion, String imagePath, String pieChartPath, String summary) {
            return new AnalysisResult(true, dominantEmotion, imagePath, pieChartPath, summary);
        }

        public static AnalysisResult error(String summary) {
            return new AnalysisResult(false, "unknown", "", "", summary);
        }
    }
}
