package com.dashstudio.dreamanalyzer.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocalDataRepository {

    private static final String PREFS_NAME = "dream_analyzer_prefs";
    private static final String KEY_LAST_EMOTION = "last_emotion";
    private static final String KEY_LAST_SUGGESTION = "last_suggestion";
    private static final String KEY_LAST_IMAGE_STYLE = "last_image_style";
    private static final String KEY_IMAGE_HISTORY = "image_history";
    private static final String KEY_POSTS = "posts";
    private static final String KEY_REGISTERED_USER = "registered_user";
    private static final String KEY_REGISTERED_PASSWORD = "registered_password";
    private static final String KEY_LOGGED_IN_USER = "logged_in_user";
    private static final String KEY_PENDING_POST_TITLE = "pending_post_title";
    private static final String KEY_PENDING_POST_CONTENT = "pending_post_content";
    private static final String KEY_PENDING_POST_IMAGE_PATH = "pending_post_image_path";

    private final SharedPreferences prefs;

    public LocalDataRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveLatestAnalysis(String emotion, String suggestion, String imageStyle) {
        prefs.edit()
                .putString(KEY_LAST_EMOTION, emotion)
                .putString(KEY_LAST_SUGGESTION, suggestion)
                .putString(KEY_LAST_IMAGE_STYLE, imageStyle)
                .apply();
    }

    public String getLastEmotion() {
        return prefs.getString(KEY_LAST_EMOTION, "平稳 / 中性");
    }

    public String getLastSuggestion() {
        return prefs.getString(KEY_LAST_SUGGESTION, "建议：保持规律作息，睡前减少蓝光暴露。\n趋势：近7天整体情绪稳定。\n");
    }

    public String getLastImageStyle() {
        return prefs.getString(KEY_LAST_IMAGE_STYLE, "对应情绪图库随机图");
    }

    public void addGeneratedImageRecord(String day, String emotion, String style, String sourceEdfName) {
        List<ImageRecord> records = getGeneratedImageRecords();
        String createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        records.add(0, new ImageRecord(day, emotion, style, sourceEdfName, createdAt));
        saveImageRecords(records);
    }

    public List<ImageRecord> getGeneratedImageRecords() {
        String raw = prefs.getString(KEY_IMAGE_HISTORY, "[]");
        List<ImageRecord> records = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                records.add(new ImageRecord(
                        obj.optString("day"),
                        obj.optString("emotion"),
                        obj.optString("style"),
                        obj.optString("sourceEdfName"),
                        obj.optString("createdAt")
                ));
            }
        } catch (JSONException ignored) {
        }
        return records;
    }

    private void saveImageRecords(List<ImageRecord> records) {
        JSONArray arr = new JSONArray();
        for (ImageRecord record : records) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("day", record.day);
                obj.put("emotion", record.emotion);
                obj.put("style", record.style);
                obj.put("sourceEdfName", record.sourceEdfName);
                obj.put("createdAt", record.createdAt);
            } catch (JSONException ignored) {
            }
            arr.put(obj);
        }
        prefs.edit().putString(KEY_IMAGE_HISTORY, arr.toString()).apply();
    }

    public void addPost(String title, String content) {
        List<PostRecord> posts = getPosts();
        String createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        posts.add(0, new PostRecord(title, content, createdAt));
        savePosts(posts);
    }

    public List<PostRecord> getPosts() {
        String raw = prefs.getString(KEY_POSTS, "[]");
        List<PostRecord> posts = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                posts.add(new PostRecord(
                        obj.optString("title"),
                        obj.optString("content"),
                        obj.optString("createdAt")
                ));
            }
        } catch (JSONException ignored) {
        }
        return posts;
    }

    private void savePosts(List<PostRecord> posts) {
        JSONArray arr = new JSONArray();
        for (PostRecord post : posts) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", post.title);
                obj.put("content", post.content);
                obj.put("createdAt", post.createdAt);
            } catch (JSONException ignored) {
            }
            arr.put(obj);
        }
        prefs.edit().putString(KEY_POSTS, arr.toString()).apply();
    }

    public String register(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return "用户名和密码不能为空";
        }
        prefs.edit()
                .putString(KEY_REGISTERED_USER, username.trim())
                .putString(KEY_REGISTERED_PASSWORD, password)
                .apply();
        return "注册成功";
    }

    public String login(String username, String password) {
        String savedUser = prefs.getString(KEY_REGISTERED_USER, "");
        String savedPass = prefs.getString(KEY_REGISTERED_PASSWORD, "");
        if (!username.equals(savedUser) || !password.equals(savedPass)) {
            return "登录失败：账号或密码错误";
        }
        prefs.edit().putString(KEY_LOGGED_IN_USER, username).apply();
        return "登录成功";
    }

    public String getLoggedInUser() {
        return prefs.getString(KEY_LOGGED_IN_USER, "未登录");
    }

    public void logout() {
        prefs.edit().remove(KEY_LOGGED_IN_USER).apply();
    }

    public void clearImageHistory() {
        prefs.edit().remove(KEY_IMAGE_HISTORY).apply();
    }

    public void clearPosts() {
        prefs.edit().remove(KEY_POSTS).apply();
    }

    public void clearCacheAndRecords() {
        prefs.edit()
                .remove(KEY_IMAGE_HISTORY)
                .remove(KEY_POSTS)
                .remove(KEY_LAST_EMOTION)
                .remove(KEY_LAST_SUGGESTION)
                .remove(KEY_LAST_IMAGE_STYLE)
                .remove(KEY_PENDING_POST_TITLE)
                .remove(KEY_PENDING_POST_CONTENT)
                .apply();
    }

    public void savePendingPostDraft(String title, String content) {
        savePendingPostDraft(title, content, "");
    }

    public void savePendingPostDraft(String title, String content, String imagePath) {
        prefs.edit()
                .putString(KEY_PENDING_POST_TITLE, title == null ? "" : title)
                .putString(KEY_PENDING_POST_CONTENT, content == null ? "" : content)
                .putString(KEY_PENDING_POST_IMAGE_PATH, imagePath == null ? "" : imagePath)
                .apply();
    }

    public PostDraft getPendingPostDraft() {
        return new PostDraft(
                prefs.getString(KEY_PENDING_POST_TITLE, ""),
                prefs.getString(KEY_PENDING_POST_CONTENT, ""),
                prefs.getString(KEY_PENDING_POST_IMAGE_PATH, "")
        );
    }

    public void clearPendingPostDraft() {
        prefs.edit()
                .remove(KEY_PENDING_POST_TITLE)
                .remove(KEY_PENDING_POST_CONTENT)
                .remove(KEY_PENDING_POST_IMAGE_PATH)
                .apply();
    }

    public static class ImageRecord {
        public final String day;
        public final String emotion;
        public final String style;
        public final String sourceEdfName;
        public final String createdAt;

        public ImageRecord(String day, String emotion, String style, String sourceEdfName, String createdAt) {
            this.day = day;
            this.emotion = emotion;
            this.style = style;
            this.sourceEdfName = sourceEdfName;
            this.createdAt = createdAt;
        }
    }

    public static class PostRecord {
        public final String title;
        public final String content;
        public final String createdAt;

        public PostRecord(String title, String content, String createdAt) {
            this.title = title;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    public static class PostDraft {
        public final String title;
        public final String content;
        public final String imagePath;

        public PostDraft(String title, String content, String imagePath) {
            this.title = title;
            this.content = content;
            this.imagePath = imagePath;
        }

        public boolean isEmpty() {
            return (title == null || title.trim().isEmpty())
                    && (content == null || content.trim().isEmpty())
                    && (imagePath == null || imagePath.trim().isEmpty());
        }
    }
}
