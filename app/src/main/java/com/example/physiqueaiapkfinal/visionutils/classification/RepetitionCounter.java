/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.physiqueaiapkfinal.visionutils.classification;

import android.util.Log;

/**
 * Counts reps for the give class.
 */
public class RepetitionCounter {
  // Enhanced thresholds for better push-up detection accuracy
  // Lower thresholds to make detection more sensitive
  private static final float DEFAULT_ENTER_THRESHOLD = 4f;  // Decreased from 6f
  private static final float DEFAULT_EXIT_THRESHOLD = 2f;   // Decreased from 3f
  
  // Stricter thresholds specifically for push-ups
  private static final float PUSHUP_ENTER_THRESHOLD = 5f;   // Decreased from 7f
  private static final float PUSHUP_EXIT_THRESHOLD = 3f;    // Decreased from 4f
  
  private static final String TAG = "RepetitionCounter";

  private final String className;
  private final float enterThreshold;
  private final float exitThreshold;

  private int numRepeats;
  private boolean poseEntered;
  private long lastRepTime = 0;
  private static final long MIN_REP_INTERVAL_MS = 1000; // Minimum 1 second between reps

  public RepetitionCounter(String className) {
    // Use stricter thresholds for push-ups
    if (className.contains("pushups") || className.contains("pushup")) {
      this.className = className;
      this.enterThreshold = PUSHUP_ENTER_THRESHOLD;
      this.exitThreshold = PUSHUP_EXIT_THRESHOLD;
    } else {
      this.className = className;
      this.enterThreshold = DEFAULT_ENTER_THRESHOLD;
      this.exitThreshold = DEFAULT_EXIT_THRESHOLD;
    }
    
    numRepeats = 0;
    poseEntered = false;
    Log.d(TAG, "Created counter for " + className + 
        " with enter threshold: " + enterThreshold + 
        ", exit threshold: " + exitThreshold);
  }

  public RepetitionCounter(String className, float enterThreshold, float exitThreshold) {
    this.className = className;
    this.enterThreshold = enterThreshold;
    this.exitThreshold = exitThreshold;
    numRepeats = 0;
    poseEntered = false;
    Log.d(TAG, "Created counter for " + className + 
        " with enter threshold: " + enterThreshold + 
        ", exit threshold: " + exitThreshold);
  }

  /**
   * Adds a new Pose classification result and updates reps for given class.
   *
   * @param classificationResult {link ClassificationResult} of class to confidence values.
   * @return number of reps.
   */
  public int addClassificationResult(ClassificationResult classificationResult) {
    float poseConfidence = classificationResult.getClassConfidence(className);
    Log.d(TAG, "Class: " + className + ", Confidence: " + poseConfidence + 
        ", poseEntered: " + poseEntered + ", numRepeats: " + numRepeats);

    if (!poseEntered) {
      poseEntered = poseConfidence > enterThreshold;
      if (poseEntered) {
        Log.d(TAG, "Pose " + className + " ENTERED");
      }
      return numRepeats;
    }

    if (poseConfidence < exitThreshold) {
      // Check minimum interval between reps to prevent rapid false positives
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastRepTime >= MIN_REP_INTERVAL_MS) {
        numRepeats++;
        poseEntered = false;
        lastRepTime = currentTime;
        Log.d(TAG, "Pose " + className + " EXITED - Repetition counted! Count now: " + numRepeats);
      } else {
        Log.d(TAG, "Rep detected too soon, ignoring. Time since last: " + (currentTime - lastRepTime) + "ms");
        poseEntered = false; // Still exit the pose but don't count
      }
    }

    return numRepeats;
  }

  public String getClassName() {
    return className;
  }

  public int getNumRepeats() {
    return numRepeats;
  }
  
  public void reset() {
    numRepeats = 0;
    poseEntered = false;
    lastRepTime = 0;
    Log.d(TAG, "Reset counter for " + className);
  }
}
