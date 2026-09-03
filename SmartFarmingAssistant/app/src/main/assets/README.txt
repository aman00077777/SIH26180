Drop your model + data files directly into this folder (app/src/main/assets/) with
these EXACT names — the code looks them up by filename:

  crop_model_int8.tflite         <- your disease model
  class_names.json               <- 38 disease class names, in model's index order
  pest_model_int8.tflite         <- pest model (once fine-tuning finishes)
  pest_class_names.json          <- 102 pest class names
  pesticide_lookup_template.json <- disease-class -> recommendation lookup

Expected pesticide_lookup_template.json shape (per handoff doc, one entry per
disease class name):

{
  "Tomato___Late_blight": {
    "recommendation": "Apply copper-based fungicide. Remove and destroy infected leaves."
  },
  "Tomato___healthy": {
    "recommendation": "No action needed."
  }
}

The app degrades gracefully if a file is missing (e.g. pest model still training) —
it just skips that model rather than crashing. Once you drop real files in here,
rebuild and reinstall the app for them to be bundled into the APK.
