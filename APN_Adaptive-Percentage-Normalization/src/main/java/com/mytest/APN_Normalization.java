package com.mytest;

import ij.*;
import ij.gui.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class APN_Normalization implements PlugInFilter {
    ImagePlus imp;

    // === 核心参数 (固定) ===
    private static final int    WINDOW_SIZE = 64;
    private static final int    STEP_SIZE   = 32;
    private static final double STD_MULT    = 0.7;
    private static final double KURT_MULT   = 2.0;

    // 固定常量
    private static final int maxBgBlocks = 100;
    private static final double pSignalPercentile = 99.8;

    // 内部类：Block
    private class Block implements Comparable<Block> {
        int x, y;
        float freqStd, grayStd, kurtosis;
        double bgScore;
        public Block(int x, int y, float freqStd, float grayStd, float kurtosis) {
            this.x = x; this.y = y; this.freqStd = freqStd; this.grayStd = grayStd; this.kurtosis = kurtosis;
        }
        @Override
        public int compareTo(Block other) { return Double.compare(this.bgScore, other.bgScore); }
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        // 修改点：允许 8-bit, 16-bit, 32-bit 图像
        // 注意：不加 DOES_STACKS，因为我们要手动控制 Stack 循环
        return DOES_8G | DOES_16 | DOES_32; 
    }

    @Override
    public void run(ImageProcessor ip) {
        // 1. 弹出 GUI
        if (!showBeautifulDialog()) return;

        IJ.showStatus("APN Analysis...");

        // === 确定“参考图像” ===
        // 这里的逻辑：先拿到原始数据，不管它是8bit还是16bit
        ImageProcessor referenceIp = ip;
        boolean isStack = imp.getStackSize() > 1;

        if (isStack) {
            // 如果是 Stack，取第一张
            referenceIp = imp.getStack().getProcessor(1);
            IJ.log("Mode: Stack Processing (Reference: Slice 1)");
        } else {
            IJ.log("Mode: Single Image Processing");
        }

        // === 关键修改：高位深自动转 8-bit ===
        // 我们创建一个专门用于计算参数的 8-bit 副本
        // convertToByte(true) 会根据图像的 min/max 自动缩放到 0-255
        ImageProcessor workIp8bit; 
        if (referenceIp.getBitDepth() > 8) {
            workIp8bit = referenceIp.convertToByte(true); 
        } else {
            workIp8bit = referenceIp.duplicate(); // 如果已经是8bit，复制一份以防万一
        }

        // ==========================================
        // === 第一阶段：基于 workIp8bit 计算参数 ===
        // ==========================================
        
        // 此时 workIp8bit 必然是 8-bit，可以安全地拿 pixels
        byte[] refRawPixels = (byte[]) workIp8bit.getPixels();
        
        // 同时也需要 FloatProcessor 进行 FFT 计算
        FloatProcessor workFloatIp = (FloatProcessor) workIp8bit.convertToFloat();
        int width = workFloatIp.getWidth();
        int height = workFloatIp.getHeight();

        List<Block> allBlocks = new ArrayList<>();
        List<Float> allFreqStds = new ArrayList<>();
        List<Float> allGrayStds = new ArrayList<>();

        for (int y = 0; y <= height - WINDOW_SIZE; y += STEP_SIZE) {
            for (int x = 0; x <= width - WINDOW_SIZE; x += STEP_SIZE) {
                workFloatIp.setRoi(x, y, WINDOW_SIZE, WINDOW_SIZE);
                ImageProcessor blockIp = workFloatIp.crop();
                float freqStd = calculateFreqStd(blockIp);
                float grayStd = (float) blockIp.getStatistics().stdDev;
                float kurt = calculateKurtosis(blockIp);
                allBlocks.add(new Block(x, y, freqStd, grayStd, kurt));
                allFreqStds.add(freqStd);
                allGrayStds.add(grayStd);
            }
        }
        workFloatIp.resetRoi();

        if (allBlocks.isEmpty()) {
            IJ.error("Image too small for APN analysis.");
            return;
        }

        double minFreq = Collections.min(allFreqStds);
        double stdFreq = getStdDev(allFreqStds);
        double threshFreq = minFreq + STD_MULT * stdFreq;
        double minGray = Collections.min(allGrayStds);
        double stdGray = getStdDev(allGrayStds);
        double threshGray = minGray + STD_MULT * stdGray;

        List<Block> potentialBg = new ArrayList<>();
        List<Block> potentialSignal = new ArrayList<>();
        for (Block b : allBlocks) {
            if (b.freqStd < threshFreq && b.grayStd < threshGray) potentialBg.add(b);
            else potentialSignal.add(b);
        }

        List<Block> finalBgBlocks = new ArrayList<>();
        if (!potentialBg.isEmpty()) {
            for (Block b : potentialBg) b.bgScore = 0.5 * (b.freqStd / threshFreq) + 0.5 * (b.grayStd / threshGray);
            Collections.sort(potentialBg);
            int count = Math.min(maxBgBlocks, potentialBg.size());
            for (int i = 0; i < count; i++) finalBgBlocks.add(potentialBg.get(i));
        }

        List<Block> finalSignalBlocks = new ArrayList<>();
        if (!potentialSignal.isEmpty()) {
            List<Float> signalKurts = new ArrayList<>();
            for (Block b : potentialSignal) signalKurts.add(b.kurtosis);
            double medianKurt = getMedian(signalKurts);
            double iqrKurt = getIQR(signalKurts);
            double kurtThreshold = medianKurt + KURT_MULT * iqrKurt;
            for (Block b : potentialSignal) {
                if (b.kurtosis < kurtThreshold) finalSignalBlocks.add(b);
            }
        }

        double Xmin, Xmax;
        
        // --- Calculate Xmin (使用转换后的 refRawPixels) ---
        if (!finalBgBlocks.isEmpty()) {
            double sumVal = 0; long pixelCount = 0;
            for (Block b : finalBgBlocks) {
                for (int by = 0; by < WINDOW_SIZE; by++) {
                    int offset = (b.y + by) * width + b.x;
                    for (int bx = 0; bx < WINDOW_SIZE; bx++) {
                        sumVal += (refRawPixels[offset + bx] & 0xff);
                        pixelCount++;
                    }
                }
            }
            Xmin = sumVal / pixelCount;
        } else {
            Xmin = getPercentile(workIp8bit, 1.0);
        }

        // --- Calculate Xmax ---
        if (!finalSignalBlocks.isEmpty()) {
            int estimatedSize = finalSignalBlocks.size() * WINDOW_SIZE * WINDOW_SIZE;
            float[] signalPixels = new float[estimatedSize];
            int ptr = 0;
            for (Block b : finalSignalBlocks) {
                for (int by = 0; by < WINDOW_SIZE; by++) {
                    int offset = (b.y + by) * width + b.x;
                    for (int bx = 0; bx < WINDOW_SIZE; bx++) {
                        signalPixels[ptr++] = (refRawPixels[offset + bx] & 0xff);
                    }
                }
            }
            Xmax = getPercentileFromFloatArray(signalPixels, ptr, pSignalPercentile);
        } else {
            Xmax = getPercentile(workIp8bit, 99.8);
        }

        if (Xmax <= Xmin) {
            Xmin = getPercentile(workIp8bit, 1.0);
            Xmax = getPercentile(workIp8bit, 99.0);
        }

        // === 计算 Pmin 和 Pmax (用于文件名) ===
        // 注意：这里必须用 workIp8bit 来计算排位，因为 Xmin/Xmax 是基于 8-bit 空间的
        double Pmin_adaptive = getRank(workIp8bit, Xmin);
        double Pmax_adaptive = getRank(workIp8bit, Xmax);

        IJ.log(String.format("Calculated Limits (8-bit scale): Xmin=%.2f (Pmin=%.2f%%), Xmax=%.2f (Pmax=%.2f%%)", 
               Xmin, Pmin_adaptive, Xmax, Pmax_adaptive));

        // ==========================================
        // === 第二阶段：应用参数并输出结果        ===
        // ==========================================

        if (isStack) {
            // --- Stack 模式 ---
            int stackSize = imp.getStackSize();
            ImageStack oldStack = imp.getStack();
            ImageStack newStack = new ImageStack(width, height);

            for (int s = 1; s <= stackSize; s++) {
                IJ.showProgress(s, stackSize);
                
                // 获取当前层
                ImageProcessor sliceIp = oldStack.getProcessor(s);
                
                // === 关键修改：如果当前层是高位深，先转 8-bit ===
                // convertToByte(true) 返回一个新的处理器，不会破坏原 Stack
                ImageProcessor sliceIp8bit;
                if (sliceIp.getBitDepth() > 8) {
                    sliceIp8bit = sliceIp.convertToByte(true);
                } else {
                    sliceIp8bit = sliceIp; // 如果已经是8bit，直接用
                }

                byte[] srcPixels = (byte[]) sliceIp8bit.getPixels();
                byte[] dstPixels = new byte[srcPixels.length];

                for (int i = 0; i < srcPixels.length; i++) {
                    int val = srcPixels[i] & 0xff;
                    double norm = (val - Xmin) / (Xmax - Xmin);
                    if (norm < 0) norm = 0;
                    if (norm > 1) norm = 1;
                    dstPixels[i] = (byte) Math.round(norm * 255);
                }
                newStack.addSlice(oldStack.getSliceLabel(s), dstPixels);
            }

            // Stack 输出文件名
            String title = String.format("Stack_Norm_Pmin%.2f_Pmax%.2f", Pmin_adaptive, Pmax_adaptive);
            new ImagePlus(title, newStack).show();

        } else {
            // --- 单图模式 ---
            // 直接用我们之前转换好的 workIp8bit 画直方图
            drawHistogram(workIp8bit, Xmin, Xmax, Pmin_adaptive, Pmax_adaptive);

            ByteProcessor resultIp = new ByteProcessor(width, height);
            byte[] resPixels = (byte[]) resultIp.getPixels();
            byte[] srcPixels = (byte[]) workIp8bit.getPixels();
            
            for (int i = 0; i < srcPixels.length; i++) {
                int val = srcPixels[i] & 0xff;
                double norm = (val - Xmin) / (Xmax - Xmin);
                if (norm < 0) norm = 0;
                if (norm > 1) norm = 1;
                resPixels[i] = (byte) Math.round(norm * 255);
            }
            
            String title = String.format("Normalized_Pmin%.2f_Pmax%.2f", Pmin_adaptive, Pmax_adaptive);
            new ImagePlus(title, resultIp).show();
        }
    }

    // ==========================================================
    // ===             GUI (IPIC Logo 版)                     ===
    // ==========================================================

    private boolean showBeautifulDialog() {
        JDialog dialog = new JDialog(IJ.getInstance(), "APN Launcher", true);
        dialog.setUndecorated(true); 
        dialog.setLayout(new BorderLayout());
        
        APNLogoPanel logoPanel = new APNLogoPanel();
        logoPanel.setPreferredSize(new Dimension(500, 220)); 
        logoPanel.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));
        dialog.add(logoPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        bottomPanel.setBackground(new Color(30, 30, 30));
        bottomPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JButton btnRun = new JButton("Start Processing");
        styleButton(btnRun, new Color(60, 179, 113));
        
        JButton btnCancel = new JButton("Close");
        styleButton(btnCancel, new Color(80, 80, 80));

        final boolean[] wasRun = {false};
        btnRun.addActionListener(e -> { wasRun[0] = true; dialog.dispose(); });
        btnCancel.addActionListener(e -> dialog.dispose());

        bottomPanel.add(btnCancel);
        bottomPanel.add(btnRun);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        MouseAdapter dragListener = new MouseAdapter() {
            private Point mouseDownCompCoords = null;
            public void mousePressed(MouseEvent e) { mouseDownCompCoords = e.getPoint(); }
            public void mouseReleased(MouseEvent e) { mouseDownCompCoords = null; }
            public void mouseDragged(MouseEvent e) {
                Point currCoords = e.getLocationOnScreen();
                dialog.setLocation(currCoords.x - mouseDownCompCoords.x, currCoords.y - mouseDownCompCoords.y);
            }
        };
        logoPanel.addMouseListener(dragListener);
        logoPanel.addMouseMotionListener(dragListener);

        dialog.pack();
        dialog.setLocationRelativeTo(IJ.getInstance());
        dialog.setVisible(true);

        return wasRun[0];
    }

    private void styleButton(JButton btn, Color bgColor) {
        btn.setPreferredSize(new Dimension(140, 35));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    class APNLogoPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(); int h = getHeight();

            GradientPaint gp = new GradientPaint(0, 0, new Color(45, 48, 55), 0, h, new Color(25, 25, 25));
            g2.setPaint(gp); g2.fillRect(0, 0, w, h);

            g2.setColor(new Color(255, 255, 255, 5));
            for(int i=0; i<w; i+=20) g2.drawLine(i, 0, i, h);
            for(int i=0; i<h; i+=20) g2.drawLine(0, i, w, i);

            g2.setColor(new Color(60, 179, 113, 80)); 
            g2.setStroke(new BasicStroke(3));
            Path2D wave = new Path2D.Double();
            wave.moveTo(0, h * 0.7);
            wave.curveTo(w*0.3, h*0.5, w*0.6, h*0.9, w, h*0.6);
            g2.draw(wave);
            
            g2.setColor(new Color(100, 200, 255, 50)); 
            Path2D wave2 = new Path2D.Double();
            wave2.moveTo(0, h * 0.75);
            wave2.curveTo(w*0.4, h*0.9, w*0.7, h*0.5, w, h*0.8);
            g2.draw(wave2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 64));
            FontMetrics fm = g2.getFontMetrics();
            String mainText = "APN";
            int textX = (w - fm.stringWidth(mainText)) / 2;
            int textY = h / 2 - 10;
            g2.drawString(mainText, textX, textY);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g2.setColor(new Color(180, 180, 180));
            String subText = "Adaptive Percentage Normalization";
            int subX = (w - g2.getFontMetrics().stringWidth(subText)) / 2;
            g2.drawString(subText, subX, textY + 35);
            
            // IPIC LOGO
            g2.setFont(new Font("Monospaced", Font.BOLD, 18));
            g2.setColor(new Color(120, 120, 120));
            String verText = "v1.0 | IPIC";
            int verX = (w - g2.getFontMetrics().stringWidth(verText)) / 2;
            g2.drawString(verText, verX, h - 15);
        }
    }

    // --- 绘图 (仅单图模式调用) ---
    private void drawHistogram(ImageProcessor ip, double Xmin, double Xmax, double Pmin, double Pmax) {
        int nBins = 256;
        int[] histCounts = ip.getHistogram();
        double total = ip.getWidth() * ip.getHeight();
        double[] yValues = new double[nBins];
        double[] xValues = new double[nBins];
        double maxDensity = 0;
        for (int i = 0; i < nBins; i++) {
            xValues[i] = i;
            yValues[i] = histCounts[i] / total;
            if (yValues[i] > maxDensity) maxDensity = yValues[i];
        }
        Plot plot = new Plot("Pixel Distribution", "Pixel Value", "Probability");
        plot.setColor(Color.LIGHT_GRAY);
        plot.add("bar", xValues, yValues);
        double yLimit = maxDensity * 1.2;
        plot.setLimits(0, 255, 0, yLimit); 
        plot.setColor(Color.RED); plot.setLineWidth(2); plot.drawLine(Xmin, 0, Xmin, yLimit); 
        plot.setColor(Color.GREEN); plot.setLineWidth(2); plot.drawLine(Xmax, 0, Xmax, yLimit); 
        plot.show();
    }

    // --- 计算工具类 ---
    private float calculateFreqStd(ImageProcessor ip) {
        ImageProcessor floatIp = ip.convertToFloat();
        FHT fht = new FHT(floatIp); fht.transform();
        float[] h = (float[]) fht.getPixels();
        int w = fht.getWidth(); int h_img = fht.getHeight();
        float[] magnitudes = new float[w * h_img];
        for (int y = 0; y < h_img; y++) {
            for (int x = 0; x < w; x++) {
                int idx1 = y * w + x;
                int symY = (h_img - y) % h_img;
                int symX = (w - x) % w;
                int idx2 = symY * w + symX;
                float val1 = h[idx1]; float val2 = h[idx2];
                magnitudes[idx1] = (float) Math.sqrt((val1 * val1 + val2 * val2) / 2.0);
            }
        }
        return (float) getStdDevForPixels(magnitudes);
    }
    private double getStdDevForPixels(float[] pixels) {
        double sum = 0; double sumSq = 0; int n = pixels.length;
        for (float v : pixels) { sum += v; sumSq += v*v; }
        double mean = sum / n;
        return Math.sqrt((sumSq/n) - (mean*mean));
    }
    private float calculateKurtosis(ImageProcessor ip) {
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        int n = pixels.length;
        double sum = 0, sumSq = 0;
        for (float v : pixels) { sum += v; sumSq += v*v; }
        double mean = sum / n;
        double std = Math.sqrt((sumSq/n) - (mean*mean));
        if (std == 0) return 0;
        double sum4th = 0;
        for (float v : pixels) sum4th += Math.pow((v - mean) / std, 4);
        return (float) (sum4th / n);
    }
    private double getStdDev(List<Float> list) {
        double sum = 0; for (float v : list) sum += v;
        double mean = sum / list.size();
        double sumSq = 0; for (float v : list) sumSq += Math.pow(v - mean, 2);
        return Math.sqrt(sumSq / list.size());
    }
    private double getMedian(List<Float> list) {
        Collections.sort(list); int n = list.size(); if (n == 0) return 0;
        return (n % 2 == 0) ? (list.get(n/2-1) + list.get(n/2))/2.0 : list.get(n/2);
    }
    private double getIQR(List<Float> list) {
        Collections.sort(list); int n = list.size(); if (n == 0) return 0;
        return list.get((int)(n * 0.75)) - list.get((int)(n * 0.25));
    }
    private double getPercentileFromFloatArray(float[] arr, int count, double p) {
        if (count == 0) return 0; Arrays.sort(arr, 0, count);
        int idx = (int) Math.round((p / 100.0) * count);
        if (idx < 0) idx = 0; if (idx >= count) idx = count - 1;
        return arr[idx];
    }
    private double getPercentile(ImageProcessor ip, double p) {
        int[] hist = ip.getHistogram(); int total = ip.getPixelCount();
        int target = (int) (total * p / 100.0); int sum = 0;
        for (int i=0; i<256; i++) { sum += hist[i]; if (sum >= target) return i; }
        return 255;
    }
    private double getRank(ImageProcessor ip, double value) {
        int[] hist = ip.getHistogram(); int total = ip.getPixelCount(); int count = 0;
        int limit = (int)Math.min(255, Math.max(0, Math.round(value)));
        for (int i=0; i<=limit; i++) count += hist[i];
        return (double)count / total * 100.0;
    }
}