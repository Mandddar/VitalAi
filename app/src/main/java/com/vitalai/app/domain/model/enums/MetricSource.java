package com.vitalai.app.domain.model.enums;

public enum MetricSource {
    BLE,           // Bluetooth Low Energy wearable
    HEALTH_CONNECT,
    CSV_IMPORT,
    SYNTHETIC,     // AI-generated / interpolated
    MANUAL         // User-entered
}