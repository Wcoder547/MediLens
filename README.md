# MediLens 🔬
### AI-Powered Medication Management for Android

MediLens is a final-year project built to address real medication safety challenges in Pakistan — combining computer vision, OCR, and intelligent reminders into a single Android application.

---

## The Problem

In Pakistan, patients frequently struggle to identify pills, verify prescriptions, and stick to medication schedules. Counterfeit drugs circulate widely, handwritten prescriptions are hard to read, and existing reminder apps rely entirely on manual input with no visual verification. MediLens was built to close that gap.

---

## Features

**Pill Detection**
Real-time pill recognition and counting using a custom-trained YOLOv11 model running entirely on-device via TensorFlow Lite. Detects pills from the live camera feed with bounding box overlays and class labels — no internet required.

**Prescription Scanning (OCR)**
Photograph a printed or handwritten prescription and the app automatically extracts medication name, dosage, and frequency using the Donut document understanding model hosted on Hugging Face, followed by Gemini NER for medical entity extraction.

**Smart Medication Reminders**
Time-based reminders with daily scheduling via Android AlarmManager. Reminders persist across reboots and can be toggled per medication. Hard/soft reminder escalation is planned for the next iteration.

**Medication History & Audit Log**
Every pill scan and prescription event is logged with timestamps, giving patients and caregivers a full adherence history.

**Offline-First**
Pill detection runs fully on-device. All data is stored locally in SQLite (Room) and synced to Firebase Firestore when connectivity is available.

**Firebase Authentication**
Secure email/password registration and login with persistent sessions.

---

## Model Performance

The custom YOLOv11 model was trained on a locally collected Pakistani pill dataset (4 classes: Panadol, Ventolin, Myteka, Risek) augmented and managed via Roboflow, then trained on Google Colab over 100 epochs.

| Metric | Value |
|---|---|
| mAP@0.5 | 99.0% |
| Precision | 97.8% |
| Recall | 96.7% |
| F1 Score | 97.3% |
| Inference Latency (on-device) | < 200ms |

---

## Tech Stack

| Layer | Technologies |
|---|---|
| Android App | Kotlin, Android Studio, MVVM, Jetpack |
| Camera | CameraX |
| Pill Detection | YOLOv11 → TensorFlow Lite |
| Prescription OCR | Donut (Hugging Face) + Gemini NER |
| Authentication | Firebase Authentication |
| Cloud Database | Firebase Firestore |
| Local Database | SQLite via Room |
| Dataset & Annotation | Roboflow |
| Model Training | Google Colab + Ultralytics |

---

## Architecture

MediLens uses a three-layer architecture:

- **Mobile Frontend** — Kotlin/MVVM Android app with 6 feature modules
- **Backend Services** — Firebase Auth + Firestore for identity and cloud sync
- **AI Engine** — On-device YOLOv11 TFLite for detection, Hugging Face APIs for OCR

---

## Project Structure

```
app/
├── ui/
│   ├── auth/          # Login, registration, session management
│   ├── home/          # Dashboard and quick actions
│   ├── detection/     # CameraX + TFLite pill detection
│   ├── prescription/  # Prescription management
│   ├── scan/          # OCR scanning + Donut API integration
│   ├── reminder/      # AlarmManager-based reminders
│   └── profile/       # User profile editing
├── data/
│   ├── local/         # Room database, DAOs, entities
│   ├── remote/        # Firestore repo, Firebase Auth, Hugging Face client
│   └── model/         # Shared data classes
├── ml/                # YOLOv11 TFLite wrapper, pre/post-processing
└── util/              # Image encoding, permission helpers, date formatters
```

---

## Known Limitations

- Detection currently covers 4 medication classes only
- Prescription scanning accuracy is 50–70% (handwritten prescriptions are harder)
- OCR requires an internet connection (on-device OCR is planned)
- Hard/soft reminder escalation not yet implemented
- Counterfeit detection not yet implemented

---

## Future Work

- Expand dataset to more Pakistani medication classes
- Hard and soft reminder escalation system
- Fine-tune Donut on Pakistani prescription layouts for higher OCR accuracy
- On-device offline prescription scanning via quantized TFLite Donut
- Urdu language support for voice alerts and prescription scanning
- Anomaly/counterfeit detection using one-class classification
- Clinical user study with real patients and caregivers

---

## Team

| Name | Roll No |
|---|---|
| Waseem Akram | BSIT51F22S021 |
| Hafiz Fahad Iqbal | BSIT51F22S047 |
| Muhammad Talha | BSIT51F22S003 |

BS Information Technology — Semester 8
University of Sargodha

---

## License

This project was developed as an academic final-year project. Please contact the authors before reuse or redistribution.
