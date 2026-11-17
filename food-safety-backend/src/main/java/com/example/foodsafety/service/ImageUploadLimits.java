package com.example.foodsafety.service;

final class ImageUploadLimits {
    static final long MAX_IMAGE_SIZE_BYTES = 4L * 1024 * 1024;
    static final String MAX_IMAGE_SIZE_MESSAGE = "Image must be 4 MB or smaller";

    private ImageUploadLimits() {
    }
}
