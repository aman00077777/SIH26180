# Sample Leaf Images

Place leaf/pest photos here for the virtual camera module.

**Naming convention:**
```
<crop>_<condition>_<N>.jpg
```

Examples:
- `tomato_late_blight_1.jpg`
- `tomato_healthy_1.jpg`
- `rice_brown_spot_1.jpg`
- `wheat_rust_1.png`

The crop prefix (before the first underscore) is extracted as the
`crop_hint` in the `/latest-capture` API response.

## Where to get images

Use any leaf disease dataset images (e.g. from PlantVillage) or take photos
of real leaves. The virtual camera module rotates through all images in this
folder every 15 seconds, simulating the auto-capture cycle of the hardware
camera unit.
