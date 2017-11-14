package com.wellthapp.android.camera;

public final class OutputConfiguration {

    private final String name;

    private Directory directory;
    private Rotation rotation;
    private double size;
    private double quality;

    public OutputConfiguration(final String name) {
        this.name = name;
    }

    public OutputConfiguration(final String name, final Directory directory, final double size, final double quality) {
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.quality = quality;
    }

    public final String getName() {
        return this.name;
    }

    public final Directory getDirectory() {
        return this.directory;
    }

    public final Rotation getRotation() {
        return this.rotation;
    }

    public final void setDirectory(final Directory directory) {
        this.directory = directory;
    }

    public final double getSize() {
        return this.size;
    }

    public final void setSize(final double size) {
        this.size = size;
    }

    public final void setRotation(final Rotation rotation) {
        this.rotation = rotation;
    }

    public final double getQuality() {
        return this.quality;
    }

    public final void setQuality(final double quality) {
        this.quality = quality;
    }

    public enum Directory {
        Cache,
        Private,
        Public
    }

    public enum Rotation {
        Automatic,
        Portrait,
        Landscape,
    }

}
