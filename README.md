# MediLens 🔬

> AI-powered Android medication management — real-time pill detection, prescription OCR, and smart reminders. Built for Pakistan's healthcare context.

[![Kotlin](https://img.shields.io/badge/Kotlin-Android-purple?logo=kotlin)](https://kotlinlang.org)
[![YOLOv11](https://img.shields.io/badge/YOLOv11-TFLite-orange)](https://docs.ultralytics.com)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-yellow?logo=firebase)](https://firebase.google.com)
[![License: Academic](https://img.shields.io/badge/License-Academic-lightgrey)](#license)

---

## The Problem

In Pakistan, patients frequently struggle to identify pills, verify prescriptions, and stick to medication schedules. Counterfeit drugs circulate widely, handwritten prescriptions are hard to read, and existing reminder apps rely entirely on manual input with no visual verification. MediLens was built to close that gap — combining computer vision, OCR, and intelligent reminders into a single Android app tailored to local healthcare realities.

---

## Table of Contents

- [Features](#features)
- [How It Works](#how-it-works)
- [Model Performance](#model-performance)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Dataset & Training](#dataset--training)
- [Getting Started](#getting-started)
- [Known Limitations](#known-limitations)
- [Future Work](#future-work)
- [Team](#team)
- [About](#about)
- [License](#license)

---

## Features

**Pill Detection**
Real-time pill recognition and counting using a custom-trained YOLOv11 model running entirely on-device via TensorFlow Lite. Detects pills from the live camera feed with bounding box overlays and class labels — no internet required. Inference latency under 200ms.

**Prescription Scanning (OCR)**
Photograph a printed or handwritten prescription and the app extracts medication name, dosage, and frequency using the Donut document understanding model (hosted on Hugging Face), followed by Gemini NER for medical entity extraction from the raw text.

**Smart Medication Reminders**
Time-based reminders with daily scheduling via Android AlarmManager. Reminders persist across reboots and can be toggled per medication. Hard/soft reminder escalation (escalating urgency until acknowledged) is planned for the next iteration.

**Medication History & Audit Log**
Every pill scan and prescription event is logged with timestamps, giving patients and caregivers a full adherence history.

**Offline-First**
Pill detection runs fully on-device. All data is stored locally in SQLite (Room) and synced to Firebase Firestore when connectivity is available.

**Firebase Authentication**
Secure email/password registration and login with persistent sessions.

---

## How It Works

```
User signs in (Firebase Auth)
         ↓
── Pill Detection ─────────────────────────────
Camera frame → CameraX → YOLOv11 TFLite
→ Bounding box overlay + class label + count
→ SCAN_LOG entry written to Room DB
         ↓
── Prescription Scanning ──────────────────────
Photo → Base64 encode → Hugging Face Donut API
→ Raw text → Gemini NER → Structured fields
→ User reviews → saved to Firestore + Room
         ↓
── Reminders ──────────────────────────────────
User sets time → AlarmManager scheduled
→ Notification + audible alert at trigger time
→ Persists across reboots via BootCompletedReceiver
```

---

## Model Performance

The YOLOv11 model was trained for 100 epochs on Google Colab (NVIDIA T4 GPU) using a custom locally-collected Pakistani pill dataset managed via Roboflow.

### Overall Metrics (held-out test set, ~108 images)

| Metric | Value |
|--------|-------|
| mAP@0.5 | **99.0%** |
| Precision | **97.8%** |
| Recall | **96.7%** |
| F1 Score | **97.3%** |
| Inference latency (on-device) | **< 200ms** |
| Confidence threshold | 0.60 |
| Training epochs | 100 |

### Per-Class Performance

| Class | Precision | Recall | F1 | AP (PR-AUC) |
|-------|-----------|--------|----|-------------|
| Panadol | 97.8% | 96.7% | 97.2% | **99.5%** |
| Ventolin | 97.8% | 96.7% | 97.2% | **99.5%** |
| Myteka | 97.8% | 96.7% | 97.2% | **98.7%** |
| Risek | 97.8% | 96.7% | 97.2% | 94.2% |

Risek (capsule form factor) shows slightly lower AP due to greater visual variation across lighting and orientations — consistent with the confusion matrix findings.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android App | Kotlin, Android Studio, MVVM, Jetpack |
| Camera | CameraX |
| Pill Detection | YOLOv11 → TensorFlow Lite |
| Prescription OCR | Donut (Hugging Face) + Gemini NER |
| Authentication | Firebase Authentication |
| Cloud Database | Firebase Firestore |
| Local Database | SQLite via Room |
| Notifications | AlarmManager + NotificationManager |
| Dataset & Annotation | Roboflow |
| Model Training | Google Colab + Ultralytics YOLOv11 |

---

## Architecture

MediLens uses a three-layer client-server architecture:

```
┌─────────────────────────────────────────────┐
│           Layer 1 — Mobile Frontend         │
│  Kotlin / MVVM / Jetpack / CameraX          │
│  6 modules: Auth, Detection, Prescription,  │
│  OCR Scan, Reminders, Profile               │
└────────────────────┬────────────────────────┘
                     │ HTTPS / Firebase SDK
┌────────────────────▼────────────────────────┐
│         Layer 2 — Backend Services          │
│  Firebase Auth  •  Firebase Firestore       │
│  SQLite (Room) for offline-first storage    │
└────────────────────┬────────────────────────┘
                     │ HTTP POST (Base64 image)
┌────────────────────▼────────────────────────┐
│          Layer 3 — AI Engine                │
│  YOLOv11 TFLite  →  on-device inference     │
│  Donut (Hugging Face) → prescription text   │
│  Gemini NER  →  medical entity extraction   │
└─────────────────────────────────────────────┘
```

---

## Project Structure

```
app/
├── ui/
│   ├── auth/          # Login, registration, session management
│   ├── home/          # Dashboard and quick actions
│   ├── detection/     # CameraX + TFLite pill detection + overlay
│   ├── prescription/  # Prescription management (manual + OCR)
│   ├── scan/          # Donut API integration + Gemini NER
│   ├── reminder/      # AlarmManager-based reminders
│   └── profile/       # User profile editing
├── data/
│   ├── local/         # Room database, DAOs, entities
│   │   └── entities/  # USER, PRESCRIPTION, MEDICATION_REMINDER, SCAN_LOG
│   ├── remote/        # Firestore repo, Firebase Auth, Hugging Face client
│   └── model/         # Shared data classes (ParsedPrescription, DetectionResult)
├── ml/                # YOLOv11 TFLite wrapper (PillDetector), pre/post-processing
└── util/              # Image encoding, permission helpers, date formatters
```

---

## Dataset & Training

The model was trained on a custom locally-collected Pakistani pill dataset — not a public Western benchmark — to reflect real-world conditions in Pakistan.

**Dataset pipeline:**
1. **Collection** — Smartphone photos of 4 pill classes (Panadol, Ventolin, Myteka, Risek) under varied lighting, backgrounds, angles, and blister/non-blister conditions
2. **Annotation** — Manual bounding box annotation in Roboflow
3. **Augmentation** — 8 transforms applied to training set only: horizontal flip, vertical flip, random rotation, brightness adjustment, exposure variation, Gaussian blur, noise injection, mosaic augmentation
4. **Split** — 90% train / 7% validation / 3% test

**Training config:**

| Hyperparameter | Value |
|---------------|-------|
| Architecture | YOLOv11 (Ultralytics) |
| Pre-trained weights | COCO |
| Input size | 640 × 640 |
| Batch size | 16 |
| Learning rate | 0.01 (cosine decay) |
| Optimizer | SGD with momentum |
| Epochs | 100 |
| Export format | TensorFlow Lite (.tflite) |

---

## Getting Started

### Prerequisites

- Android Studio (Flamingo or later)
- Android device or emulator — API 26+, camera, min 4GB RAM
- Firebase project with Auth and Firestore enabled
- Hugging Face account (for Donut inference API key)

### Setup

```bash
# 1. Clone the repo
git clone https://github.com/Wcoder547/MediLens.git
cd MediLens

# 2. Open in Android Studio
# File → Open → select the MediLens directory

# 3. Add google-services.json
# Download from your Firebase project console
# Place in: app/google-services.json

# 4. Add your API keys to local.properties or your secrets config
HUGGING_FACE_API_KEY=your_hf_token
GEMINI_API_KEY=your_gemini_api_key

# 5. Build and run
# Run → Run 'app' on your connected device or emulator
```

> The YOLOv11 TFLite model file is bundled in `app/src/main/assets/` — no separate download needed.

---

## Known Limitations

| Limitation | Detail |
|-----------|--------|
| 4 medication classes only | Cannot identify pills outside Panadol, Ventolin, Myteka, Risek |
| OCR accuracy 50–70% | Handwritten prescriptions and non-standard abbreviations reduce accuracy |
| Prescription scanning requires internet | Donut model is cloud-hosted; offline OCR not yet available |
| No hard/soft reminder escalation | Current reminders are single-tier; escalation planned for next iteration |
| No counterfeit detection | Pill class recognition only; anomaly detection not yet implemented |
| Extreme angle/low-light degradation | Detection degrades beyond ~60° angle or very low light |

---

## Future Work

- Expand dataset to cover more Pakistani medication classes
- Hard/soft reminder escalation system
- Fine-tune Donut on Pakistani prescription layouts for higher OCR accuracy (target: >90%)
- On-device offline prescription scanning via quantized TFLite Donut
- Counterfeit/anomaly detection using one-class classification or autoencoder
- Urdu language support for voice alerts and prescription scanning
- Clinical user study with real patients and caregivers

---

## Team

| Name | Roll No |
|------|---------|
| Waseem Akram | BSIT51F22S021 |
| Hafiz Fahad Iqbal | BSIT51F22S047 |
| Muhammad Talha | BSIT51F22S003 |

**BS Information Technology — Semester 8, University of Sargodha**

---

## About

MediLens is a final-year project built to address real medication safety challenges in Pakistan — combining computer vision, OCR, and intelligent reminders into a single Android application.

In Pakistan, patients frequently struggle to identify pills, verify prescriptions, and adhere to complex medication schedules. Counterfeit drugs circulate widely. Handwritten prescriptions are hard to parse. Existing reminder apps rely entirely on user-entered data and have no way to verify the physical pill being taken. MediLens was built to close that gap.

From an engineering perspective, the project covers a full mobile AI stack: a custom-trained YOLOv11 model exported to TFLite and running on-device in real time, a cloud-hosted document understanding model (Donut) integrated with a Gemini NER post-processing step for structured prescription extraction, and an offline-first data layer using Room + Firestore sync. Every component was chosen for deployability in Pakistan's infrastructure — low-end Android devices, intermittent connectivity, and localized medication packaging.

Built by [Waseem Akram](https://www.linkedin.com/in/wasim-akram-dev/) and team — BS IT, University of Sargodha.

---

## License

This project was developed as an academic final-year project. Please contact the authors before reuse or redistribution.
