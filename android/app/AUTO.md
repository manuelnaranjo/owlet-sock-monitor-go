# Debugging with Android Auto

```bash
$ adb shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity
# Now from burger menu start the server
$ adb forward tcp:5277 tcp:5277
$ ${ANDROID_SDK_ROOT}/extras/google/auto/desktop-head-unit
```
