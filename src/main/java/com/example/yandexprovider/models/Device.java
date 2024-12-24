package com.example.yandexprovider.models;

import java.util.List;

public class Device {
    private String id;
    private String name;
    private boolean status;
    private List<DeviceService> services;

    public Device(String id, String name, boolean status, List<DeviceService> services) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.services = services;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isStatus() {
        return status;
    }

    public List<DeviceService> getServices() {
        return services;
    }
}