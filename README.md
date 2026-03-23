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

**SR-Wiki Normalization** is an ImageJ/Fiji plugin for **robust preprocessing of biological microscopy images**, with a particular focus on low signal-to-noise (low-SNR) fluorescence data.

Microscopy images often suffer from:
- baseline drift  
- uneven background  
- hot pixels and sparse outliers  
- weak structural signals buried in noise  

Traditional global normalization methods (e.g., Min-Max) frequently fail under these conditions.  
This plugin addresses these challenges by providing a **unified framework of 7 normalization and standardization methods**, centered around the **Adaptive Percentage Normalization (APN)** algorithm.

> **Core idea:** Instead of relying solely on global intensity statistics, SR-Wiki introduces APN to **separate background and signal using local statistical structure**, enabling more reliable intensity scaling in challenging imaging conditions.

The plugin is suitable for:
- unsupervised deep learning denoising  
- quantitative fluorescence analysis  
- preprocessing pipelines  
- high-quality visualization  

---

## 🚀 Why APN?

### Problem with traditional normalization

Standard normalization methods assume:
- background is globally uniform  
- signal distribution is well-behaved  
- extreme values are rare  

However, real microscopy data often violates these assumptions.

As a result:
- **Min-Max** over-amplifies noise  
- **Max normalization** is dominated by hot pixels  
- **Percentile normalization** still depends on manually chosen thresholds  

---

### APN: Adaptive, data-driven normalization

**Adaptive Percentage Normalization (APN)** is designed specifically for microscopy data.

Instead of using global extrema, APN:

1. Divides the image into local blocks  
2. Computes:
   - frequency-domain statistics  
   - grayscale variance  
   - kurtosis (signal sharpness)  
3. Separates:
   - background-like regions  
   - signal-like regions  
4. Estimates:
   - **Xmin from background regions**
   - **Xmax from signal regions**

---

### Key Advantages of APN

- **Robust to noise and background drift**  
- **Insensitive to hot pixels and sparse outliers**  
- **Automatically adapts to image content**  
- **Better preserves weak biological structures**  
- **No manual parameter tuning required**

---

### When to use APN

Use **APN normalization** when:

- your image has **low SNR**
- background is **uneven or drifting**
- weak structures are **hard to see**
- Min-Max produces **washed-out contrast**
- Percentile requires too much manual tuning

> In most real microscopy scenarios, **APN should be your default choice**.

---

## 📥 Installation

1. Download the latest `SRWiki_Normalization.jar`
2. Copy it into:
