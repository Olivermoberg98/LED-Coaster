#ifndef BLE_HANDLER_H
#define BLE_HANDLER_H

#include <NimBLEDevice.h>
#include <FastLED.h>

extern CRGB led_output_inner[];
extern CRGB led_output_outer[];

// Connection state machine
enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
};

class BLEHandler {
public:
    BLEHandler(const std::string& coasterID); // Constructor with a unique ID
    void begin();
    void startAdvertising();
    void stopAdvertising();
    void updateAdvertising();
    bool isConnected();
    void updateConnectionState(); // Update state machine
    bool shouldProcessPatterns(); // Check if patterns should be processed
    void resetConnectionState(); // Reset state on disconnect

    // Flags for the package status
    bool package1Received = false;
    bool package2Received = false;
    bool outerChecked = false;
    bool innerChecked = false;
    std::string received_pattern;
    int received_colors[3];

    // Callbacks for data receiving events
    void handlePackage1(const std::vector<byte>& data);
    void handlePackage2(const std::vector<byte>& data);
    bool deviceConnected;

    // Battery status: setStatus updates the readable values, notifyStatus pushes them
    void setStatus(const uint8_t* package3, size_t length, uint8_t percent);
    void notifyStatus();
    // Set from the NimBLE host task when a client subscribes, consumed by the main loop
    volatile bool statusSubscribePending;

private:
    std::string coasterID;
    NimBLEServer* pServer;
    NimBLECharacteristic* pCharacteristic;
    NimBLECharacteristic* pStatusCharacteristic;
    NimBLECharacteristic* pBatteryLevelCharacteristic;
    ConnectionState connectionState;

    unsigned long advertisingStartTime;
    bool isAdvertising;
    // Set from the NimBLE host task, consumed by the main loop
    volatile bool disconnectAnimationPending;
    static const unsigned long ADVERTISING_TIMEOUT_MS = 120000; // 2 minutes

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

    // Callback for notification subscriptions on the status characteristics
    class StatusCallbacks : public NimBLECharacteristicCallbacks {
    public:
        StatusCallbacks(BLEHandler* handler) : handler(handler) {}
        void onSubscribe(NimBLECharacteristic* pCharacteristic, ble_gap_conn_desc* desc, uint16_t subValue) override;
    private:
        BLEHandler* handler;
    };
};

#endif // BLE_HANDLER_H