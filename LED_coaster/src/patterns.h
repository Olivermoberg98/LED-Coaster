#ifndef PATTERN_H
#define PATTERN_H

#include <FastLED.h>

void pulse(CRGB* ledsIn,CRGB* ledsOut, int numberOfLeds, float pulseFrequency, float maxBrigthness, float minBrigthness);
void chaser(CRGB* ledsIn, CRGB* ledsOut, int numberOfLeds, int brightSpots,float roationalFrequency, float maxBrigthness, float minBrigthness);
// Chaser
// Beat

#endif