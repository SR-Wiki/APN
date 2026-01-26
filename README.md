# IPIC-APN: Adaptive Percentage Normalization

![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)
![Build](https://img.shields.io/badge/Build-Maven-orange.svg)
![Platform](https://img.shields.io/badge/Platform-ImageJ%2FFiji-red.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

**IPIC-APN** is a preprocessing tool designed for **unsupervised learning denoising** workflows in ImageJ/Fiji.

It utilizes the Adaptive Percentage Normalization (APN) algorithm to prepare raw image data for downstream analysis. By automatically calculating optimal intensity thresholds, this plugin **effectively mitigates the influence of baseline drift and hot pixels**, providing a cleaner and more consistent input for deep learning models or advanced analysis.

## 📥 Installation

1.  **Download**: Locate the file named `Adaptive_Percentage-Normalization-1.0.0.jar` in the file list above (the main directory of this repository). Click on the file name and then click the **Download** button (or "View raw").
2.  [cite_start]**Install**: Copy the downloaded `.jar` file into the `plugins/` folder of your ImageJ or Fiji directory[cite: 1].
3.  [cite_start]**Restart**: Restart ImageJ/Fiji to complete the installation[cite: 107].

## 🚀 Usage

1.  **Open Image**: Load your target image or stack (supports 8-bit, 16-bit, and 32-bit).
2.  **Run Plugin**: Navigate to the menu bar:
    [cite_start]`Plugins > APN Tool > Run APN`[cite: 107].
3.  **Process**:
    * [cite_start]Click **Start Processing**[cite: 107].
4.  **Output**:
    * The plugin generates a normalized version of the image/stack.
    * [cite_start]It also displays the histogram with the calculated percentile thresholds used for the normalization[cite: 113].

## 🖼️ Screenshots

| Interface | Normalization Result |
| :---: | :---: |
| ![GUI](docs/gui_preview.png) | ![Result](docs/result_preview.png) |
| *IPIC Preprocessing Interface* | *Effect of Baseline & Hot Pixel Removal* |

> *Note: Please ensure `gui_preview.png` and `result_preview.png` are placed in the `docs/` folder of your repository.*

## 🛠 Building from Source

[cite_start]This project is managed by **Maven**[cite: 2]. To compile the plugin yourself:

1.  Clone the repository:
    ```bash
    git clone [https://github.com/YourUsername/APN_Adaptive-Percentage-Normalization.git](https://github.com/YourUsername/APN_Adaptive-Percentage-Normalization.git)
    ```
2.  Navigate to the project directory containing the `pom.xml`.
3.  Build using Maven:
    ```bash
    mvn clean package
    ```
4.  The compiled `.jar` file will be located in the `target/` directory.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
