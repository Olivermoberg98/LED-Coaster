#ifndef PATTERN_H
#define PATTERN_H
#include <FastLED.h>

#define LED_PIN_INNER     0
#define LED_PIN_OUTER     1
#define NUM_LEDS_INNER    10
#define NUM_LEDS_OUTER    20

extern CRGB colors_inner[NUM_LEDS_INNER];
extern CRGB colors_outer[NUM_LEDS_OUTER];

enum PatternType {
    FIXED,
    CHASER,
    PULSE
};

PatternType stringToPatternType(const std::string& pattern);

void fixed(CRGB* ledsIn,CRGB* ledsOut, int numberOfLeds);
void pulse(CRGB* ledsIn,CRGB* ledsOut, int numberOfLeds, float pulseFrequency, float maxBrigthness, float minBrigthness);
void chaser(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds, int brightSpots,float roationalFrequency, float maxBrigthness, float minBrigthness);
void clearRing(CRGB* leds, int numLeds);
void runPattern(PatternType pattern, CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds);
void updateLEDColors(int ring, int NUM_LEDS, const int colors[3]);
void updateLEDColors(int ring, int NUM_LEDS);
void onDisconnectPattern(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds);
void onConnectPattern(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds);

#endif