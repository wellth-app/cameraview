package com.wellthapp.android.camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class OutputConfigurations {

    private final List<OutputConfiguration> outputConfigurationList = new ArrayList<>();

    public OutputConfigurations(final OutputConfiguration...configurations) {
        this.outputConfigurationList.clear();
        this.outputConfigurationList.addAll(Arrays.asList(configurations));
    }

    public OutputConfiguration getConfiguration(final int index) {
        return (index < this.outputConfigurationList.size()) ? this.outputConfigurationList.get(index) : null;
    }

    public final int getSize() {
        return this.outputConfigurationList.size();
    }

    public final void clear() {
        this.outputConfigurationList.clear();
    }

    public final void addConfiguration(final OutputConfiguration configuration) {
        if (!this.outputConfigurationList.contains(configuration)) {
            this.outputConfigurationList.add(configuration);
        }
    }

}
