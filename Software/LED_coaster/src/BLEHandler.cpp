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
    std::vector<byte> byteData(data.begin(), data.end());
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
        Serial.println("Invalid command byte for Package 1");
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
    std::string mode = modeAndColorString.substr(0, patternEndIndex); 

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