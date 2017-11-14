package com.google.android.cameraview;

import java.util.List;
import java.util.Map;

public interface ReactNativeEventListener {
    void emitEvent(final List<Map<String, String>> mapList);
}
