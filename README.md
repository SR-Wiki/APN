# SRWiki Normalization

![Version](https://img.shields.io/badge/Version-3.0-blue.svg)
![Platform](https://img.shields.io/badge/Platform-ImageJ%2FFiji-red.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

## 📖 Overview

**SRWiki Normalization** (formerly the APN Tool) is a comprehensive ImageJ/Fiji plugin designed for the robust preprocessing of biological microscopy images. 

While raw microscopy data often suffers from baseline drift, inconsistent contrast, and hot pixels, this plugin offers 7 distinct normalization and standardization modes to clean and prepare your data. It is highly optimized for downstream tasks such as **unsupervised deep learning denoising**, quantitative analysis, and high-fidelity image visualization.

---

## 📥 Installation

1. **Download**: Obtain the latest `SRWiki_Normalization.jar` file from the releases page.
2. **Install**: Place the `.jar` file into the `plugins/` directory of your ImageJ or Fiji installation.
3. **Restart**: Restart ImageJ/Fiji.
4. **Launch**: Navigate to `Plugins > SRWiki > SRWiki Normalization` in the top menu bar.

---

## 🚀 Normalization Modes & Use Cases

The plugin dynamically maps inputs (8-bit, 16-bit, or 32-bit) into an internal computational space, applies the selected mathematical model, and outputs either an 8-bit or 32-bit Float image.

| Mode | Mathematical Logic | Best Used For... | Output Format |
| :--- | :--- | :--- | :--- |
| **Adaptive (APN)** | Auto-calculates thresholds by isolating true signal from background using local block statistics. | **Raw microscopy images** with hot pixels, baseline drift, or complex noise. | 8-bit |
| **Percentile** | Clips intensities based on user-defined Lower and Upper percentiles. | Images with a known percentage of outlier pixels (e.g., dead camera pixels). | 8-bit |
| **Min-Max** | Linear scaling from the absolute minimum to the absolute maximum. | Clean images requiring a simple dynamic range stretch. | 8-bit |
| **Max Only** | Scales based entirely on the maximum pixel intensity. | Calibrated images where the background is already zeroed out. | 8-bit |
| **Z-Score** | Standardization: $z = \frac{x - \mu}{\sigma}$ | **Deep learning (CNN) inputs** requiring a standard normal distribution (zero mean, unit variance). | 32-bit Float |
| **Mean** | Mean Normalization: $x' = \frac{x - \mu}{\max - \min}$ | Centering data distributions while strictly maintaining relative intensity ranges. | 32-bit Float |
| **Vector** | L2 Normalization: $x' = \frac{x}{\|X\|_2}$ | Standardizing the total "energy" of an image for cross-correlation or feature matching. | 32-bit Float |

---

## ⚙️ Parameters & Stack Processing

When you launch the plugin, the GUI presents several customizable parameters:

### General Settings
* **Lower Percentile (%)**: Determines the lower cutoff threshold. Values below this percentile are clamped. *(Default: 1.0)*
* **Upper Percentile (%)**: Determines the upper cutoff threshold. Values above this percentile are clamped. *(Default: 99.8)*
    * *Note: The Percentile settings are only active when the **Percentile** mode is selected.*
* **Show Histogram**: If checked, the plugin will generate a histogram plot after processing, visualizing the pixel distribution and drawing vertical lines to indicate the calculated lower and upper thresholds.

### 📚 Stack Processing Logic (Crucial for Time-Lapse & Z-Stacks)
If you apply this plugin to an Image Stack, it employs a **Reference-Frame Strategy**:
1. The plugin analyzes **Slice 1** to compute all necessary statistical metrics (e.g., APN thresholds, Mean, Standard Deviation, Max values).
2. It then applies these exact same metrics globally to **all subsequent slices** in the stack.
3. **Why?** This prevents flickering and ensures that relative intensity changes across time or depth are perfectly preserved.

---

## 🛠 Methodology: How APN Works

The **Adaptive (APN)** mode is the flagship feature of this plugin, designed to solve the problem of standard normalizations failing when faced with large, empty, noisy backgrounds.

1. **Block Division**: The image is virtually split into `64x64` pixel blocks.
2. **Feature Extraction**: For each block, the algorithm calculates three metrics:
   * **Intensity Standard Deviation**: To measure local contrast.
   * **Frequency Standard Deviation**: Uses a Fast Hartley Transform (FHT) to measure high-frequency detail.
   * **Kurtosis**: To measure the "tailedness" of the distribution (identifying flat backgrounds vs. sharp structures).
3. **Signal vs. Background Identification**: Blocks with high kurtosis and low frequency SD are flagged as background/noise and excluded.
4. **Threshold Calculation**: The plugin calculates the final minimum and maximum normalization thresholds using *only* the data from the valid signal blocks, entirely ignoring hot pixels and empty space.

---

## 📝 License

This project is licensed under the **MIT License**.
