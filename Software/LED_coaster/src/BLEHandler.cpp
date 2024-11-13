#include "BLEHandler.h"

// Constructor that sets up the unique coaster ID
BLEHandler::BLEHandler(const std::string& coasterID) 
    : coasterID(coasterID), deviceConnected(false) {}

void BLEHandler::begin() {
    // Initialize BLE and set the device name to include the coaster ID
    NimBLEDevice::init("Coaster-" + coasterID);
    pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks(this));  

    // Create a service and characteristic (example UUIDs used here)
    NimBLEService* pService = pServer->createService("12345678-1234-1234-1234-1234567890AB");
    pCharacteristic = pService->createCharacteristic(
        "87654321-4321-4321-4321-BA0987654321",
        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE
    );

    // Start the service
    pCharacteristic->setCallbacks(new CharacteristicCallbacks(this));  
    pService->start();
    startAdvertising();

    //Maybe initialize true for outer and inner checked
    innerChecked = true;
    outerChecked = true;
}

void BLEHandler::startAdvertising() {
    NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
    pAdvertising->addServiceUUID("12345678-1234-1234-1234-1234567890AB");
    pAdvertising->setScanResponse(true);
    pAdvertising->start();
}

bool BLEHandler::isConnected() {
    return deviceConnected;
}

// ServerCallbacks implementation
void BLEHandler::ServerCallbacks::onConnect(NimBLEServer* pServer) {
    handler->deviceConnected = true;  
    Serial.println("Device connected");
}

void BLEHandler::ServerCallbacks::onDisconnect(NimBLEServer* pServer) {
    handler->deviceConnected = false;  
    Serial.println("Device disconnected");
    pServer->startAdvertising(); 
}

void BLEHandler::CharacteristicCallbacks::onWrite(NimBLECharacteristic* pCharacteristic) {
    std::string data = pCharacteristic->getValue();
    handler->handlePackage1(data);
}

void BLEHandler::handlePackage1(const std::string& data) {
    // Check minimum length for Package 1 (4 bytes: command, outer, inner, checksum)
    if (data.size() != 4) {
        Serial.println("Invalid data length for Package 1");
        return;
    }

    // Parse data bytes
    uint8_t command = data[0];
    uint8_t isOuterChecked = data[1];
    uint8_t isInnerChecked = data[2];
    uint8_t receivedChecksum = data[3];

    // Validate the command byte
    if (command != 0x01) {
        Serial.println("Invalid command byte for Package 1");
        return;
    }

    // Calculate checksum
    uint8_t calculatedChecksum = (command + isOuterChecked + isInnerChecked) % 256;
    if (calculatedChecksum != receivedChecksum) {
        Serial.println("Checksum mismatch");
        return;
    }

    // Set flags based on the package data
    outerChecked = (isOuterChecked == 0x01);
    innerChecked = (isInnerChecked == 0x01);
    package1Received = true;

    // Print the data
    Serial.print("Package 1 received: ");
    Serial.print("OuterChecked = ");
    Serial.print(outerChecked);
    Serial.print(", InnerChecked = ");
    Serial.println(innerChecked);
}

void BLEHandler::handlePackage2(const std::string& data) {
    // Assuming the data is in the format: [command byte] [mode string] [color string] [checksum]

    // Extract the command byte (0x02)
    byte commandByte = static_cast<byte>(data[0]);

    // Check if the command byte matches the expected one
    if (commandByte != 0x02) {
        Serial.println("Invalid command byte.");
        return;
    }

    // Extract the mode and colors from the received string.
    size_t modeEndIndex = data.find(','); // Look for the first comma to separate mode
    std::string mode = data.substr(1, modeEndIndex - 1); // Extract mode from data (after command byte)

    // Extract color data (skip past the mode and comma)
    std::string colorString = data.substr(modeEndIndex + 1); // This will be the color string part
    std::vector<int> colors;
    size_t start = 0;
    size_t end = colorString.find(',');

    // Parse the color values (expecting a "R,G,B" format)
    while (end != std::string::npos) {
        colors.push_back(std::stoi(colorString.substr(start, end - start)));
        start = end + 1;
        end = colorString.find(',', start);
    }
    colors.push_back(std::stoi(colorString.substr(start))); // Add the last color value (Blue)

    // Calculate the checksum and verify it.
    byte receivedChecksum = static_cast<byte>(data.back()); // The last byte is the checksum
    byte checksumCalculated = 0;

    for (size_t i = 0; i < data.length() - 1; ++i) {
        checksumCalculated += data[i]; // Sum up all bytes except the checksum
    }

    if (checksumCalculated % 256 != receivedChecksum) {
        Serial.println("Checksum mismatch.");
        return; // Data is corrupted
    }

    // Handle the pattern.
}