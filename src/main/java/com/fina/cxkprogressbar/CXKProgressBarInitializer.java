package com.fina.cxkprogressbar;

import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CXKProgressBarInitializer extends PreloadingActivity {
    private static final Logger LOG = Logger.getInstance(CXKProgressBarInitializer.class);

    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
        LOG.info("=== CXK Progress Bar 初始化开始 ===");
        try {
            // 设置默认进度条 UI
            UIManager.put("ProgressBarUI", CXKProgressBarUI.class.getName());
            UIManager.getDefaults().put(CXKProgressBarUI.class.getName(), CXKProgressBarUI.class);
            
            // 设置 IDEA 特定的进度条 UI
            UIManager.put(ProgressWindow.class.getName() + ".progressBarUi", 
                         CXKProgressBarUI.class.getName());
            
            LOG.info("✅ 进度条UI注册完成");
            
            // 强制更新当前主题
            LafManager.getInstance().updateUI();
            
        } catch (Exception e) {
            LOG.error("❌ 初始化失败", e);
        }
    }
} 