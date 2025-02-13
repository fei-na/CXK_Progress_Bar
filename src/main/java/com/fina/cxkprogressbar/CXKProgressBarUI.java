package com.fina.cxkprogressbar;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.openapi.diagnostic.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;
import java.awt.event.HierarchyEvent;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseAdapter;

import static java.awt.desktop.UserSessionEvent.Reason.LOCK;

public class CXKProgressBarUI extends BasicProgressBarUI {
    private static final Logger LOG = Logger.getInstance(CXKProgressBarUI.class);
    private static final int FRAME_DELAY = 50;
    private static final int NOTE_SPACING = JBUI.scale(15);
    private static List<Image> frames;
    private static final int FRAME_COUNT = 12;
    private static final String TREBLE_CLEF = "𝄞";  // 高音谱号
    private static final String BASS_CLEF = "𝄢";    // 低音谱号
    private static final Object LOCK = new Object();  // 添加锁对象
    
    private volatile int animationIndex = 0;
    private long lastUpdate = 0;
    private boolean isDisposed = false;
    private boolean wasIndeterminate = false;
    private Clip audioClip;
    private JProgressBar currentBar;  // 添加这个字段保存当前进度条引用

    @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
    public static ComponentUI createUI(JComponent c) {
        c.setBorder(JBUI.Borders.empty().asUIResource());
        return new CXKProgressBarUI();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return new Dimension(super.getPreferredSize(c).width, JBUI.scale(20));
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        isDisposed = false;
        LOG.info("安装UI到组件: " + c.getClass().getName());
        ensureFramesLoaded();
        loadAudioClip();
        
        if (c instanceof JProgressBar) {
            currentBar = (JProgressBar) c;
            wasIndeterminate = currentBar.isIndeterminate();
            
            // 添加层次结构监听器，等待组件被添加到父容器
            currentBar.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
                    LOG.info("进度条父容器变更");
                    setupCancelButton();
                }
            });
            
            // 立即尝试一次设置
            setupCancelButton();
        }
    }

    private void setupCancelButton() {
        if (currentBar == null || isDisposed) return;
        
        Container parent = currentBar.getParent();
        if (parent != null) {
            LOG.info("正在查找取消按钮...");
            LOG.info("父容器类型: " + parent.getClass().getName());
            
            // 递归查找所有父容器
            Container topParent = parent;
            while (topParent.getParent() != null) {
                topParent = topParent.getParent();
            }
            
            // 递归查找按钮
            findAndSetupCancelButton(topParent);
        }
    }

    private void findAndSetupCancelButton(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof AbstractButton) {  // 使用 AbstractButton 作为基类
                AbstractButton button = (AbstractButton) comp;
                String className = button.getClass().getName();
                LOG.info("找到按钮: actionCommand=" + button.getActionCommand() 
                        + ", icon=" + (button.getIcon() != null ? button.getIcon().toString() : "null")
                        + ", text=" + button.getText()
                        + ", class=" + className);
                
                // 检查是否是取消按钮
                boolean isCancel = false;
                
                // 检查常规取消按钮
                if ((button.getActionCommand() != null && 
                     (button.getActionCommand().equals("Cancel") || 
                      button.getActionCommand().equals("Stop") || 
                      button.getActionCommand().equals("stop"))) ||
                    "Cancel".equals(button.getText()) ||
                    "Stop".equals(button.getText())) {
                    isCancel = true;
                }
                
                // 检查进度条上的内联取消按钮
                if (className.contains("InplaceButton") && 
                    button.getParent() != null && 
                    button.getParent().getClass().getName().contains("ProgressIndicator")) {
                    isCancel = true;
                }
                
                if (isCancel) {
                    LOG.info("找到取消按钮，添加监听器");
                    
                    // 移除现有的监听器
                    MouseListener[] mouseListeners = button.getMouseListeners();
                    for (MouseListener listener : mouseListeners) {
                        if (listener.toString().contains("playStopSound")) {
                            button.removeMouseListener(listener);
                        }
                    }
                    
                    // 添加新的监听器
                    button.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            playStopSound();
                        }
                        
                        @Override
                        public void mouseReleased(MouseEvent e) {
                            playStopSound();
                        }
                    });
                    
                    LOG.info("成功添加取消按钮监听器");
                }
            }
            
            // 递归查找子容器
            if (comp instanceof Container) {
                findAndSetupCancelButton((Container) comp);
            }
        }
    }

    @Override
    public void uninstallUI(JComponent c) {
        if (audioClip != null) {
            audioClip.close();
        }
        currentBar = null;  // 清除引用
        isDisposed = true;
        super.uninstallUI(c);
    }

    private void ensureFramesLoaded() {
        if (frames == null || frames.isEmpty()) {
            synchronized (LOCK) {
                if (frames == null || frames.isEmpty()) {
                    LOG.info("重新加载图片资源...");
                    loadImages();
                    if (frames != null && !frames.isEmpty()) {
                        LOG.info("图片加载成功，重置动画状态");
                        animationIndex = 0;
                        lastUpdate = System.currentTimeMillis();
                    }
                }
            }
        }
    }

    private void loadImages() {
        List<Image> newFrames = new ArrayList<>();
        boolean loadSuccess = false;
        
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            for (int i = 0; i < FRAME_COUNT; i++) {
                String imagePath = String.format("images/cxk/frame_%04d.png", i);
                URL resource = classLoader.getResource(imagePath);
                if (resource != null) {
                    BufferedImage image = ImageIO.read(resource);
                    if (image != null) {
                        newFrames.add(image);
                        loadSuccess = true;
                    } else {
                        LOG.error("无法加载图片: " + imagePath);
                    }
                } else {
                    LOG.error("找不到图片资源: " + imagePath);
                }
            }
        } catch (Exception e) {
            LOG.error("加载图片时发生错误", e);
        }

        if (loadSuccess) {
            frames = newFrames;
            LOG.info("✅ 成功加载 " + frames.size() + " 帧图片");
        } else {
            LOG.error("❌ 图片加载失败");
        }
    }

    private BufferedImage normalizeImage(BufferedImage source) {
        // 如果已经是正确的格式，直接返回
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        
        // 创建新的ARGB图片
        BufferedImage normalized = new BufferedImage(
            source.getWidth(),
            source.getHeight(),
            BufferedImage.TYPE_INT_ARGB
        );
        
        // 绘制原图到新图
        Graphics2D g2d = normalized.createGraphics();
        try {
            g2d.setComposite(AlphaComposite.Src);
            g2d.drawImage(source, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        
        return normalized;
    }

    private void debugImage(Image image, String prefix) {
        if (image == null) {
            LOG.error(prefix + "图片为空！");
            return;
        }
        
        if (image instanceof BufferedImage) {
            BufferedImage bi = (BufferedImage) image;
            LOG.info(String.format(prefix + "图片信息: 类型=%d, 尺寸=%dx%d, 透明=%b, 颜色模型=%s",
                bi.getType(),
                bi.getWidth(),
                bi.getHeight(),
                bi.getColorModel().hasAlpha(),
                bi.getColorModel().getClass().getSimpleName()
            ));
            
            // 检查图片是否全黑
            boolean isAllBlack = true;
            for (int x = 0; x < bi.getWidth() && isAllBlack; x++) {
                for (int y = 0; y < bi.getHeight() && isAllBlack; y++) {
                    int rgb = bi.getRGB(x, y);
                    if (rgb != 0 && rgb != Color.BLACK.getRGB()) {
                        isAllBlack = false;
                    }
                }
            }
            LOG.info(prefix + "图片是否全黑: " + isAllBlack);
        }
    }

    private void drawMusicStaff(Graphics2D g, int width, int h, float lineSpacing, 
                               boolean isDeterminate, int progress, int imageSize) {
        // 先绘制五线谱
        for (int i = 1; i <= 5; i++) {
            float y = i * lineSpacing;
            // 走过的部分（考虑图片左侧空白，提前1/4图片宽度变色）
            g.setColor(new JBColor(Gray._88.withAlpha(180), Gray._88.withAlpha(180)));
            g.drawLine(
                JBUI.scale(20),  
                (int)y, 
                isDeterminate ? 
                    Math.min(JBUI.scale(20) + progress, width - JBUI.scale(5)) :
                    Math.max(JBUI.scale(20), animationIndex - JBUI.scale(10) + imageSize/4), 
                (int)y
            );
            // 未走过的部分
            g.setColor(new JBColor(Gray._165.withAlpha(50), Gray._128.withAlpha(50)));
            g.drawLine(
                isDeterminate ? 
                    Math.min(JBUI.scale(20) + progress, width - JBUI.scale(5)) :
                    Math.max(JBUI.scale(20), animationIndex - JBUI.scale(10) + imageSize/4),
                (int)y, 
                width - JBUI.scale(5), 
                (int)y
            );
        }

        // 绘制谱号
        g.setFont(new Font("Serif", Font.BOLD, JBUI.scale(16)));
        g.setColor(new JBColor(Gray._88.withAlpha(180), Gray._88.withAlpha(180)));
        String clef = isDeterminate ? BASS_CLEF : TREBLE_CLEF;
        g.drawString(clef, JBUI.scale(5), h - JBUI.scale(5));

        // 计算需要的音符数量（根据宽度和固定间距）
        int noteCount = (width - JBUI.scale(40)) / NOTE_SPACING;
        String[] notes = {"♪", "♫", "♩", "♬"};
        g.setFont(new Font("Serif", Font.PLAIN, JBUI.scale(12)));

        // 绘制音符
        for (int i = 0; i < noteCount; i++) {
            String note = notes[i % notes.length];
            int noteX = JBUI.scale(30) + i * NOTE_SPACING;
            float noteY = (float)(Math.sin(i * 0.5) * JBUI.scale(5) + h/2);
            
            // 根据调整后的位置决定音符颜色
            if ((isDeterminate && noteX <= JBUI.scale(20) + progress) ||
                (!isDeterminate && noteX <= animationIndex - JBUI.scale(10) + imageSize/4)) {
                g.setColor(new JBColor(Gray._88.withAlpha(180), Gray._88.withAlpha(180)));
            } else {
                g.setColor(new JBColor(Gray._165.withAlpha(50), Gray._128.withAlpha(50)));
            }
            g.drawString(note, noteX, noteY);
        }
    }

    private void paintProgress(Graphics2D g, int width, int height, boolean isDeterminate, int progress) {
        final int h = getPreferredSize(currentBar).height;
        final float lineSpacing = h / 6.0f;

        // 绘制五线谱和音符
        drawMusicStaff(g, width, h, lineSpacing, isDeterminate, progress, h);

        // 绘制蔡徐坤图片
        Image currentFrame = frames.get((int)((System.currentTimeMillis() / 50) % frames.size()));
        if (isDeterminate) {
            g.drawImage(currentFrame, 
                       Math.min(JBUI.scale(20) + progress - h/2, width - h),
                       0,
                       h,
                       h,
                       null);
        } else {
            g.drawImage(currentFrame, 
                       animationIndex - JBUI.scale(10),
                       0,
                       h,
                       h,
                       null);
        }
    }

    @Override
    protected void paintDeterminate(Graphics g2d, JComponent c) {
        if (!(g2d instanceof Graphics2D)) {
            return;
        }

        Graphics2D g = (Graphics2D) g2d;
        Insets b = progressBar.getInsets();
        int width = progressBar.getWidth() - (b.right + b.left);
        int height = progressBar.getHeight() - (b.top + b.bottom);

        if (width <= 0 || height <= 0) {
            return;
        }

        GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
        g.translate(0, (c.getHeight() - height) / 2);

        // 计算尺寸
        final int h = getPreferredSize(c).height;
        
        // 绘制背景
        Container parent = c.getParent();
        Color background = parent != null ? parent.getBackground() : UIUtil.getPanelBackground();
        
        if (!isEven(c.getHeight() - h)) height++;

        final float R = JBUI.scale(8f);
        final float off = JBUI.scale(1f);

        g.translate(0, (c.getHeight() - h) / 2);
        g.setColor(background);
        g.fill(new RoundRectangle2D.Float(off, off, width - 2f * off - off, h - 2f * off - off, R, R));

        // 计算进度
        int amountFull = getAmountFull(b, width, height);

        // 使用统一的绘制方法
        paintProgress(g, width, height, true, amountFull);

        g.translate(0, -(c.getHeight() - h) / 2);
        config.restore();

        // 继续动画
        if (!isDisposed) {
            c.repaint(FRAME_DELAY);
        }
    }

    @Override
    protected void paintIndeterminate(Graphics g2d, JComponent c) {
        if (isDisposed) {
            LOG.info("组件已销毁，跳过绘制");
            return;
        }

        // 确保图片已加载
        ensureFramesLoaded();

        if (frames == null || frames.isEmpty()) {
            LOG.error("⚠️ 无可用帧，使用默认进度条");
            super.paintIndeterminate(g2d, c);
            return;
        }

        try {
            if (!(g2d instanceof Graphics2D)) {
                return;
            }

            Graphics2D g = (Graphics2D) g2d;
            Insets b = progressBar.getInsets();
            int width = progressBar.getWidth() - (b.right + b.left);
            int height = progressBar.getHeight() - (b.top + b.bottom);

            if (width <= 0 || height <= 0) {
                return;
            }

            GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
            g.translate(0, (c.getHeight() - height) / 2);

            // 计算尺寸
            final int h = getPreferredSize(c).height;
            final float lineSpacing = h / 6.0f;  // 五线谱间距
            
            // 绘制背景
            Container parent = c.getParent();
            Color background = parent != null ? parent.getBackground() : UIUtil.getPanelBackground();
            
            if (!isEven(c.getHeight() - h)) height++;

            final float R = JBUI.scale(8f);
            final float off = JBUI.scale(1f);
            
            g.setColor(background);
            g.fill(new RoundRectangle2D.Float(off, off, width - 2f * off - off, h - 2f * off - off, R, R));

            // 绘制两侧竖线
            g.setColor(new JBColor(Gray._88.withAlpha(180), Gray._88.withAlpha(180)));
            g.drawLine(
                JBUI.scale(20), 
                (int)(lineSpacing), 
                JBUI.scale(20), 
                (int)(lineSpacing * 5)
            );
            g.drawLine(
                width - JBUI.scale(5), 
                (int)(lineSpacing), 
                width - JBUI.scale(5), 
                (int)(lineSpacing * 5)
            );

            paintProgress(g, width, height, false, 0);

            g.translate(0, -(c.getHeight() - h) / 2);
            config.restore();

            // 更新动画状态并确保不会停止
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdate > FRAME_DELAY) {
                animationIndex = (animationIndex + JBUI.scale(3)) % (width + JBUI.scale(10));
                lastUpdate = currentTime;
                
                // 添加更详细的调试日志
                if (animationIndex % 100 == 0) {
                    LOG.info(String.format(
                        "动画状态: index=%d, frames=%d, time=%d, disposed=%b, width=%d", 
                        animationIndex, frames.size(), currentTime, isDisposed, width
                    ));
                }
            }

            // 确保继续动画
            if (!isDisposed) {
                c.repaint(FRAME_DELAY);
            }

        } catch (Exception e) {
            LOG.error("绘制过程发生错误", e);
            super.paintIndeterminate(g2d, c);
        }
    }

    public int getAnimationIndex() {
        return animationIndex;
    }

    private static boolean isEven(int value) {
        return value % 2 == 0;
    }

    public CXKProgressBarUI() {
        LOG.info("=== CXK Progress Bar UI 被创建 ===");
        // 立即加载图片
        loadImages();
    }

    private void loadAudioClip() {
        try {
            if (audioClip != null) {
                audioClip.close();
            }
            
            // 尝试多个可能的路径
            URL url = null;
            String[] possiblePaths = {
                "/audio/stop.wav",
                "/sounds/stop.wav",
                "sounds/stop.wav",
                "audio/stop.wav",
                "/com/fina/cxkprogressbar/sounds/stop.wav",
                "/com/fina/cxkprogressbar/audio/stop.wav"
            };
            
            for (String path : possiblePaths) {
                url = getClass().getResource(path);
                if (url != null) {
                    LOG.info("找到音频文件: " + path);
                    break;
                }
            }
            
            if (url == null) {
                // 尝试使用 ClassLoader
                ClassLoader classLoader = getClass().getClassLoader();
                for (String path : possiblePaths) {
                    url = classLoader.getResource(path.startsWith("/") ? path.substring(1) : path);
                    if (url != null) {
                        LOG.info("通过ClassLoader找到音频文件: " + path);
                        break;
                    }
                }
            }
            
            if (url != null) {
                LOG.info("正在加载音频文件: " + url);
                try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(
                        new BufferedInputStream(url.openStream()))) {
                    
                    // 转换为支持的音频格式
                    AudioFormat targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        44100.0f,
                        16,
                        2,
                        4,
                        44100.0f,
                        false
                    );
                    
                    // 转换音频流
                    try (AudioInputStream convertedStream = 
                            AudioSystem.getAudioInputStream(targetFormat, audioInputStream)) {
                        audioClip = AudioSystem.getClip();
                        audioClip.open(convertedStream);
                        LOG.info("✅ 音效加载成功");
                    }
                }
            } else {
                LOG.error("❌ 找不到音效文件，尝试过以下路径:");
                for (String path : possiblePaths) {
                    LOG.error("  - " + path);
                }
            }
        } catch (Exception e) {
            LOG.error("加载音效失败", e);
            if (e instanceof LineUnavailableException) {
                LOG.error("音频系统不可用");
            } else if (e instanceof UnsupportedAudioFileException) {
                LOG.error("不支持的音频格式");
            } else if (e instanceof IOException) {
                LOG.error("读取音频文件失败");
            }
        }
    }

    private void playStopSound() {
        LOG.info("取消按钮被点击 - 开始处理");
        try {
            if (audioClip != null && !isDisposed) {
                if (audioClip.isRunning()) {
                    LOG.info("停止当前正在播放的音效");
                    audioClip.stop();
                }
                
                audioClip.setFramePosition(0);
                audioClip.start();
                LOG.info("✅ 音效播放成功启动");
            } else {
                LOG.info("❌ 无法播放音效: audioClip=" + (audioClip != null) + ", isDisposed=" + isDisposed);
            }
        } catch (Exception ex) {
            LOG.error("播放音效时发生错误", ex);
        }
    }
} 