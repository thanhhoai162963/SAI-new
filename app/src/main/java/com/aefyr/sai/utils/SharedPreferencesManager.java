package com.aefyr.sai.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesManager {
    private static SharedPreferencesManager instance;
    private SharedPreferences sharedPreferences;

    // Tên của SharedPreferences
    private static final String PREF_NAME = "MySharedPreferences";

    // Khóa (key) và giá trị mặc định
    private static final String KEY_LANG = "lang";
    private static final String DEFAULT_USERNAME = "";

    // Private constructor để ngăn việc tạo đối tượng từ bên ngoài
    private SharedPreferencesManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // Singleton instance
    public static synchronized SharedPreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPreferencesManager(context);
        }
        return instance;
    }

    // Lưu giá trị
    public void setLang(String username) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LANG, username);
        editor.apply();
    }

    // Đọc giá trị
    public String getLang() {
        return sharedPreferences.getString(KEY_LANG, DEFAULT_USERNAME);
    }

    // Xóa giá trị
    public void clear() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
}