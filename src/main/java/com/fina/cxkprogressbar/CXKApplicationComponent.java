package com.fina.cxkprogressbar;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CXKApplicationComponent implements LafManagerListener, ApplicationActivationListener {
    private static final Logger LOG = Logger.getInstance(CXKApplicationComponent.class);
    // 添加一个静态标志来控制所有进度条
    private static volatile boolean isApplicationActive = true;

    public CXKApplicationComponent() {
        LOG.info("=== CXK Progress Bar Component Created ===");
        isApplicationActive = true;
        updateProgressBarUI();
    }

    @Override
    public void lookAndFeelChanged(@NotNull LafManager source) {
        LOG.info("主题变更，更新进度条UI");
        updateProgressBarUI();
    }

    @Override
    public void applicationActivated(@NotNull IdeFrame ideFrame) {
        LOG.info("应用激活，更新进度条UI");
        isApplicationActive = true;
        updateProgressBarUI();
    }

    @Override
    public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
        LOG.info("应用停用，强制停止所有进度条动画");
        isApplicationActive = false;
        // 强制停止所有进度条动画
        CXKProgressBarUI.stopAllAnimations();
    }

    private void updateProgressBarUI() {
        try {
            UIManager.put("ProgressBarUI", CXKProgressBarUI.class.getName());
            UIManager.getDefaults().put(CXKProgressBarUI.class.getName(), CXKProgressBarUI.class);
            LOG.info("✅ 进度条UI更新成功");
        } catch (Exception e) {
            LOG.error("❌ 进度条UI更新失败", e);
        }
    }
    
    // 提供一个静态方法让进度条UI检查应用程序状态
    public static boolean isApplicationActive() {
        return isApplicationActive;
    }
}