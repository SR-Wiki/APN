# SR-Wiki Normalization

![Version](https://img.shields.io/badge/Version-3.0-blue.svg)
![Platform](https://img.shields.io/badge/Platform-ImageJ%2FFiji-red.svg)
[![License: ODbL](https://img.shields.io/badge/License-ODbL_1.0-lightgrey.svg)](https://opendatacommons.org/licenses/odbl/)

<p align="center">
  <img src="images/GUI.png" alt="GUI Interface" width="900">
  <br>
  <em>The SR-Wiki Normalization User Interface</em>
</p>

---

## 📖 Overview

**SR-Wiki Normalization** is an ImageJ/Fiji plugin designed for robust preprocessing of biological microscopy images, with a particular emphasis on low signal-to-noise ratio (low-SNR) fluorescence data.

Microscopy images often suffer from baseline drift, uneven background, hot pixels, and weak structural signals that are easily overwhelmed by noise. Conventional global normalization strategies such as Min-Max or Max normalization implicitly assume stable background and well-behaved intensity distributions, which are rarely satisfied in real experimental conditions.

To address this, SR-Wiki integrates a set of seven normalization and standardization methods within a unified interface, centered around the **Adaptive Percentage Normalization (APN)** algorithm. The plugin enables both display-oriented enhancement and analysis-oriented standardization, making it suitable for downstream tasks such as unsupervised deep learning denoising, quantitative measurement, and visualization.

---

## 🚀 Core Concept

The key distinction of this plugin lies in its treatment of intensity normalization as a **data-dependent estimation problem**, rather than a purely global rescaling process.

Traditional normalization methods rely on:
- global minimum and maximum values  
- manually selected percentile thresholds  

In contrast, SR-Wiki introduces APN, which estimates meaningful intensity bounds directly from the image structure itself.

---

## 🔬 Why APN

### Limitations of Conventional Methods

In practical microscopy scenarios:

- A small number of hot pixels can dominate the global maximum  
- Background intensity may vary spatially across the field of view  
- Weak biological structures may occupy only a small fraction of pixels  
- Noise distributions are often non-Gaussian  

Under these conditions:

- **Max normalization** is unstable  
- **Min-Max normalization** amplifies background noise  
- **Percentile normalization** depends heavily on manual parameter selection  

---

### APN: Adaptive Estimation of Signal and Background

APN addresses these issues by introducing a local statistical analysis framework.

The algorithm operates as follows:

1. The image is partitioned into overlapping local blocks  
2. For each block, the following features are computed:
   - frequency-domain standard deviation  
   - grayscale-domain variance  
   - kurtosis (to characterize signal sharpness)  
3. Blocks are classified into:
   - background-like regions  
   - signal-like regions  
4. Robust estimates are computed:
   - **Xmin from background regions**
   - **Xmax from signal regions**  

This process effectively separates structural signal from background fluctuations without requiring manual thresholds.

---

### Advantages of APN

- Robust to baseline drift and uneven background  
- Insensitive to hot pixels and sparse outliers  
- Automatically adapts to image content  
- Preserves weak biological structures  
- Eliminates the need for manual parameter tuning  

In most real-world microscopy datasets, APN provides a more stable and biologically meaningful normalization compared to global methods.

---

## 📥 Installation

1. Download the latest `SRWiki_Normalization.jar`
2. Copy it into:ImageJ/Fiji/plugins/
3. Restart ImageJ/Fiji
4. Launch via:Plugins > SRWiki > SRWiki Normalization

---

## ⚙️ Usage Workflow

1. Open a grayscale image or stack in ImageJ/Fiji  
2. Launch the plugin  
3. Select a normalization or standardization method  
4. If using Percentile mode, set lower and upper thresholds  
5. Optionally enable histogram visualization  
6. Click **Run SRWiki**  
7. A processed image or stack will be generated  

---

## 📊 Normalization and Standardization Modes

| Mode | Mathematical Principle | Typical Use Case | Output |
| :--- | :--- | :--- | :--- |
| **APN (Adaptive)** | Data-driven estimation of signal/background bounds using local statistics | Low-SNR microscopy, real biological data | 8-bit |
| **Percentile** | Clipping based on user-defined percentiles | Controlled removal of outliers | 8-bit |
| **Min-Max** | Linear scaling using global min/max | Clean images with stable intensity range | 8-bit |
| **Max Only** | Scaling based on global maximum | Pre-calibrated data with zero background | 8-bit |
| **Z-Score** | $z = (x - \mu)/\sigma$ | Deep learning preprocessing | 32-bit float |
| **Mean** | $(x - \mu)/(max - min)$ | Centered normalization for analysis | 32-bit float |
| **Vector** | $x / \|x\|_2$ | Energy normalization / feature comparison | 32-bit float |

---

## ⚙️ Parameters

### Percentile Parameters (active only in Percentile mode)

- **Lower Percentile (%)**
- Default: `1.0`
- Intensities below this value are mapped to 0  

- **Upper Percentile (%)**
- Default: `99.8`
- Intensities above this value are mapped to 255  

These parameters allow manual control over intensity clipping when automatic methods are not preferred.

---

### Histogram Visualization

- **Show Histogram**
- Displays the pixel intensity distribution of the working image  
- Indicates selected normalization bounds  
- Useful for diagnosing intensity scaling behavior  

---

## 🔬 Input and Internal Processing

- Supports **8-bit, 16-bit, and 32-bit grayscale images**  
- All inputs are internally converted to an **8-bit working representation**  
- Normalization or standardization models are computed in this unified space  

### Stack Handling

- The first slice is used as the **reference slice**  
- The computed model is applied consistently to all slices  

This ensures comparability across time-series or volumetric datasets.

---

## 📊 Output Characteristics

- **8-bit output** for visualization-oriented normalization methods  
- **32-bit float output** for analysis-oriented standardization methods  

Output images retain method-specific identifiers in their naming for traceability.

---

## 📌 Practical Recommendations

- For most microscopy data:  
→ use **APN normalization**

- When manual control is required:  
→ use **Percentile normalization**

- For clean and well-behaved data:  
→ use **Min-Max normalization**

- For machine learning preprocessing:  
→ use **Z-Score standardization**

- For mathematical normalization tasks:  
→ use **Vector or Mean normalization**

---

## 📄 License

This project is licensed under the **ODbL 1.0 License**.

---

## ⭐ Summary

SR-Wiki Normalization provides a unified and extensible framework for microscopy image preprocessing. While multiple normalization strategies are available, the central contribution lies in the introduction of **Adaptive Percentage Normalization (APN)**.

By leveraging local statistical structure instead of global extrema, APN enables more robust, adaptive, and biologically meaningful intensity normalization for challenging imaging conditions.

In scenarios where traditional normalization fails, APN offers a principled and reliable alternative.
