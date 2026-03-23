# SR-Wiki Normalization

![Version](https://img.shields.io/badge/Version-3.0-blue.svg)
![Platform](https://img.shields.io/badge/Platform-ImageJ%2FFiji-red.svg)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)

## 📖 Overview

**SR-Wiki Normalization** (formerly the APN Tool) is a comprehensive ImageJ/Fiji plugin designed for the robust preprocessing of biological microscopy images. 

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

## ⚙️ Parameters

When you launch the plugin, the GUI presents several customizable parameters:

### General Settings
* **Lower Percentile (%)**: Determines the lower cutoff threshold. Values below this percentile are clamped. *(Default: 1.0)*
* **Upper Percentile (%)**: Determines the upper cutoff threshold. Values above this percentile are clamped. *(Default: 99.8)*
    * *Note: The Percentile settings are only active when the **Percentile** mode is selected.*
* **Show Histogram**: If checked, the plugin will generate a histogram plot after processing, visualizing the pixel distribution and drawing vertical lines to indicate the calculated lower and upper thresholds.

