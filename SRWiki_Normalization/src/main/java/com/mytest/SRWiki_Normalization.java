package com.mytest;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Component;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SRWiki_Normalization implements PlugInFilter {

    private ImagePlus imp;

    private static final String PLUGIN_NAME = "SRWiki";
    private static final String PLUGIN_VERSION = "v3.0";
    private static final String HELP_URL = "https://github.com/SR-Wiki/APN";
    private static final String HEADER_BG_RESOURCE = "/srwiki_bio_header.png";

    private static final int WINDOW_SIZE = 64;
    private static final int STEP_SIZE = 32;
    private static final double STD_MULT = 0.7;
    private static final double KURT_MULT = 2.0;
    private static final int MAX_BG_BLOCKS = 100;
    private static final double SIGNAL_PERCENTILE = 99.8;

    private enum NormalizationMode {
        MAX_ONLY("Max normalization"),
        MIN_MAX("Min-Max normalization"),
        PERCENTILE("Percentile normalization"),
        APN("APN normalization"),
        Z_SCORE("Z-Score standardization"),
        MEAN("Mean normalization"),
        VECTOR("Vector normalization");

        final String label;

        NormalizationMode(String label) {
            this.label = label;
        }
    }

    private enum OutputType {
        BYTE_LINEAR,
        FLOAT_ZSCORE,
        FLOAT_MEAN,
        FLOAT_VECTOR
    }

    private static class LaunchConfig {
        NormalizationMode mode = NormalizationMode.APN;
        boolean showHistogram = true;
        double lowPercentile = 1.0;
        double highPercentile = 99.8;
    }

    private static class WorkingScale {
        double sourceMin;
        double sourceMax;
        String summary;
    }

    private static class ComputationModel {
        NormalizationMode mode;
        OutputType outputType;
        String methodName;
        String summary;
        double xMin;
        double xMax;
        double pMin;
        double pMax;
        double mean;
        double std;
        double dataMin;
        double dataMax;
        double l2Norm;
    }

    private class Block implements Comparable<Block> {
        int x, y;
        float freqStd, grayStd, kurtosis;
        double bgScore;

        Block(int x, int y, float freqStd, float grayStd, float kurtosis) {
            this.x = x;
            this.y = y;
            this.freqStd = freqStd;
            this.grayStd = grayStd;
            this.kurtosis = kurtosis;
        }

        @Override
        public int compareTo(Block other) {
            return Double.compare(this.bgScore, other.bgScore);
        }
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        return DOES_8G | DOES_16 | DOES_32 | NO_CHANGES;
    }

    @Override
    public void run(ImageProcessor ip) {
        if (imp == null) {
            IJ.error(PLUGIN_NAME, "No image is open.");
            return;
        }

        LaunchConfig config = showSRWikiDialog();
        if (config == null) return;

        boolean isStack = imp.getStackSize() > 1;
        ImageProcessor referenceOriginal = isStack ? imp.getStack().getProcessor(1) : imp.getProcessor();

        WorkingScale scale = buildWorkingScale(referenceOriginal);
        ByteProcessor reference8bit = toWorking8Bit(referenceOriginal, scale);
        if (reference8bit == null) {
            IJ.error(PLUGIN_NAME, "Unable to prepare 8-bit working image.");
            return;
        }

        ComputationModel model = buildComputationModel(reference8bit, config);
        if (model == null) {
            IJ.error(PLUGIN_NAME, "Normalization model could not be computed.");
            return;
        }

        logRunInfo(model, scale, isStack);

        if (!isStack && config.showHistogram && model.outputType == OutputType.BYTE_LINEAR) {
            drawHistogram(reference8bit, model);
        }

        if (isStack) {
            processStack(scale, model);
        } else {
            processSingle(reference8bit, model);
        }

        IJ.showStatus(PLUGIN_NAME + ": done.");
    }

    private void processSingle(ByteProcessor source8bit, ComputationModel model) {
        ImageProcessor result = applyModel(source8bit, model);
        String title = buildOutputTitle(imp.getTitle(), model);
        new ImagePlus(title, result).show();
    }

    private void processStack(WorkingScale scale, ComputationModel model) {
        ImageStack oldStack = imp.getStack();
        int width = oldStack.getWidth();
        int height = oldStack.getHeight();
        int size = oldStack.getSize();
        ImageStack newStack = new ImageStack(width, height);

        for (int s = 1; s <= size; s++) {
            IJ.showProgress(s, size);
            ByteProcessor slice8bit = toWorking8Bit(oldStack.getProcessor(s), scale);
            ImageProcessor result = applyModel(slice8bit, model);
            newStack.addSlice(oldStack.getSliceLabel(s), result);
        }

        String title = buildOutputTitle(imp.getTitle(), model);
        new ImagePlus(title, newStack).show();
    }

    private ImageProcessor applyModel(ByteProcessor source8bit, ComputationModel model) {
        switch (model.outputType) {
            case BYTE_LINEAR:
                return applyLinearNormalization(source8bit, model.xMin, model.xMax);
            case FLOAT_ZSCORE:
                return applyZScore(source8bit, model.mean, model.std);
            case FLOAT_MEAN:
                return applyMeanNormalization(source8bit, model.mean, model.dataMin, model.dataMax);
            case FLOAT_VECTOR:
                return applyVectorNormalization(source8bit, model.l2Norm);
            default:
                return applyLinearNormalization(source8bit, model.xMin, model.xMax);
        }
    }

    private String buildOutputTitle(String baseTitle, ComputationModel model) {
        if (model.outputType == OutputType.BYTE_LINEAR) {
            return String.format("%s_%s_Pmin%.2f_Pmax%.2f", baseTitle, model.methodName, model.pMin, model.pMax);
        }
        return String.format("%s_%s", baseTitle, model.methodName);
    }

    private ComputationModel buildComputationModel(ByteProcessor reference8bit, LaunchConfig config) {
        switch (config.mode) {
            case MAX_ONLY:
                return buildMaxModel(reference8bit);
            case MIN_MAX:
                return buildMinMaxModel(reference8bit);
            case PERCENTILE:
                return buildPercentileModel(reference8bit, config.lowPercentile, config.highPercentile);
            case APN:
                return buildAPNModel(reference8bit);
            case Z_SCORE:
                return buildZScoreModel(reference8bit);
            case MEAN:
                return buildMeanModel(reference8bit);
            case VECTOR:
                return buildVectorModel(reference8bit);
            default:
                return buildAPNModel(reference8bit);
        }
    }

    private ComputationModel buildMaxModel(ByteProcessor ip) {
        int maxVal = getOccupiedMax(ip);
        if (maxVal <= 0) maxVal = 255;

        ComputationModel model = new ComputationModel();
        model.mode = NormalizationMode.MAX_ONLY;
        model.outputType = OutputType.BYTE_LINEAR;
        model.methodName = "Max";
        model.xMin = 0;
        model.xMax = maxVal;
        model.pMin = 0.0;
        model.pMax = getRank(ip, maxVal);
        model.summary = String.format("[%s] Standard max normalization on the 8-bit working image: x' = x / %d × 255.",
                PLUGIN_NAME, maxVal);
        return model;
    }

    private ComputationModel buildMinMaxModel(ByteProcessor ip) {
        int minVal = getOccupiedMin(ip);
        int maxVal = getOccupiedMax(ip);
        if (maxVal <= minVal) {
            minVal = 0;
            maxVal = 255;
        }

        ComputationModel model = new ComputationModel();
        model.mode = NormalizationMode.MIN_MAX;
        model.outputType = OutputType.BYTE_LINEAR;
        model.methodName = "MinMax";
        model.xMin = minVal;
        model.xMax = maxVal;
        model.pMin = getRank(ip, minVal);
        model.pMax = getRank(ip, maxVal);
        model.summary = String.format("[%s] Min-Max normalization on the 8-bit working image: x' = (x - %d) / (%d - %d) × 255.",
                PLUGIN_NAME, minVal, maxVal, minVal);
        return model;
    }

    private ComputationModel buildPercentileModel(ByteProcessor ip, double lowP, double highP) {
        double xMin = getPercentile(ip, lowP);
        double xMax = getPercentile(ip, highP);
        if (highP <= lowP || xMax <= xMin) {
            IJ.error(PLUGIN_NAME, "Invalid percentile range. Falling back to Min-Max normalization.");
            return buildMinMaxModel(ip);
        }

        ComputationModel model = new ComputationModel();
        model.mode = NormalizationMode.PERCENTILE;
        model.outputType = OutputType.BYTE_LINEAR;
        model.methodName = String.format("Percentile_%.2f_%.2f", lowP, highP);
        model.xMin = xMin;
        model.xMax = xMax;
        model.pMin = getRank(ip, xMin);
        model.pMax = getRank(ip, xMax);
        model.summary = String.format("[%s] Manual percentile normalization on the 8-bit working image using lower = %.2f%% and upper = %.2f%%.",
                PLUGIN_NAME, lowP, highP);
        return model;
    }

    private ComputationModel buildAPNModel(ByteProcessor ip) {
        FloatProcessor workFloat = (FloatProcessor) ip.convertToFloat();
        byte[] refPixels = (byte[]) ip.getPixels();
        int width = workFloat.getWidth();
        int height = workFloat.getHeight();

        List<Block> allBlocks = new ArrayList<Block>();
        List<Float> allFreqStds = new ArrayList<Float>();
        List<Float> allGrayStds = new ArrayList<Float>();

        for (int y = 0; y <= height - WINDOW_SIZE; y += STEP_SIZE) {
            for (int x = 0; x <= width - WINDOW_SIZE; x += STEP_SIZE) {
                workFloat.setRoi(x, y, WINDOW_SIZE, WINDOW_SIZE);
                ImageProcessor blockIp = workFloat.crop();
                float freqStd = calculateFreqStd(blockIp);
                float grayStd = (float) blockIp.getStatistics().stdDev;
                float kurt = calculateKurtosis(blockIp);
                allBlocks.add(new Block(x, y, freqStd, grayStd, kurt));
                allFreqStds.add(freqStd);
                allGrayStds.add(grayStd);
            }
        }
        workFloat.resetRoi();

        if (allBlocks.isEmpty()) {
            IJ.error(PLUGIN_NAME, "Image is too small for APN analysis. Falling back to Min-Max normalization.");
            return buildMinMaxModel(ip);
        }

        double minFreq = Collections.min(allFreqStds);
        double stdFreq = getStdDev(allFreqStds);
        double freqThreshold = minFreq + STD_MULT * stdFreq;

        double minGray = Collections.min(allGrayStds);
        double stdGray = getStdDev(allGrayStds);
        double grayThreshold = minGray + STD_MULT * stdGray;

        List<Block> potentialBg = new ArrayList<Block>();
        List<Block> potentialSignal = new ArrayList<Block>();
        for (Block b : allBlocks) {
            if (b.freqStd < freqThreshold && b.grayStd < grayThreshold) {
                potentialBg.add(b);
            } else {
                potentialSignal.add(b);
            }
        }

        List<Block> finalBgBlocks = new ArrayList<Block>();
        if (!potentialBg.isEmpty()) {
            for (Block b : potentialBg) {
                b.bgScore = 0.5 * safeRatio(b.freqStd, freqThreshold) + 0.5 * safeRatio(b.grayStd, grayThreshold);
            }
            Collections.sort(potentialBg);
            int count = Math.min(MAX_BG_BLOCKS, potentialBg.size());
            for (int i = 0; i < count; i++) {
                finalBgBlocks.add(potentialBg.get(i));
            }
        }

        List<Block> finalSignalBlocks = new ArrayList<Block>();
        if (!potentialSignal.isEmpty()) {
            List<Float> signalKurts = new ArrayList<Float>();
            for (Block b : potentialSignal) signalKurts.add(b.kurtosis);
            double medianKurt = getMedian(signalKurts);
            double iqrKurt = getIQR(signalKurts);
            double kurtThreshold = medianKurt + KURT_MULT * iqrKurt;
            for (Block b : potentialSignal) {
                if (b.kurtosis < kurtThreshold) finalSignalBlocks.add(b);
            }
        }

        double xMin;
        if (!finalBgBlocks.isEmpty()) {
            double sum = 0;
            long count = 0;
            for (Block b : finalBgBlocks) {
                for (int by = 0; by < WINDOW_SIZE; by++) {
                    int offset = (b.y + by) * width + b.x;
                    for (int bx = 0; bx < WINDOW_SIZE; bx++) {
                        sum += (refPixels[offset + bx] & 0xff);
                        count++;
                    }
                }
            }
            xMin = sum / Math.max(1, count);
        } else {
            xMin = getPercentile(ip, 1.0);
        }

        double xMax;
        if (!finalSignalBlocks.isEmpty()) {
            int estimatedSize = finalSignalBlocks.size() * WINDOW_SIZE * WINDOW_SIZE;
            float[] signalPixels = new float[estimatedSize];
            int ptr = 0;
            for (Block b : finalSignalBlocks) {
                for (int by = 0; by < WINDOW_SIZE; by++) {
                    int offset = (b.y + by) * width + b.x;
                    for (int bx = 0; bx < WINDOW_SIZE; bx++) {
                        signalPixels[ptr++] = (refPixels[offset + bx] & 0xff);
                    }
                }
            }
            xMax = getPercentileFromFloatArray(signalPixels, ptr, SIGNAL_PERCENTILE);
        } else {
            xMax = getPercentile(ip, SIGNAL_PERCENTILE);
        }

        if (xMax <= xMin) {
            xMin = getPercentile(ip, 1.0);
            xMax = getPercentile(ip, 99.0);
        }

        ComputationModel model = new ComputationModel();
        model.mode = NormalizationMode.APN;
        model.outputType = OutputType.BYTE_LINEAR;
        model.methodName = "APN";
        model.xMin = xMin;
        model.xMax = xMax;
        model.pMin = getRank(ip, xMin);
        model.pMax = getRank(ip, xMax);
        model.summary = String.format("[%s] APN selected %d background blocks and %d signal blocks after frequency / grayscale / kurtosis screening.",
                PLUGIN_NAME, finalBgBlocks.size(), finalSignalBlocks.size());
        return model;
    }

    private ComputationModel buildZScoreModel(ByteProcessor ip) {
        double mean = computeMean(ip);
        double std = computeStd(ip, mean);
        if (std <= 0) std = 1.0;

        ComputationModel model = new ComputationModel();
        model.mode = NormalizationMode.Z_SCORE;
        model.outputType = OutputType.FLOAT_ZSCORE;
        model.methodName = "ZScore";
        model.mean = mean;
        model.std = std;
        model.summary = String.format("[%s] Z-Score standardization on the 8-bit working image: z = (x - %.4f) / %.4f. Output is kept as 32-bit float.",
                PLUGIN_NAME, mean, std);
        return model;
    }

    private ComputationModel buildMeanModel(ByteProcessor ip) {
        double mean = computeMean(ip);
        double minVal = getOccupiedMin(ip);
        double maxVal = getOccupiedMax(ip);
        if (maxVal <= minVal) {
            minVal = 0;
            maxVal = 255;
        }

        ComputationModel model = new ComputationModel();
        model.mode = NormalizationMode.MEAN;
        model.outputType = OutputType.FLOAT_MEAN;
        model.methodName = "MeanNorm";
        model.mean = mean;
        model.dataMin = minVal;
        model.dataMax = maxVal;
        model.summary = String.format("[%s] Mean normalization on the 8-bit working image: y = (x - %.4f) / (%.4f - %.4f). Output is kept as 32-bit float.",
                PLUGIN_NAME, mean, maxVal, minVal);
        return model;
    }

    private ComputationModel buildVectorModel(ByteProcessor ip) {
        double l2 = computeL2Norm(ip);
        if (l2 <= 0) l2 = 1.0;

        ComputationModel model = new ComputationModel();
        model.mode = NormalizationMode.VECTOR;
        model.outputType = OutputType.FLOAT_VECTOR;
        model.methodName = "VectorNorm";
        model.l2Norm = l2;
        model.summary = String.format("[%s] Vector normalization on the 8-bit working image: y = x / ||x||₂, with ||x||₂ = %.6f. Output is kept as 32-bit float.",
                PLUGIN_NAME, l2);
        return model;
    }

    private ByteProcessor applyLinearNormalization(ByteProcessor source, double xMin, double xMax) {
        int width = source.getWidth();
        int height = source.getHeight();
        byte[] src = (byte[]) source.getPixels();
        ByteProcessor out = new ByteProcessor(width, height);
        byte[] dst = (byte[]) out.getPixels();

        double denom = xMax - xMin;
        if (denom <= 0) denom = 1.0;

        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xff;
            double n = (v - xMin) / denom;
            if (n < 0) n = 0;
            if (n > 1) n = 1;
            dst[i] = (byte) Math.round(n * 255.0);
        }
        return out;
    }

    private FloatProcessor applyZScore(ByteProcessor source, double mean, double std) {
        int width = source.getWidth();
        int height = source.getHeight();
        byte[] src = (byte[]) source.getPixels();
        float[] dst = new float[src.length];
        double denom = std <= 0 ? 1.0 : std;

        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xff;
            dst[i] = (float) ((v - mean) / denom);
        }
        return new FloatProcessor(width, height, dst);
    }

    private FloatProcessor applyMeanNormalization(ByteProcessor source, double mean, double dataMin, double dataMax) {
        int width = source.getWidth();
        int height = source.getHeight();
        byte[] src = (byte[]) source.getPixels();
        float[] dst = new float[src.length];
        double denom = dataMax - dataMin;
        if (denom <= 0) denom = 1.0;

        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xff;
            dst[i] = (float) ((v - mean) / denom);
        }
        return new FloatProcessor(width, height, dst);
    }

    private FloatProcessor applyVectorNormalization(ByteProcessor source, double l2Norm) {
        int width = source.getWidth();
        int height = source.getHeight();
        byte[] src = (byte[]) source.getPixels();
        float[] dst = new float[src.length];
        double denom = l2Norm <= 0 ? 1.0 : l2Norm;

        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xff;
            dst[i] = (float) (v / denom);
        }
        return new FloatProcessor(width, height, dst);
    }

    private WorkingScale buildWorkingScale(ImageProcessor src) {
        WorkingScale scale = new WorkingScale();
        int bitDepth = src.getBitDepth();

        if (bitDepth == 8) {
            scale.sourceMin = 0;
            scale.sourceMax = 255;
            scale.summary = "Input is already 8-bit; the working image is used directly.";
        } else if (bitDepth == 16) {
            scale.sourceMin = 0;
            scale.sourceMax = 65535;
            scale.summary = "16-bit input is first mapped to 8-bit with the fixed range [0, 65535].";
        } else {
            ImageStatistics stats = src.getStatistics();
            scale.sourceMin = stats.min;
            scale.sourceMax = stats.max;
            if (scale.sourceMax <= scale.sourceMin) scale.sourceMax = scale.sourceMin + 1.0;
            scale.summary = String.format("32-bit input is first mapped to 8-bit with the reference range [%.6f, %.6f].",
                    scale.sourceMin, scale.sourceMax);
        }
        return scale;
    }

    private ByteProcessor toWorking8Bit(ImageProcessor src, WorkingScale scale) {
        int width = src.getWidth();
        int height = src.getHeight();
        ByteProcessor out = new ByteProcessor(width, height);
        byte[] dst = (byte[]) out.getPixels();
        int bitDepth = src.getBitDepth();

        if (bitDepth == 8) {
            byte[] srcPixels = (byte[]) src.getPixels();
            System.arraycopy(srcPixels, 0, dst, 0, srcPixels.length);
            return out;
        }

        double denom = scale.sourceMax - scale.sourceMin;
        if (denom <= 0) denom = 1.0;

        for (int i = 0; i < src.getPixelCount(); i++) {
            double raw = src.getf(i);
            double v = (raw - scale.sourceMin) / denom;
            if (v < 0) v = 0;
            if (v > 1) v = 1;
            dst[i] = (byte) Math.round(v * 255.0);
        }
        return out;
    }

    private LaunchConfig showSRWikiDialog() {
        final JDialog dialog = new JDialog(IJ.getInstance(), PLUGIN_NAME + " Launcher", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(13, 16, 24));
        root.setBorder(new LineBorder(new Color(54, 65, 84), 1, true));

        JButton helpButton = new JButton("Help / GitHub");
        styleHeaderButton(helpButton);

        HeaderPanel headerPanel = new HeaderPanel(helpButton);
        headerPanel.setPreferredSize(new Dimension(980, 170));
        root.add(headerPanel, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(18, 0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel leftPanel = buildSidebar();
        content.add(leftPanel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));

        JPanel detailsCard = createCard();
        detailsCard.setLayout(new BorderLayout(0, 12));
        JLabel detailsTitle = createCardTitle("Method details");
        JLabel detailsBody = new JLabel();
        detailsBody.setVerticalAlignment(SwingConstants.TOP);
        detailsBody.setForeground(new Color(224, 231, 239));
        detailsBody.setFont(new Font("SansSerif", Font.PLAIN, 13));
        detailsCard.add(detailsTitle, BorderLayout.NORTH);
        detailsCard.add(detailsBody, BorderLayout.CENTER);

        JPanel parametersCard = createCard();
        parametersCard.setLayout(new BorderLayout(0, 12));
        JLabel parametersTitle = createCardTitle("Parameters");
        parametersCard.add(parametersTitle, BorderLayout.NORTH);

        JPanel parametersGrid = new JPanel(new GridBagLayout());
        parametersGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lowLabel = createFieldLabel("Lower percentile (%)");
        JTextField lowField = createTextField("1.0");
        JLabel highLabel = createFieldLabel("Upper percentile (%)");
        JTextField highField = createTextField("99.8");
        JLabel paramNote = createSoftNote("Used only for Percentile normalization. Values below the lower percentile are clipped to 0, and values above the upper percentile are clipped to 255.");

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        parametersGrid.add(lowLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        parametersGrid.add(lowField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        parametersGrid.add(highLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        parametersGrid.add(highField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        parametersGrid.add(paramNote, gbc);
        parametersCard.add(parametersGrid, BorderLayout.CENTER);

        JPanel optionsCard = createCard();
        optionsCard.setLayout(new BorderLayout(0, 12));
        JLabel optionsTitle = createCardTitle("Execution options");
        JPanel optionsBody = new JPanel();
        optionsBody.setOpaque(false);
        optionsBody.setLayout(new BoxLayout(optionsBody, BoxLayout.Y_AXIS));
        JCheckBox showHistogram = new JCheckBox("Show histogram for single-image runs (linear 8-bit methods only)", true);
        showHistogram.setOpaque(false);
        showHistogram.setForeground(new Color(231, 236, 244));
        showHistogram.setFont(new Font("SansSerif", Font.PLAIN, 13));
        showHistogram.setFocusPainted(false);
        optionsBody.add(showHistogram);
        optionsBody.add(Box.createVerticalStrut(12));
        optionsBody.add(createSoftNote("All input images are first converted to an 8-bit working image, then the selected method is applied."));
        optionsBody.add(Box.createVerticalStrut(10));
        optionsBody.add(createSoftNote("For stacks, slice 1 is used as the reference slice to determine normalization or standardization statistics."));
        optionsBody.add(Box.createVerticalStrut(10));
        optionsBody.add(createSoftNote("Z-Score, Mean normalization, and Vector normalization are exported as 32-bit float images to preserve their mathematical definitions."));
        optionsCard.add(optionsTitle, BorderLayout.NORTH);
        optionsCard.add(optionsBody, BorderLayout.CENTER);

        rightPanel.add(detailsCard);
        rightPanel.add(Box.createVerticalStrut(14));
        rightPanel.add(parametersCard);
        rightPanel.add(Box.createVerticalStrut(14));
        rightPanel.add(optionsCard);

        JScrollPane rightScrollPane = createScrollPane(rightPanel);
        rightScrollPane.setPreferredSize(new Dimension(840, 470));
        content.add(rightScrollPane, BorderLayout.CENTER);
        root.add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 18, 18, 18));
        JLabel footerLabel = new JLabel("Bio-oriented microscopy styling is now built in. To use a custom banner image, add a resource named srwiki_bio_header.png or provide the image for replacement.");
        footerLabel.setForeground(new Color(130, 146, 166));
        footerLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton cancelButton = new JButton("Cancel");
        JButton runButton = new JButton("Run SRWiki");
        styleActionButton(cancelButton, new Color(82, 88, 100), 120, 38);
        styleActionButton(runButton, new Color(52, 186, 133), 150, 38);

        JPanel footerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footerButtons.setOpaque(false);
        footerButtons.add(cancelButton);
        footerButtons.add(runButton);

        footer.add(footerButtons, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        JRadioButton rbMax = (JRadioButton) leftPanel.getClientProperty("rbMax");
        JRadioButton rbMinMax = (JRadioButton) leftPanel.getClientProperty("rbMinMax");
        JRadioButton rbPercentile = (JRadioButton) leftPanel.getClientProperty("rbPercentile");
        JRadioButton rbAPN = (JRadioButton) leftPanel.getClientProperty("rbAPN");
        JRadioButton rbZScore = (JRadioButton) leftPanel.getClientProperty("rbZScore");
        JRadioButton rbMean = (JRadioButton) leftPanel.getClientProperty("rbMean");
        JRadioButton rbVector = (JRadioButton) leftPanel.getClientProperty("rbVector");

        final LaunchConfig[] result = new LaunchConfig[1];

        Runnable refreshDetails = () -> {
            NormalizationMode mode = getSelectedMode(rbMax, rbMinMax, rbPercentile, rbAPN, rbZScore, rbMean, rbVector);
            detailsBody.setText(buildMethodDetailHtml(mode));
            boolean percentileMode = (mode == NormalizationMode.PERCENTILE);
            parametersCard.setVisible(percentileMode);
            lowLabel.setEnabled(percentileMode);
            highLabel.setEnabled(percentileMode);
            lowField.setEnabled(percentileMode);
            highField.setEnabled(percentileMode);
            rightPanel.revalidate();
            rightPanel.repaint();
        };

        rbMax.addActionListener(e -> refreshDetails.run());
        rbMinMax.addActionListener(e -> refreshDetails.run());
        rbPercentile.addActionListener(e -> refreshDetails.run());
        rbAPN.addActionListener(e -> refreshDetails.run());
        rbZScore.addActionListener(e -> refreshDetails.run());
        rbMean.addActionListener(e -> refreshDetails.run());
        rbVector.addActionListener(e -> refreshDetails.run());

        helpButton.addActionListener(e -> openHelpPage());
        cancelButton.addActionListener(e -> dialog.dispose());
        runButton.addActionListener(e -> {
            LaunchConfig cfg = new LaunchConfig();
            cfg.mode = getSelectedMode(rbMax, rbMinMax, rbPercentile, rbAPN, rbZScore, rbMean, rbVector);
            cfg.showHistogram = showHistogram.isSelected();

            try {
                cfg.lowPercentile = Double.parseDouble(lowField.getText().trim());
                cfg.highPercentile = Double.parseDouble(highField.getText().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Percentile values must be valid numbers.",
                        PLUGIN_NAME,
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (cfg.mode == NormalizationMode.PERCENTILE) {
                if (cfg.lowPercentile < 0 || cfg.lowPercentile > 100 || cfg.highPercentile < 0 || cfg.highPercentile > 100 || cfg.highPercentile <= cfg.lowPercentile) {
                    JOptionPane.showMessageDialog(dialog,
                            "Please set a valid percentile range such that 0 ≤ lower < upper ≤ 100.",
                            PLUGIN_NAME,
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            result[0] = cfg;
            dialog.dispose();
        });

        refreshDetails.run();
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(IJ.getInstance());
        dialog.setVisible(true);
        return result[0];
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(18, 22, 31));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(44, 53, 70), 1, true),
                new EmptyBorder(16, 16, 16, 16)));
        sidebar.setPreferredSize(new Dimension(250, 430));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Methods");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(title);

        sidebar.add(Box.createVerticalStrut(16));


        ButtonGroup group = new ButtonGroup();
        JRadioButton rbMax = createSidebarRadio("Max normalization");
        JRadioButton rbMinMax = createSidebarRadio("Min-Max normalization");
        JRadioButton rbPercentile = createSidebarRadio("Percentile normalization");
        JRadioButton rbAPN = createSidebarRadio("APN normalization");
        JRadioButton rbZScore = createSidebarRadio("Z-Score standardization");
        JRadioButton rbMean = createSidebarRadio("Mean normalization");
        JRadioButton rbVector = createSidebarRadio("Vector normalization");
        rbAPN.setSelected(true);

        group.add(rbMax);
        group.add(rbMinMax);
        group.add(rbPercentile);
        group.add(rbAPN);
        group.add(rbZScore);
        group.add(rbMean);
        group.add(rbVector);

        sidebar.add(createSidebarItem(rbMax));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarItem(rbMinMax));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarItem(rbPercentile));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarItem(rbAPN));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarItem(rbZScore));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarItem(rbMean));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(createSidebarItem(rbVector));
        sidebar.add(Box.createVerticalGlue());

        sidebar.putClientProperty("rbMax", rbMax);
        sidebar.putClientProperty("rbMinMax", rbMinMax);
        sidebar.putClientProperty("rbPercentile", rbPercentile);
        sidebar.putClientProperty("rbAPN", rbAPN);
        sidebar.putClientProperty("rbZScore", rbZScore);
        sidebar.putClientProperty("rbMean", rbMean);
        sidebar.putClientProperty("rbVector", rbVector);
        return sidebar;
    }

    private JPanel createSidebarItem(JRadioButton radio) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        panel.setBackground(new Color(28, 34, 46));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(56, 67, 88), 1, true),
                new EmptyBorder(6, 8, 6, 8)));
        panel.add(radio, BorderLayout.CENTER);
        return panel;
    }

    private JRadioButton createSidebarRadio(String text) {
        JRadioButton rb = new JRadioButton(text);
        rb.setOpaque(false);
        rb.setForeground(new Color(236, 240, 246));
        rb.setFont(new Font("SansSerif", Font.BOLD, 13));
        rb.setFocusPainted(false);
        rb.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return rb;
    }

    private JPanel createCard() {
        JPanel card = new JPanel();
        card.setBackground(new Color(19, 24, 34));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(50, 62, 82), 1, true),
                new EmptyBorder(16, 16, 16, 16)));
        card.setAlignmentX(0f);
        return card;
    }

    private JLabel createCardTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.BOLD, 18));
        return label;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(224, 230, 239));
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return label;
    }

    private JTextField createTextField(String value) {
        JTextField field = new JTextField(value);
        field.setBackground(new Color(11, 15, 23));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setFont(new Font("SansSerif", Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(70, 84, 108), 1, true),
                new EmptyBorder(7, 8, 7, 8)));
        return field;
    }

    private JLabel createSoftNote(String text) {
        JLabel label = new JLabel("<html><div style='width:620px; line-height:1.45;'>" + text + "</div></html>");
        label.setForeground(new Color(154, 168, 186));
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return label;
    }

    private JLabel createSidebarNote(String text) {
        JLabel label = new JLabel("<html><div style='width:180px; line-height:1.45;'>" + text + "</div></html>");
        label.setForeground(new Color(154, 168, 186));
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return label;
    }

    private JScrollPane createScrollPane(JPanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        scrollPane.getVerticalScrollBar().setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private void styleActionButton(JButton button, Color bg, int w, int h) {
        button.setPreferredSize(new Dimension(w, h));
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setBorder(new EmptyBorder(8, 14, 8, 14));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private void styleHeaderButton(JButton button) {
        styleActionButton(button, new Color(55, 140, 255), 140, 34);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
    }

    private NormalizationMode getSelectedMode(JRadioButton rbMax, JRadioButton rbMinMax, JRadioButton rbPercentile,
                                              JRadioButton rbAPN, JRadioButton rbZScore, JRadioButton rbMean,
                                              JRadioButton rbVector) {
        if (rbMax.isSelected()) return NormalizationMode.MAX_ONLY;
        if (rbMinMax.isSelected()) return NormalizationMode.MIN_MAX;
        if (rbPercentile.isSelected()) return NormalizationMode.PERCENTILE;
        if (rbZScore.isSelected()) return NormalizationMode.Z_SCORE;
        if (rbMean.isSelected()) return NormalizationMode.MEAN;
        if (rbVector.isSelected()) return NormalizationMode.VECTOR;
        return NormalizationMode.APN;
    }

    private String buildMethodDetailHtml(NormalizationMode mode) {
        if (mode == NormalizationMode.MAX_ONLY) {
            return "<html><div style='width:650px; line-height:1.5;'>"
                    + "<b>Max normalization</b><br><br>"
                    + "<b>Formula:</b> x' = x / max(x) × 255<br><br>"
                    + "<b>Input space:</b> applied after conversion to the 8-bit working image.<br>"
                    + "<b>Output type:</b> 8-bit.<br>"
                    + "<b>Interpretation:</b> the darkest level is fixed at 0 and the brightest occupied value becomes 255. This is the simplest global scaling strategy."
                    + "</div></html>";
        }
        if (mode == NormalizationMode.MIN_MAX) {
            return "<html><div style='width:650px; line-height:1.5;'>"
                    + "<b>Min-Max normalization</b><br><br>"
                    + "<b>Formula:</b> x' = (x - xmin) / (xmax - xmin) × 255<br><br>"
                    + "<b>Input space:</b> applied after conversion to the 8-bit working image.<br>"
                    + "<b>Output type:</b> 8-bit.<br>"
                    + "<b>Interpretation:</b> the occupied intensity interval is linearly stretched to the full display range. It gives strong contrast enhancement but can be sensitive to outliers."
                    + "</div></html>";
        }
        if (mode == NormalizationMode.PERCENTILE) {
            return "<html><div style='width:650px; line-height:1.5;'>"
                    + "<b>Percentile normalization</b><br><br>"
                    + "<b>Formula:</b> x' = clip((x - Xplow) / (Xphigh - Xplow), 0, 1) × 255<br><br>"
                    + "<b>Input space:</b> applied after conversion to the 8-bit working image.<br>"
                    + "<b>Output type:</b> 8-bit.<br>"
                    + "<b>Interpretation:</b> the lower and upper percentiles are set manually. This is useful when the user wants explicit control over clipping and contrast compression."
                    + "</div></html>";
        }
        if (mode == NormalizationMode.APN) {
            return "<html><div style='width:650px; line-height:1.5;'>"
                    + "<b>APN normalization</b><br><br>"
                    + "<b>Core idea:</b> Adaptive Percentage Normalization estimates background and signal bounds from local block statistics in the frequency and grayscale domains, followed by kurtosis-based signal filtering.<br><br>"
                    + "<b>Input space:</b> applied after conversion to the 8-bit working image.<br>"
                    + "<b>Output type:</b> 8-bit.<br>"
                    + "<b>Internal settings:</b> window = " + WINDOW_SIZE + ", step = " + STEP_SIZE + ", stdMult = " + STD_MULT + ", kurtMult = " + KURT_MULT + ".<br>"
                    + "<b>Interpretation:</b> APN is designed for low-SNR fluorescence-like microscopy data where naive global min-max scaling may over-amplify background or suppress weak structure."
                    + "</div></html>";
        }
        if (mode == NormalizationMode.Z_SCORE) {
            return "<html><div style='width:650px; line-height:1.5;'>"
                    + "<b>Z-Score standardization</b><br><br>"
                    + "<b>Formula:</b> z = (x - μ) / σ<br><br>"
                    + "<b>Input space:</b> applied after conversion to the 8-bit working image.<br>"
                    + "<b>Output type:</b> 32-bit float.<br>"
                    + "<b>Interpretation:</b> the result is centered at zero with unit standard deviation. This method is useful when relative deviation from the global mean is more important than a fixed display range."
                    + "</div></html>";
        }
        if (mode == NormalizationMode.MEAN) {
            return "<html><div style='width:650px; line-height:1.5;'>"
                    + "<b>Mean normalization</b><br><br>"
                    + "<b>Formula:</b> y = (x - mean(x)) / (max(x) - min(x))<br><br>"
                    + "<b>Input space:</b> applied after conversion to the 8-bit working image.<br>"
                    + "<b>Output type:</b> 32-bit float.<br>"
                    + "<b>Interpretation:</b> the intensities are centered relative to the global mean and scaled by the occupied range. This reduces global offset while keeping a bounded linear denominator."
                    + "</div></html>";
        }
        return "<html><div style='width:650px; line-height:1.5;'>"
                + "<b>Vector normalization</b><br><br>"
                + "<b>Formula:</b> y = x / ||x||₂<br><br>"
                + "<b>Input space:</b> applied after conversion to the 8-bit working image.<br>"
                + "<b>Output type:</b> 32-bit float.<br>"
                + "<b>Interpretation:</b> the whole image is treated as a vector and scaled to unit L2 norm. This is useful when the overall energy of the image should be normalized rather than its dynamic range."
                + "</div></html>";
    }

    private void openHelpPage() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(HELP_URL));
            } else {
                IJ.showMessage(PLUGIN_NAME, "Please open this page manually:" + HELP_URL);
            }
        } catch (Exception ex) {
            IJ.showMessage(PLUGIN_NAME, "Could not open GitHub automatically.Please open:" + HELP_URL);
        }
    }

    private class HeaderPanel extends JPanel {
        private final JButton helpButton;
        private BufferedImage backgroundImage;

        HeaderPanel(JButton helpButton) {
            this.helpButton = helpButton;
            setLayout(new BorderLayout());
            setOpaque(true);
            setBorder(new EmptyBorder(18, 22, 18, 22));
            loadOptionalBackground();
            buildForeground();
        }

        private void buildForeground() {
            JPanel topBar = new JPanel(new BorderLayout());
            topBar.setOpaque(false);
            JLabel identity = new JLabel(PLUGIN_NAME + "  " + PLUGIN_VERSION);
            identity.setForeground(new Color(230, 245, 244));
            identity.setFont(new Font("SansSerif", Font.BOLD, 14));
            topBar.add(identity, BorderLayout.WEST);
            topBar.add(helpButton, BorderLayout.EAST);
            add(topBar, BorderLayout.NORTH);

            JPanel leftWrap = new JPanel();
            leftWrap.setOpaque(false);
            leftWrap.setLayout(new BoxLayout(leftWrap, BoxLayout.Y_AXIS));

            JLabel title = new JLabel("SRWiki");
            title.setForeground(Color.WHITE);
            title.setFont(new Font("SansSerif", Font.BOLD, 44));

            JLabel subtitle = new JLabel("Microscopy-oriented normalization and standardization suite");
            subtitle.setForeground(new Color(218, 235, 236));
            subtitle.setFont(new Font("SansSerif", Font.PLAIN, 18));

            JLabel desc = new JLabel("Bio-style launcher | Max · Min-Max · Percentile · APN · Z-Score · Mean · Vector");
            desc.setForeground(new Color(171, 210, 214));
            desc.setFont(new Font("SansSerif", Font.PLAIN, 13));

            leftWrap.add(Box.createVerticalGlue());
            leftWrap.add(title);
            leftWrap.add(Box.createVerticalStrut(5));
            leftWrap.add(subtitle);
            leftWrap.add(Box.createVerticalStrut(8));
            leftWrap.add(desc);
            leftWrap.add(Box.createVerticalGlue());
            add(leftWrap, BorderLayout.WEST);
        }

        private void loadOptionalBackground() {
            try {
                InputStream is = getClass().getResourceAsStream(HEADER_BG_RESOURCE);
                if (is != null) {
                    backgroundImage = ImageIO.read(is);
                }
            } catch (Exception ignored) {
                backgroundImage = null;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            if (backgroundImage != null) {
                g2.drawImage(backgroundImage, 0, 0, w, h, null);
                g2.setColor(new Color(5, 15, 24, 150));
                g2.fillRect(0, 0, w, h);
            } else {
                GradientPaint gp = new GradientPaint(0, 0, new Color(6, 20, 38), w, h, new Color(20, 67, 66));
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);

                g2.setColor(new Color(255, 255, 255, 10));
                for (int i = -h; i < w; i += 28) {
                    g2.drawLine(i, 0, i + h, h);
                }

                g2.setColor(new Color(88, 239, 189, 26));
                g2.fill(new Ellipse2D.Double(w * 0.68, h * 0.15, 230, 110));
                g2.fill(new Ellipse2D.Double(w * 0.75, h * 0.48, 160, 75));
                g2.setColor(new Color(120, 185, 255, 22));
                g2.fill(new Ellipse2D.Double(w * 0.58, h * 0.30, 150, 150));

                g2.setStroke(new BasicStroke(3f));
                g2.setColor(new Color(90, 219, 255, 105));
                Path2D wave1 = new Path2D.Double();
                wave1.moveTo(0, h * 0.72);
                wave1.curveTo(w * 0.18, h * 0.43, w * 0.42, h * 0.94, w * 0.62, h * 0.63);
                wave1.curveTo(w * 0.78, h * 0.38, w * 0.90, h * 0.77, w, h * 0.47);
                g2.draw(wave1);

                g2.setColor(new Color(71, 236, 161, 92));
                Path2D wave2 = new Path2D.Double();
                wave2.moveTo(0, h * 0.84);
                wave2.curveTo(w * 0.20, h * 1.02, w * 0.47, h * 0.28, w * 0.69, h * 0.76);
                wave2.curveTo(w * 0.85, h * 0.95, w * 0.94, h * 0.38, w, h * 0.61);
                g2.draw(wave2);

                g2.setColor(new Color(255, 255, 255, 18));
                g2.setStroke(new BasicStroke(1.1f));
                for (int i = 0; i < 6; i++) {
                    int cx = (int) (w * (0.60 + i * 0.06));
                    int cy = (int) (h * (0.20 + (i % 2) * 0.18));
                    g2.drawOval(cx, cy, 30, 30);
                    g2.drawLine(cx + 15, cy + 15, cx + 52, cy + 8);
                }
            }
        }
    }

    private void logRunInfo(ComputationModel model, WorkingScale scale, boolean isStack) {
        IJ.log("============================================================");
        IJ.log(PLUGIN_NAME + " " + PLUGIN_VERSION);
        IJ.log("Input: " + imp.getTitle());
        IJ.log("Mode: " + model.methodName + (isStack ? " (stack reference = slice 1)" : " (single image)"));
        IJ.log("8-bit preparation: " + scale.summary);
        if (model.outputType == OutputType.BYTE_LINEAR) {
            IJ.log(String.format("Linear limits on 8-bit working image: Xmin = %.2f (Pmin = %.2f%%), Xmax = %.2f (Pmax = %.2f%%)",
                    model.xMin, model.pMin, model.xMax, model.pMax));
        }
        IJ.log(model.summary);
    }

    private void drawHistogram(ByteProcessor ip, ComputationModel model) {
        int[] hist = ip.getHistogram();
        int nBins = 256;
        double total = ip.getWidth() * ip.getHeight();
        double[] x = new double[nBins];
        double[] y = new double[nBins];
        double maxDensity = 0;

        for (int i = 0; i < nBins; i++) {
            x[i] = i;
            y[i] = hist[i] / total;
            if (y[i] > maxDensity) maxDensity = y[i];
        }

        Plot plot = new Plot(PLUGIN_NAME + " - Working Image Histogram", "Pixel value (8-bit)", "Probability");
        plot.setColor(new Color(214, 220, 231));
        plot.add("bar", x, y);
        double yLimit = Math.max(0.01, maxDensity * 1.2);
        plot.setLimits(0, 255, 0, yLimit);

        plot.setColor(new Color(255, 116, 95));
        plot.setLineWidth(2);
        plot.drawLine(model.xMin, 0, model.xMin, yLimit);

        plot.setColor(new Color(71, 226, 139));
        plot.setLineWidth(2);
        plot.drawLine(model.xMax, 0, model.xMax, yLimit);

        plot.setColor(Color.WHITE);
        plot.addLabel(0.06, 0.92, "Mode: " + model.methodName);
        plot.addLabel(0.06, 0.86, String.format("Xmin = %.2f   Xmax = %.2f", model.xMin, model.xMax));
        plot.addLabel(0.06, 0.80, String.format("Pmin = %.2f%%   Pmax = %.2f%%", model.pMin, model.pMax));
        plot.show();
    }

    private float calculateFreqStd(ImageProcessor ip) {
        ImageProcessor floatIp = ip.convertToFloat();
        FHT fht = new FHT(floatIp);
        fht.transform();

        float[] h = (float[]) fht.getPixels();
        int w = fht.getWidth();
        int hImg = fht.getHeight();
        float[] magnitudes = new float[w * hImg];

        for (int y = 0; y < hImg; y++) {
            for (int x = 0; x < w; x++) {
                int idx1 = y * w + x;
                int symY = (hImg - y) % hImg;
                int symX = (w - x) % w;
                int idx2 = symY * w + symX;
                float v1 = h[idx1];
                float v2 = h[idx2];
                magnitudes[idx1] = (float) Math.sqrt((v1 * v1 + v2 * v2) / 2.0);
            }
        }
        return (float) getStdDevForPixels(magnitudes);
    }

    private double getStdDevForPixels(float[] pixels) {
        double sum = 0;
        double sumSq = 0;
        int n = pixels.length;
        for (float v : pixels) {
            sum += v;
            sumSq += v * v;
        }
        double mean = sum / n;
        return Math.sqrt((sumSq / n) - (mean * mean));
    }

    private float calculateKurtosis(ImageProcessor ip) {
        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        int n = pixels.length;
        double sum = 0;
        double sumSq = 0;
        for (float v : pixels) {
            sum += v;
            sumSq += v * v;
        }
        double mean = sum / n;
        double std = Math.sqrt((sumSq / n) - (mean * mean));
        if (std == 0) return 0;

        double sum4 = 0;
        for (float v : pixels) {
            sum4 += Math.pow((v - mean) / std, 4);
        }
        return (float) (sum4 / n);
    }

    private double getStdDev(List<Float> list) {
        if (list == null || list.isEmpty()) return 0;
        double sum = 0;
        for (float v : list) sum += v;
        double mean = sum / list.size();
        double sumSq = 0;
        for (float v : list) sumSq += Math.pow(v - mean, 2);
        return Math.sqrt(sumSq / list.size());
    }

    private double getMedian(List<Float> list) {
        if (list == null || list.isEmpty()) return 0;
        Collections.sort(list);
        int n = list.size();
        return (n % 2 == 0) ? (list.get(n / 2 - 1) + list.get(n / 2)) / 2.0 : list.get(n / 2);
    }

    private double getIQR(List<Float> list) {
        if (list == null || list.isEmpty()) return 0;
        Collections.sort(list);
        int n = list.size();
        return list.get((int) (n * 0.75)) - list.get((int) (n * 0.25));
    }

    private int getOccupiedMin(ByteProcessor ip) {
        int[] hist = ip.getHistogram();
        int min = 0;
        while (min < 255 && hist[min] == 0) min++;
        return min;
    }

    private int getOccupiedMax(ByteProcessor ip) {
        int[] hist = ip.getHistogram();
        int max = 255;
        while (max > 0 && hist[max] == 0) max--;
        return max;
    }

    private double computeMean(ByteProcessor ip) {
        byte[] pixels = (byte[]) ip.getPixels();
        double sum = 0;
        for (byte pixel : pixels) sum += (pixel & 0xff);
        return sum / Math.max(1, pixels.length);
    }

    private double computeStd(ByteProcessor ip, double mean) {
        byte[] pixels = (byte[]) ip.getPixels();
        double sumSq = 0;
        for (byte pixel : pixels) {
            double v = (pixel & 0xff) - mean;
            sumSq += v * v;
        }
        return Math.sqrt(sumSq / Math.max(1, pixels.length));
    }

    private double computeL2Norm(ByteProcessor ip) {
        byte[] pixels = (byte[]) ip.getPixels();
        double sumSq = 0;
        for (byte pixel : pixels) {
            double v = (pixel & 0xff);
            sumSq += v * v;
        }
        return Math.sqrt(sumSq);
    }

    private double getPercentileFromFloatArray(float[] arr, int count, double p) {
        if (count <= 0) return 0;
        Arrays.sort(arr, 0, count);
        int idx = (int) Math.round((p / 100.0) * (count - 1));
        if (idx < 0) idx = 0;
        if (idx >= count) idx = count - 1;
        return arr[idx];
    }

    private double getPercentile(ByteProcessor ip, double p) {
        int[] hist = ip.getHistogram();
        int total = ip.getPixelCount();
        int target = (int) Math.round(total * p / 100.0);
        int sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += hist[i];
            if (sum >= target) return i;
        }
        return 255;
    }

    private double getRank(ByteProcessor ip, double value) {
        int[] hist = ip.getHistogram();
        int total = ip.getPixelCount();
        int limit = (int) Math.min(255, Math.max(0, Math.round(value)));
        int count = 0;
        for (int i = 0; i <= limit; i++) count += hist[i];
        return (double) count / Math.max(1, total) * 100.0;
    }

    private double safeRatio(double a, double b) {
        if (b == 0) return 0;
        return a / b;
    }
}
