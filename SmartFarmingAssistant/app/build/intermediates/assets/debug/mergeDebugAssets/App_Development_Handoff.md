# Smart Farming Assistant — App Development Handoff
### For: Android/App Development Teammate | SIH 2026, PS 26180

---

## 1. The Problem (context, in short)

Farmers, especially in low-connectivity regions, lose crops because problems (disease, pest, water stress) are caught too late — no expert nearby, no internet to consult one. We're building a system that senses crop/environmental conditions in the field and gives instant advice **without needing internet**.

## 2. The Proposed Solution (what we're building)

A **phone-based edge AI app** (using the Snapdragon NPU already in the phone) as the "brain," paired with an **external ESP32 sensor node** (soil moisture, temp, humidity) that talks to the phone over Bluetooth. The phone:
1. Takes a photo of a crop leaf
2. Runs an on-device AI model — fully offline — to detect disease/pest
3. Combines that with live sensor readings from the ESP32 node
4. Shows the farmer plain-language advice ("Irrigate now," "Possible disease detected — [pest lookup]")
5. Sends an SMS alert as a fallback, even with zero data connection

**Core principle for the whole app: nothing in the detection/advice pipeline should require internet.** Internet is only used opportunistically (weather sync, cooperative dashboard sync) — never as a dependency for the core alert flow.

---

## 3. What's Already Built and Ready to Hand Off to You

### A. Crop Disease Model ✅ Done, tested, ready
- File: `crop_model_int8.tflite` (2.75 MB, INT8 quantized)
- File: `class_names.json` — ordered list of 38 class names, **index order matters**, this is the order the model outputs correspond to
- Verified: 96.25% accuracy on 640 real test images, 18.28ms inference latency on CPU (no NPU yet — should be faster on-device)
- Input spec: `224x224x3` image, `uint8` dtype (NOT normalized float — the model expects raw 0-255 pixel values as uint8, don't divide by 255 on the app side)
- Output spec: array of 38 `uint8` probability scores — take `argmax` for predicted class, that same index maps into `class_names.json`

### B. Pest Detection Model 🔄 In progress (fine-tuning today/tomorrow)
- Same format as above once done: `.tflite` file + `pest_class_names.json` (102 classes)
- Will hand off once training completes — build your image-capture and inference code generically enough to accept either model file, since you'll likely run both models on the same captured photo (disease model + pest model, two separate inferences)

### C. Pesticide/Treatment Lookup Table 🔄 In progress (teammate filling in ICAR data)
- File: `pesticide_lookup_template.json` — maps each of the 38 disease class names to a treatment recommendation string
- **Important**: this is a static lookup, not something the app generates — when the disease model predicts a class, just look up that class name as a key in this JSON file and display the `recommendation` field. Don't build any logic that tries to generate treatment advice dynamically.

---

## 4. What You Need to Build (App Side)

### Core screens/flow
1. **Capture screen** — camera view, capture a leaf photo
2. **Processing** — run inference (see Section 5), show a brief loading state (should be near-instant, <100ms typically)
3. **Result screen** — show: predicted disease/pest class, confidence %, matching advisory text, matching pesticide recommendation (from lookup table)
4. **Sensor status screen** — show live readings from ESP32 (soil moisture, temp, humidity) once BLE integration is in, plus derived irrigation advice ("Irrigate now" / "Hold")
5. **History/dashboard** — simple local log of past readings/predictions with basic trend charts (Point 7 from the PS)
6. **Alert settings** — where the farmer's phone number is registered for SMS fallback

### Required technical components

**On-device ML inference**
- Use **TensorFlow Lite Android library** (`org.tensorflow:tensorflow-lite`) — this is the standard, well-documented path
- Add the **NNAPI delegate** (`org.tensorflow:tensorflow-lite-support` includes this) — this is what routes inference through the Snapdragon Hexagon NPU instead of the general CPU. Without this delegate, inference still works but won't showcase the "runs on Qualcomm silicon" story that matters for this PS
- If you want to go further (optional, stretch): Qualcomm's own **QNN (Qualcomm AI Engine Direct) SDK** gives more direct/optimized NPU access, but has a steeper integration curve — NNAPI delegate is the pragmatic choice given your timeline

**Camera**
- **CameraX** (Android Jetpack library) — modern standard API, handles device compatibility issues for you, much less painful than the legacy Camera API

**Bluetooth (for ESP32 sensor node)**
- **Android Bluetooth LE APIs** (`BluetoothLeScanner`, `BluetoothGatt`) — you'll be scanning for the ESP32's BLE advertisement and reading its characteristic values (soil moisture/temp/humidity as numbers it broadcasts)
- Coordinate directly with whoever's building the ESP32 firmware (Workstream B/E) on the exact BLE service/characteristic UUIDs and data format they're using — this needs to be agreed between you two specifically, ask them today

**SMS**
- **Android SmsManager API** — sends SMS directly from the phone's own SIM, no separate GSM hardware needed since you're using the phone itself
- Needs `SEND_SMS` permission — request this explicitly, and handle the case where it's denied gracefully

**Local storage/database**
- **Room** (Android's SQLite abstraction) — for storing prediction history, sensor readings over time (feeds the dashboard/analytics screen)

**Charts (for the analytics dashboard)**
- **MPAndroidChart** or **Vico** — either is fine, pick whichever has better docs for whoever's building this screen

**Weather API (optional, when connectivity available)**
- IMD (India Meteorological Department) API or OpenWeatherMap — pull and cache forecast data whenever the phone has connectivity, for the "Environmental Risk Monitoring" point

### Required permissions (add to AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!-- Location permission is required by Android for BLE scanning, even though you're not using GPS -->
<uses-permission android:name="android.permission.INTERNET" />
<!-- Only used for optional weather sync — app must fully function with this denied/unavailable -->
```

---

## 5. Minimal Inference Code (starting point)

```kotlin
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class DiseaseClassifier(context: Context) {
    private val interpreter: Interpreter
    private val classNames: List<String>

    init {
        val model = loadModelFile(context, "crop_model_int8.tflite")
        interpreter = Interpreter(model)
        classNames = loadClassNames(context, "class_names.json")
    }

    fun predict(bitmap: Bitmap): Pair<String, Float> {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        // resize to 224x224, keep as uint8 — do NOT normalize to 0-1 float
        val resized = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .build()
            .process(tensorImage)

        val output = Array(1) { ByteArray(classNames.size) }
        interpreter.run(resized.buffer, output)

        val scores = output[0]
        val maxIdx = scores.indices.maxByOrNull { scores[it].toInt() and 0xFF } ?: 0
        val confidence = (scores[maxIdx].toInt() and 0xFF) / 255f

        return Pair(classNames[maxIdx], confidence)
    }

    // ... loadModelFile / loadClassNames helper functions
}
```
This is a starting skeleton, not production-ready — but it shows the key gotcha: **the model expects uint8 input and gives uint8 output**, not the normalized float format many TFLite tutorials assume. Getting this wrong (e.g. normalizing to 0-1 float) will make the model run without errors but produce garbage predictions — a classic silent bug, so test against a known image and confirm the prediction matches what you got in the Python testing (Tomato Late Blight etc.) before trusting it further.

---

## 6. Critical Test Before Demo Day

**Test in airplane mode.** Turn off WiFi and mobile data entirely, then run the full capture → predict → advisory flow. If it works with zero connectivity, that's your core proof for the whole PS. Do this early, not the night before — if something in your app accidentally depends on internet (a library silently trying to phone home, an image loader expecting a URL, etc.), you want to catch that with time to fix it.

---

## 7. Questions to Resolve With the Team Today
- [ ] Confirm exact BLE data format from ESP32 teammate (what characteristic UUIDs, what data types for moisture/temp/humidity)
- [ ] Confirm phone hardware you're actually developing/testing on (which Snapdragon chip, for the NNAPI/NPU story to be accurate in the pitch)
- [ ] Get the pest model + pesticide lookup table once they're ready (tomorrow)
