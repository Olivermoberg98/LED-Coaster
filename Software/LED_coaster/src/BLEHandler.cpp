#include "BLEHandler.h"
#include <iostream>
#include <cstdlib>
#include "patterns.h"
#include <esp_pm.h>

extern bool inner_needs_update;
extern bool outer_needs_update;

// Constructor that sets up the unique coaster ID
BLEHandler::BLEHandler(const std::string& coasterID) 
    : coasterID(coasterID), deviceConnected(false), connectionState(DISCONNECTED),
      isAdvertising(false), advertisingStartTime(0), disconnectAnimationPending(false) {}

void BLEHandler::begin() {
    // Configure BLE power settings for lower energy consumption
    NimBLEDevice::setPower(ESP_PWR_LVL_N0);
    
    // Initialize BLE and set the device name to include the coaster ID
    NimBLEDevice::init("Coaster-" + coasterID);
    pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks(this));  

    // Create a service and characteristic (example UUIDs used here)
    NimBLEService* pService = pServer->createService("00001801-0000-1000-8000-008051234567");
    pCharacteristic = pService->createCharacteristic(
        "00001234-0000-1000-8000-001122334455",
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE
    );

    // Start the service
    pCharacteristic->setCallbacks(new CharacteristicCallbacks(this));  
    pService->start();
    
    // Configure light sleep mode for power saving when idle (ESP32-C3 specific)
    esp_pm_config_esp32c3_t pm_config;
    pm_config.max_freq_mhz = 160;  // Use full C3 speed when active
    pm_config.min_freq_mhz = 10;   // Allow CPU to slow down when idle
    pm_config.light_sleep_enable = true; // Enable automatic light sleep
    esp_pm_configure(&pm_config);
    
    startAdvertising();

    // Initialize true for outer and inner checked
    innerChecked = true;
    outerChecked = true;
}

void BLEHandler::startAdvertising() {
    if (!isAdvertising) {
        NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
        pAdvertising->addServiceUUID("00001801-0000-1000-8000-008051234567");
        pAdvertising->setScanResponse(true);
        pAdvertising->setMinInterval(800);
        pAdvertising->setMaxInterval(1600);
        pAdvertising->start();
        isAdvertising = true;
        advertisingStartTime = millis();
        Serial.println("Started Advertising (low power mode)");
    }
}

void BLEHandler::stopAdvertising() {
    if (isAdvertising) {
        NimBLEDevice::getAdvertising()->stop();
        isAdvertising = false;
        Serial.println("Stopped Advertising - entering low power mode");
    }
}

void BLEHandler::updateAdvertising() {
    // If advertising and timeout reached, stop advertising to save power
    if (isAdvertising && !deviceConnected) {
        if (millis() - advertisingStartTime > ADVERTISING_TIMEOUT_MS) {
            stopAdvertising();
            Serial.println("Advertising timeout - stopped to conserve power");
        }
    }
}

bool BLEHandler::isConnected() {
    return deviceConnected;
}

void BLEHandler::updateConnectionState() {
    switch (connectionState) {
        case DISCONNECTED:
            if (deviceConnected) {
                Serial.println("State: DISCONNECTED -> CONNECTING");
                connectionState = CONNECTING;
            }
            break;
            
        case CONNECTING:
            Serial.println("State: CONNECTING - Playing connection animation");
            onConnectPattern(led_output_inner, NUM_LEDS_INNER, led_output_outer, NUM_LEDS_OUTER);
            connectionState = CONNECTED;
            Serial.println("State: CONNECTING -> CONNECTED");
            inner_needs_update = true;
            outer_needs_update = true;
            break;
            
        case CONNECTED:
            if (!deviceConnected) {
                Serial.println("State: CONNECTED -> DISCONNECTING");
                connectionState = DISCONNECTING;
            }
            break;
            
        case DISCONNECTING:
            if (disconnectAnimationPending) {
                onDisconnectPattern(led_output_inner, NUM_LEDS_INNER, led_output_outer, NUM_LEDS_OUTER);
                disconnectAnimationPending = false;
            }
            startAdvertising();
            Serial.println("State: DISCONNECTING -> DISCONNECTED");
            connectionState = DISCONNECTED;
            break;
    }
}

bool BLEHandler::shouldProcessPatterns() {
    return connectionState == CONNECTED;
}

void BLEHandler::resetConnectionState() {
    // Reset all flags to prepare for a clean reconnection
    package1Received = false;
    package2Received = false;
    inner_needs_update = true;
    outer_needs_update = true;
    Serial.println("Connection state reset");
}

void BLEHandler::ServerCallbacks::onConnect(NimBLEServer* pServer) {
    handler->deviceConnected = true;
    handler->isAdvertising = false;
    Serial.println("Device connected - advertising stopped");
}

void BLEHandler::ServerCallbacks::onDisconnect(NimBLEServer* pServer) {
    handler->deviceConnected = false;
    Serial.println("Device disconnected");

    // Reset connection state for clean reconnection
    handler->resetConnectionState();

    // Animation and re-advertising run from the main loop; this is the NimBLE host task
    handler->disconnectAnimationPending = true;
}

void BLEHandler::CharacteristicCallbacks::onWrite(NimBLECharacteristic* pCharacteristic) {
    std::string data = pCharacteristic->getValue();
    std::vector<byte> byteData(data.begin(), data.end());
    Serial.println("Data received");
    handler->handlePackage1(byteData);
    handler->handlePackage2(byteData);
}

void BLEHandler::handlePackage1(const std::vector<byte>& data) {
    // Layout: command, outer, inner, checksum
    if (data.size() != 4 || data[0] != 0x01) {
        return;
    }

    byte command = data[0];
    byte isOuterChecked = data[1];
    byte isInnerChecked = data[2];
    byte receivedChecksum = data[3];

    // Calculate checksum and verify it
    byte calculatedChecksum = (command + isOuterChecked + isInnerChecked) % 256;
    if (calculatedChecksum != receivedChecksum) {
        Serial.println("Package 1: checksum mismatch");
        return;
    }

    // Set flags based on the package data
    outerChecked = (isOuterChecked == 0x01);
    innerChecked = (isInnerChecked == 0x01);
    package1Received = true;
}

void BLEHandler::handlePackage2(const std::vector<byte>& data) {
    // Layout: command, pattern, ',', "R,G,B", checksum
    if (data.size() < 4 || data[0] != 0x02) {
        return;
    }

    // Calculate checksum and verify it
    byte receivedChecksum = data.back();
    byte checksumCalculated = 0;
    for (size_t i = 0; i < data.size() - 1; ++i) {
        checksumCalculated += data[i]; // Sum up all bytes except the checksum
    }
    if (checksumCalculated != receivedChecksum) {
        Serial.println("Package 2: checksum mismatch");
        return;
    }

    // Extract the mode and colors from the data (excluding command byte and checksum)
    std::string modeAndColorString(data.begin() + 1, data.end() - 1);
    size_t patternEndIndex = modeAndColorString.find(',');
    if (patternEndIndex == std::string::npos) {
        Serial.println("Package 2: missing pattern/color separator");
        return;
    }
    std::string pattern = modeAndColorString.substr(0, patternEndIndex);
    std::string colorString = modeAndColorString.substr(patternEndIndex + 1);

    // Exceptions are disabled in this build, so parsing must not throw
    int parsedColors[3];
    size_t start = 0;
    for (int i = 0; i < 3; ++i) {
        size_t end = colorString.find(',', start);
        if ((i < 2 && end == std::string::npos) || (i == 2 && end != std::string::npos)) {
            Serial.println("Package 2: expected exactly 3 color components");
            return;
        }

        std::string component = colorString.substr(start, end - start);
        char* parseEnd = nullptr;
        long value = strtol(component.c_str(), &parseEnd, 10);
        if (parseEnd == component.c_str() || *parseEnd != '\0') {
            Serial.println("Package 2: non-numeric color component");
            return;
        }

        parsedColors[i] = constrain(value, 0, 255);
        start = end + 1;
    }

    // Publish only once the whole packet has validated
    received_pattern = pattern;
    received_colors[0] = parsedColors[0];
    received_colors[1] = parsedColors[1];
    received_colors[2] = parsedColors[2];
    package2Received = true;

    std::cout << "Pattern type: " << received_pattern << std::endl;
}