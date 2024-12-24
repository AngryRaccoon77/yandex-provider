package com.example.yandexprovider;

import com.example.yandexprovider.models.Device;
import com.example.yandexprovider.models.DeviceService;
import com.example.yandexprovider.models.enums.ServiceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
public class YandexProviderApplication {
    static final String queueName = "deviceStatusQueue1";

    @RabbitListener(queues = queueName)
    public void listen(String message) {
        System.out.println(" [x] Received '" + message + "'");
        try {
            Device device = parseMessage(message);
            Map<String, Object> apiPayload = convertToApiFormat(device);

            ObjectMapper objectMapper = new ObjectMapper();
            String apiJson = objectMapper.writeValueAsString(apiPayload);

            System.out.println("API Payload: " + apiJson);
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Device parseMessage(String message) {
        // Base device parsing logic remains the same
        Pattern devicePattern = Pattern.compile("Device\\{id=(.*?), name='(.*?)', status=(.*?), services=\\[(.*?)\\]\\}");
        Matcher deviceMatcher = devicePattern.matcher(message);

        if (!deviceMatcher.find()) {
            throw new IllegalArgumentException("Invalid device message format");
        }

        String id = deviceMatcher.group(1);
        String name = deviceMatcher.group(2);
        boolean status = Boolean.parseBoolean(deviceMatcher.group(3));
        String servicesRaw = deviceMatcher.group(4);

        List<DeviceService> services = parseServices(servicesRaw);

        return new Device(id, name, status, services);
    }

    private List<DeviceService> parseServices(String servicesRaw) {
        // Services parsing logic remains the same
        List<DeviceService> services = new ArrayList<>();
        if (servicesRaw.isEmpty()) {
            return services;
        }

        Pattern servicePattern = Pattern.compile("DeviceService\\{id=(.*?), name='(.*?)', type=(.*?), data='?(\\{.*?\\})'?\\}");
        Matcher serviceMatcher = servicePattern.matcher(servicesRaw);

        while (serviceMatcher.find()) {
            String id = serviceMatcher.group(1);
            String name = serviceMatcher.group(2);
            String type = serviceMatcher.group(3);
            String data = serviceMatcher.group(4);

            services.add(new DeviceService(id, name, ServiceType.valueOf(type), data));
        }

        return services;
    }

    private Map<String, Object> convertToApiFormat(Device device) {
        Map<String, Object> apiDevice = new HashMap<>();
        apiDevice.put("id", device.getId());
        apiDevice.put("name", device.getName());
        apiDevice.put("type", determineDeviceType(device.getServices()));
        apiDevice.put("description", "Smart home device");
        apiDevice.put("custom_data", Map.of("status", device.isStatus()));
        apiDevice.put("capabilities", buildCapabilities(device.getServices()));
        apiDevice.put("properties", buildProperties(device.getServices()));

        return apiDevice;
    }

    private String determineDeviceType(List<DeviceService> services) {
        // Determine device type based on primary service
        if (services.isEmpty()) return "sensor";

        ServiceType primaryService = services.get(0).getType();
        switch (primaryService) {
            case LIGHT:
                return "light";
            case SWITCH:
                return "switch";
            case TEMPERATURE:
            case HUMIDITY:
                return "sensor";
            case DOOR:
            case WINDOW:
                return "openable";
            default:
                return "sensor";
        }
    }

    private List<Map<String, Object>> buildCapabilities(List<DeviceService> services) {
        List<Map<String, Object>> capabilities = new ArrayList<>();

        for (DeviceService service : services) {
            Map<String, Object> capability = new HashMap<>();
            Map<String, Object> state = new HashMap<>();

            switch (service.getType()) {
                case WATERLEAK:
                case SMOKE:
                case GAS:
                case MOTION:
                    capability.put("type", "event");
                    state.put("instance", service.getType().toString().toLowerCase());
                    state.put("value", parseServiceData(service.getData()).getOrDefault("detected", false));
                    break;

                case TEMPERATURE:
                    capability.put("type", "range");
                    state.put("instance", "temperature");
                    state.put("value", parseServiceData(service.getData()).getOrDefault("temperature", 20));
                    capability.put("parameters", Map.of(
                            "range", Map.of(
                                    "min", -50,
                                    "max", 100,
                                    "precision", 0.1
                            ),
                            "unit", "unit.temperature.celsius"
                    ));
                    break;

                case HUMIDITY:
                    capability.put("type", "range");
                    state.put("instance", "humidity");
                    state.put("value", parseServiceData(service.getData()).getOrDefault("humidity", 50));
                    capability.put("parameters", Map.of(
                            "range", Map.of(
                                    "min", 0,
                                    "max", 100,
                                    "precision", 1
                            ),
                            "unit", "unit.percent"
                    ));
                    break;

                case DOOR:
                case WINDOW:
                    capability.put("type", "toggle");
                    state.put("instance", service.getType().toString().toLowerCase());
                    state.put("value", parseServiceData(service.getData()).getOrDefault("opened", false));
                    break;

                case LIGHT:
                    addLightCapabilities(capabilities, service);
                    continue;

                case SWITCH:
                    capability.put("type", "on_off");
                    state.put("instance", "on");
                    state.put("value", parseServiceData(service.getData()).getOrDefault("enabled", false));
                    break;
            }

            capability.put("retrievable", true);
            capability.put("reportable", true);
            capability.put("state", state);
            capabilities.add(capability);
        }

        return capabilities;
    }

    private void addLightCapabilities(List<Map<String, Object>> capabilities, DeviceService service) {
        // Basic on/off capability
        capabilities.add(Map.of(
                "type", "on_off",
                "retrievable", true,
                "reportable", true,
                "state", Map.of(
                        "instance", "on",
                        "value", parseServiceData(service.getData()).getOrDefault("enabled", false)
                )
        ));

        // Brightness capability
        Map<String, Object> data = parseServiceData(service.getData());
        if (data.containsKey("brightness")) {
            capabilities.add(Map.of(
                    "type", "range",
                    "retrievable", true,
                    "reportable", true,
                    "parameters", Map.of(
                            "range", Map.of(
                                    "min", 1,
                                    "max", 100,
                                    "precision", 1
                            ),
                            "unit", "unit.percent"
                    ),
                    "state", Map.of(
                            "instance", "brightness",
                            "value", data.get("brightness")
                    )
            ));
        }

        // Color setting capability
        if (data.containsKey("color") || data.containsKey("temperature_k")) {
            Map<String, Object> colorCapability = new HashMap<>();
            colorCapability.put("type", "color_setting");
            colorCapability.put("retrievable", true);
            colorCapability.put("reportable", true);

            if (data.containsKey("color")) {
                colorCapability.put("parameters", Map.of(
                        "color_model", "rgb"
                ));
                colorCapability.put("state", Map.of(
                        "instance", "rgb",
                        "value", data.get("color")
                ));
            } else {
                colorCapability.put("parameters", Map.of(
                        "temperature_k", Map.of(
                                "min", 2700,
                                "max", 6500,
                                "precision", 100
                        )
                ));
                colorCapability.put("state", Map.of(
                        "instance", "temperature_k",
                        "value", data.get("temperature_k")
                ));
            }

            capabilities.add(colorCapability);
        }
    }

    private List<Map<String, Object>> buildProperties(List<DeviceService> services) {
        List<Map<String, Object>> properties = new ArrayList<>();

        for (DeviceService service : services) {
            Map<String, Object> data = parseServiceData(service.getData());

            // Battery level property
            if (data.containsKey("battery")) {
                properties.add(Map.of(
                        "type", "float",
                        "retrievable", true,
                        "reportable", true,
                        "parameters", Map.of(
                                "unit", "unit.percent"
                        ),
                        "state", Map.of(
                                "instance", "battery_level",
                                "value", data.get("battery")
                        )
                ));
            }

            // Signal strength property
            if (data.containsKey("rsi")) {
                properties.add(Map.of(
                        "type", "float",
                        "retrievable", true,
                        "reportable", true,
                        "parameters", Map.of(
                                "unit", "unit.percent"
                        ),
                        "state", Map.of(
                                "instance", "signal_strength",
                                "value", data.get("rsi")
                        )
                ));
            }

            // Event-based properties for sensors
            switch (service.getType()) {
                case WATERLEAK:
                case SMOKE:
                case GAS:
                case MOTION:
                    properties.add(Map.of(
                            "type", "event",
                            "retrievable", true,
                            "reportable", true,
                            "parameters", Map.of(
                                    "events", List.of(
                                            Map.of("value", "detected"),
                                            Map.of("value", "not_detected")
                                    )
                            ),
                            "state", Map.of(
                                    "instance", service.getType().toString().toLowerCase(),
                                    "value", (Boolean) data.getOrDefault("detected", false) ? "detected" : "not_detected"                            )
                    ));
                    break;
            }
        }

        return properties;
    }

    private Map<String, Object> parseServiceData(String data) {
        Map<String, Object> result = new HashMap<>();
        // Remove curly braces
        data = data.substring(1, data.length() - 1);

        // Split into key-value pairs
        String[] pairs = data.split(",\\s*");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":\\s*");
            if (keyValue.length == 2) {
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();

                // Try parsing as number
                try {
                    if (value.contains(".")) {
                        result.put(key, Double.parseDouble(value));
                    } else {
                        result.put(key, Integer.parseInt(value));
                    }
                } catch (NumberFormatException e) {
                    // If not a number, store as boolean or string
                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                        result.put(key, Boolean.parseBoolean(value));
                    } else {
                        // Remove quotes if present
                        value = value.replaceAll("^\"|\"$", "");
                        result.put(key, value);
                    }
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        SpringApplication.run(YandexProviderApplication.class, args);
    }
}