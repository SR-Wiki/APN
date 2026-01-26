# IPIC: Adaptive Percentage Normalization (APN)

![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)
![Build](https://img.shields.io/badge/Build-Maven-orange.svg)
![Platform](https://img.shields.io/badge/Platform-ImageJ%2FFiji-red.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

**IPIC-APN** is a robust ImageJ/Fiji plugin designed to normalize image intensity based on the **Adaptive Percentage Normalization (APN)** algorithm. It effectively removes background noise while preserving signal integrity, supporting both single images and stacks (time-lapse/z-stack).

## ✨ Features

* **Smart Normalization**: Automatically calculates optimal $X_{min}$ and $X_{max}$ thresholds based on signal statistics.
* **Stack Support**: Detects image stacks and applies the parameters from the reference slice (Slice 1) to the entire stack to prevent flickering.
* **High-Bit Depth Compatible**: Seamlessly handles 8-bit, 16-bit, and 32-bit input images.
* **User-Friendly GUI**: A modern, dark-themed interface with a one-click "Start Processing" workflow.
* **Detailed Output**: Generates normalized images and provides the calculated percentile thresholds (Pmin/Pmax).

## 📥 Installation

1.  **Download**: Get the latest `.jar` file (e.g., `Adaptive_Percentage-Normalization-1.0.0.jar`) from the [Releases](../../releases) page.
2.  [cite_start]**Install**: Copy the downloaded `.jar` file into the `plugins/` directory of your ImageJ or Fiji installation.
3.  [cite_start]**Restart**: Restart ImageJ/Fiji to load the plugin.

## 🚀 Usage

1.  **Open Image**: Open the image or stack you wish to process in ImageJ/Fiji.
2.  **Run Plugin**: Navigate to the menu bar and select:
    `Plugins > APN Tool > Run APN`.
3.  **Process**:
    * The **IPIC** interface will appear.
    * Click the **Start Processing** button.
4.  **Result**:
    * For single images: A normalized image and a histogram with thresholds will be displayed[cite: 113].
    * For stacks: A new normalized stack will be generated.

## 🖼️ Screenshots

| Interface | Result |
| :---: | :---: |
| ![GUI](docs/gui_preview.png) | ![Result](docs/result_preview.png) |
| *Modern IPIC Interface* | *Before vs. After Normalization* |

> *Note: Please replace `docs/gui_preview.png` and `docs/result_preview.png` with your actual screenshots.*

## 🛠 Building from Source

This project is managed by **Maven**. If you want to contribute or modify the code:

1.  Clone the repository:
    ```bash
    git clone [https://github.com/YourUsername/IPIC-APN-Normalization.git](https://github.com/YourUsername/IPIC-APN-Normalization.git)
    ```
2.  Navigate to the project directory (where `pom.xml` is located).
3.  Build the project using Maven:
    ```bash
    mvn clean package
    ```
4.  The compiled `.jar` file will be generated in the `target/` directory[cite: 14].

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📧 Contact / Citation

If you use this tool in your research, please cite:
> **[Your Name]**. (2026). *IPIC-APN: Adaptive Percentage Normalization Plugin for ImageJ*. GitHub Repository.
