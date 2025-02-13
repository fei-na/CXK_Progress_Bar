package com.fina.cxkprogressbar;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "CXKProgressBarSettings",
    storages = @Storage("cxk-progress-bar.xml")
)
public class CXKProgressBarSettings implements PersistentStateComponent<CXKProgressBarSettings> {
    public boolean isEnabled = true;

    @Nullable
    @Override
    public CXKProgressBarSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CXKProgressBarSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
} 