#include "BLEHandler.h"
#include <iostream>
#include "patterns.h"
#include <esp_pm.h>

extern bool inner_needs_update;
extern bool outer_needs_update;

// Constructor that sets up the unique coaster ID
BLEHandler::BLEHandler(const std::string& coasterID) 
    : coasterID(coasterID), deviceConnected(false), connectionState(DISCONNECTED),
      isAdvertising(false), advertisingStartTime(0) {}

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
            // Disconnect animation is handled in BLE callback
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
    
    // Play disconnect animation
    onDisconnectPattern(led_output_inner, NUM_LEDS_INNER, led_output_outer, NUM_LEDS_OUTER);
    
    // Restart advertising with timeout
    handler->startAdvertising();
}

void BLEHandler::CharacteristicCallbacks::onWrite(NimBLECharacteristic* pCharacteristic) {
    std::string data = pCharacteristic->getValue();
    std::vector<byte> byteData(data.begin(), data.end());
    Serial.println("Data received");
    handler->handlePackage1(byteData);
    handler->handlePackage2(byteData);
}

void BLEHandler::handlePackage1(const std::vector<byte>& data) {
    // Parse data bytes
    byte command = data[0];
    byte isOuterChecked = data[1];
    byte isInnerChecked = data[2];
    byte receivedChecksum = data.back();

    // Validate the command byte
    if (command != 0x01) {
        Serial.println("Invalid command byte for Package 1");
        return;
    }

    // Calculate checksum and verify it
    byte calculatedChecksum = (command + isOuterChecked + isInnerChecked) % 256;
    if (calculatedChecksum != receivedChecksum) {
        Serial.println("Checksum mismatch");
        return;
    }

    // Set flags based on the package data
    outerChecked = (isOuterChecked == 0x01);
    innerChecked = (isInnerChecked == 0x01);
    package1Received = true;
}

void BLEHandler::handlePackage2(const std::vector<byte>& data) {
    // Validate the command byte
    byte commandByte = data[0];
    if (commandByte != 0x02) {
        Serial.println("Invalid command byte for Package 2");
        return;
    }

    // Calculate checksum and verify it
    byte receivedChecksum = data.back();
    byte checksumCalculated = 0;
    for (size_t i = 0; i < data.size() - 1; ++i) {
        checksumCalculated += data[i]; // Sum up all bytes except the checksum
    }
    if (checksumCalculated % 256 != receivedChecksum) {
        Serial.println("Checksum mismatch.");
        return;
    }

    // Extract the mode and colors from the data (excluding command byte and checksum)
    std::string modeAndColorString(data.begin() + 1, data.end() - 1); 
    size_t patternEndIndex = modeAndColorString.find(','); 
    received_pattern = modeAndColorString.substr(0, patternEndIndex); 
    std::cout << "Pattern type: " << received_pattern << std::endl;

    // Extract color string
    std::string colorString = modeAndColorString.substr(patternEndIndex + 1);
    size_t start = 0;
    size_t end = colorString.find(',');

    // Parse the color values assuming "R,G,B" format
    for (int i = 0; i < 3; ++i) {
        end = colorString.find(',', start);
        received_colors[i] = std::stoi(colorString.substr(start, end - start));
        start = (end == std::string::npos) ? end : end + 1; // Move to next part
    }

    package2Received = true;
}