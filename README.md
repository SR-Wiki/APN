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

---

## ⭐ Summary

SR-Wiki Normalization provides a unified and extensible framework for microscopy image preprocessing. While multiple normalization strategies are available, the central contribution lies in the introduction of **Adaptive Percentage Normalization (APN)**.

By leveraging local statistical structure instead of global extrema, APN enables more robust, adaptive, and biologically meaningful intensity normalization for challenging imaging conditions.

In scenarios where traditional normalization fails, APN offers a principled and reliable alternative.
