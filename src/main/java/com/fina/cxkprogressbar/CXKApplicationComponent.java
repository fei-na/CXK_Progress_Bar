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

    public CXKApplicationComponent() {
        LOG.info("=== CXK Progress Bar Component Created ===");
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
        updateProgressBarUI();
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
} 