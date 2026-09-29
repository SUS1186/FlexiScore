# FlexiScore

> **On-Device Computer Vision Goniometry & Biomechanics Platform for Paralympic Functional Classification**  
> *Assistive Biomechanics | IPC Functional Standards | Edge Android Architecture*

---

## 📌 Executive Summary

**FlexiScore** is an on-device digital screening and clinical goniometry platform built for **Paralympic & Adaptive Athletics**. It replaces traditional, subjective manual plastic goniometers with an edge-computed kinematic tracking pipeline capable of calculating 3D joint range-of-motion (ROM), detecting compensatory movements, logging bilateral asymmetry deficits, and generating tamper-proof classification reports compliant with International Paralympic Committee (IPC) standards—operating 100% offline with zero cloud dependency.

---

## 🧩 Core Architectural Modules

### Module 1: Real-Time Computer Vision & AR Goniometry Engine

* **3D Body Pose Landmark Tracking:**
  * Powered by Google MediaPipe Tasks Vision (`PoseLandmarker`) running over an asynchronous AndroidX CameraX video stream.
  * Simultaneously tracks 33 standard 3D anatomical landmarks at 30+ FPS on-device with normalized $(X, Y, Z)$ spatial-depth coordinates.
  * **Anatomical Mapping Configurations:**
    * *Elbow Joint:* Proximal = Shoulder ($P_{11} / P_{12}$), Vertex = Elbow ($P_{13} / P_{14}$), Distal = Wrist ($P_{15} / P_{16}$).
    * *Shoulder Abduction:* Proximal = Hip ($P_{23} / P_{24}$), Vertex = Shoulder ($P_{11} / P_{12}$), Distal = Elbow ($P_{13} / P_{14}$).
* **Live AR Overlay UI (`GoniometerOverlayView.java`):**
  * Hardware-accelerated custom view layered above `PreviewView` rendering vertex nodes, vector connection arms (`Canvas.drawLine`), and dynamic swept arcs (`Canvas.drawArc`).
  * **Color State Indicators:**
    * `Cyan (#00E5FF)`: Active tracking and movement phase.
    * `Emerald Green (#00E676)`: Peak angle stabilized and measurement locked.
    * `Amber (#FFAB00)`: Joint range restriction / sub-maximal threshold detected.
    * `Neon Red (#FF1744)`: Compensatory cheat posture or camera skew warning.
* **Vector Goniometry Math Engine (`GoniometerMath.java`):**
  * Computes 3D joint angles using Euclidean vector dot products:
    $$\vec{u} = P_{\text{proximal}} - P_{\text{joint}}, \quad \vec{v} = P_{\text{distal}} - P_{\text{joint}}$$
    $$\theta = \arccos\left(\frac{\vec{u} \cdot \vec{v}}{\Vert{}\vec{u}\Vert{} \Vert{}\vec{v}\Vert{}}\right) \times \left(\frac{180^\circ}{\pi}\right)$$

---

### Module 2: Automated Motion Capture & Peak Freeze Engine

* **Rolling Angular Velocity Tracker:**
  * Tracks velocity continuously across a 30-frame window to detect physiological deceleration:
    $$\omega(t) = \frac{\theta(t) - \theta(t - \Delta t)}{\Delta t}$$
* **Auto-Peak Freeze Trigger:**
  * Hands-free measurement lock: freezes frame when $\vert{}\omega\vert{} < 2^\circ/\text{sec}$ continuously for $\ge 1.2\text{ s}$.
  * Captures peak value $\theta_{\text{peak}} = \max(\theta_{1..N})$ and fires an Android `Vibrator` haptic pulse to notify the athlete.

---

### Module 3: Anti-Cheat & Assessment Integrity System

* **Multi-Joint Compensatory Movement Check:**
  * **Spinal Lean Detection:** Computes angular deviation of the torso vector relative to the vertical plane:
    $$\theta_{\text{spine}} = \arctan2(\Delta X_{\text{torso}}, \Delta Y_{\text{torso}})$$
    Halts measurement if $\vert{}\theta_{\text{spine}}\vert{} > 5^\circ$ to prevent trunk leaning from inflating arm elevation.
  * **Pelvic/Hip Tilt Check:** Monitors vertical delta between left hip ($P_{23}$) and right hip ($P_{24}$) to eliminate pelvic compensation.
* **Camera Plane Orthogonality Detector:**
  * Measures depth disparity between symmetrical body points ($\Delta Z_{\text{shoulders}} = \vert{}Z_{\text{left}} - Z_{\text{right}}\vert{}$). Prompts user if camera orientation is skewed.
* **Intra-Subject 3-Trial Consistency Check:**
  * Evaluates effort authenticity across 3 consecutive trial repetitions:
    $$\text{Variance \%} = \frac{\max(\theta_1, \theta_2, \theta_3) - \min(\theta_1, \theta_2, \theta_3)}{\text{mean}(\theta_1, \theta_2, \theta_3)} \times 100$$
  * $\text{Variance} \le 5\%$: Confirmed genuine maximal effort.
  * $\text{Variance} > 15\%$: Inconsistent effort flagged; protocol reset required.

---

### Module 4: Advanced Clinical & Biomechanical Analytics

* **Bilateral Symmetry Deficit Calculator:**
  * Quantifies motor loss by benchmarking impaired limbs directly against healthy baselines:
    $$\text{Deficit \%} = \frac{\vert{}\theta_{\text{unaffected}} - \theta_{\text{affected}}\vert{}}{\theta_{\text{unaffected}}} \times 100$$
* **Active vs. Passive Range of Motion (AROM vs. PROM):**
  * Differentiates mechanical contracture from neuromuscular weakness:
    $$\text{Activation Deficit \%} = \frac{\theta_{\text{Passive}} - \theta_{\text{Active}}}{\theta_{\text{Passive}}} \times 100$$
  * *Clinical Rule:* Normal PROM with high activation deficit indicates neurological recruitment impairment.
* **Dynamic Velocity & Spasticity Curves:**
  * Plots velocity curves ($\omega$ over time) mapped to the Ashworth Scale to isolate involuntary catches in Hypertonia and Cerebral Palsy.

---

### Module 5: Security, Auditability & PDF Exporter

* **Native IPC PDF Exporter (`ClassificationPdfReport.java`):**
  * Compiles official IPC-ready summary cards on-device using Android's native `android.graphics.pdf.PdfDocument`.
  * Generates athlete data tables, frozen frame screenshots, ROM angles, and pass/fail indicators against Minimum Impairment Criteria (MIC).
* **Cryptographic Anti-Tamper QR Code:**
  * Hashes recorded angles, timestamps, and athlete identifiers with **SHA-256** and embeds a verifiable QR code directly onto the report.
* **Video Audit Trail Integration:**
  * Captures a 3-second animated image sequence or keyframe capture of the peak stretch position and embeds it into the permanent local audit record.

---

### Module 6: Offline Architecture & Accessibility

* **Room Database Persistence:**
  * Local offline-first architecture with Android Jetpack Room ORM (`AppDatabase`, `AssessmentRecord`) for zero-latency storage in rural venues.
* **Hands-Free Voice Engine:**
  * Android `SpeechRecognizer` for audio commands (*"Start Test"*, *"Hold Position"*, *"Save Test"*).
  * `TextToSpeech` (TTS) vocalizes real-time angles and alerts (*"142 degrees locked"*, *"Straighten torso"*).

---

## 📊 Feature Summary Matrix

| Feature Module | Key Component | Implementation | Primary Value |
|---|---|---|---|
| **Vision** | Pose Tracking | MediaPipe + CameraX | Non-contact digital goniometry |
| **Automation** | Peak Freeze Engine | Angular Velocity ($\omega$) threshold | Hands-free hands-off workflow |
| **Anti-Cheat** | Posture Validation | Spine & Hip angle constraints | Rejects false reach/leaning |
| **Anti-Cheat** | 3-Trial Variance | Statistical variance formula | Detects faked joint stiffness |
| **Clinical** | Bilateral & AROM/PROM | Deficit percentage formulas | Identifies muscle vs. joint origin |
| **Security** | Tamper-Proof QR PDF | SHA-256 + Native `PdfDocument` | Official IPC audit compliance |
| **Storage** | Room Database | Local SQLite abstraction | Complete offline execution |

---

## 📂 Project Structure

```text
app/
├── src/main/
│   ├── assets/
│   │   └── pose_landmarker_full.task         # Google AI Edge MediaPipe Model
│   ├── java/com/parakinetics/flexiscore/
│   │   ├── assessment/                       # Test protocols & IPC class criteria
│   │   │   ├── AssessmentProtocol.java
│   │   │   ├── PeakAngleTracker.java
│   │   │   └── ClassificationRules.java
│   │   ├── data/                             # Room ORM database & entities
│   │   │   ├── AssessmentRecord.java
│   │   │   └── AppDatabase.java
│   │   ├── math/                             # Biomechanical math & kinematics
│   │   │   ├── GoniometerMath.java
│   │   │   └── StabilizationDetector.java
│   │   ├── ml/                               # CameraX & MediaPipe integration
│   │   │   └── PoseLandmarkerHelper.java
│   │   ├── pdf/                              # PDF report generation & QR hashing
│   │   │   ├── ClassificationPdfReport.java
│   │   │   └── SecurityHashHelper.java
│   │   └── ui/                               # Presentation layer & custom canvas
│   │       ├── MainActivity.java
│   │       ├── GoniometerOverlayView.java
│   │       └── ReportActivity.java
│   ├── res/
│   │   ├── layout/                           # XML Layouts (Preview + Canvas HUD)
│   │   └── values/                           # Medical dark palette (Teal/Emerald/Amber)
│   └── AndroidManifest.xml
└── build.gradle

```

---

## 🛠 Tech Stack

* **Platform:** Native Android (Min SDK 26 / Android 8.0+)
* **Vision & Edge AI:** Google AI Edge MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision:0.10.14`)
* **Camera Framework:** AndroidX CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`)
* **Local Persistence:** Android Jetpack Room ORM
* **Document Engine:** Android Native `PdfDocument` & ZXing QR Generator
* **Accessibility:** Android `TextToSpeech` & `SpeechRecognizer`
* **Languages:** Java / Kotlin

---

## 📥 Clone Repository

To clone and set up the FlexiScore repository locally:

```bash
# Clone the repository
git clone [https://github.com/SUS1186/FlexiScore.git](https://github.com/SUS1186/FlexiScore.git)

# Navigate into the project directory
cd FlexiScore

# Build the debug APK via Gradle wrapper
./gradlew assembleDebug

```

```

```
