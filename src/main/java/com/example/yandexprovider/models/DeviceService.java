package com.example.yandexprovider.models;

import com.example.yandexprovider.models.enums.ServiceType;

public class DeviceService {
    private String id;
    private String name;
    private ServiceType type;
    private String data;

    public DeviceService(String id, String name, ServiceType type, String data) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.data = data;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ServiceType getType() {
        return type;
    }

    public String getData() {
        return data;
    }


}