package com.leclowndu93150.foxy.fabricstub.loader.api.metadata;

import com.leclowndu93150.foxy.fabricstub.loader.api.Version;

public interface ModMetadata {
    Version getVersion();
    CustomValue getCustomValue(String key);
}
