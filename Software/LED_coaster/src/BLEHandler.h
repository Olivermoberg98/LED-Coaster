#ifndef BLE_HANDLER_H
#define BLE_HANDLER_H

#include <NimBLEDevice.h>

class BLEHandler {
public:
    BLEHandler(const std::string& coasterID); // Constructor with a unique ID
    void begin();
    void startAdvertising();
    bool isConnected();

    // Flags for the package status
    bool package1Received = false;
    bool package2Received = false;
    bool outerChecked = false;
    bool innerChecked = false;
    std::string received_pattern;
    int received_colors[3];

    // Callbacks for data receiving events
    void handlePackage1(const std::string& data);
    void handlePackage2(const std::string& data);

private:
    std::string coasterID;
    NimBLEServer* pServer;
    NimBLECharacteristic* pCharacteristic;
    bool deviceConnected;

    // Callbacks for connection and disconnection events
    class ServerCallbacks : public NimBLEServerCallbacks {
    public:
        ServerCallbacks(BLEHandler* handler) : handler(handler) {}
        void onConnect(NimBLEServer* pServer) override;
        void onDisconnect(NimBLEServer* pServer) override;
    private:
        BLEHandler* handler;  // Pointer to BLEHandler instance
    };
    friend class ServerCallbacks;

    // Callback for handling received data
    class CharacteristicCallbacks : public NimBLECharacteristicCallbacks {
    public:
        CharacteristicCallbacks(BLEHandler* handler) : handler(handler) {}
        void onWrite(NimBLECharacteristic* pCharacteristic) override;
    private:
        BLEHandler* handler;
    };
};

#endif // BLE_HANDLER_H
