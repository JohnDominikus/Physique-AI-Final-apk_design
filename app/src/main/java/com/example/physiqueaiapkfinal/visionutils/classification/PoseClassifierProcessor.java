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

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.google.common.base.Preconditions;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Accepts a stream of {@link Pose} for classification and Rep counting.
 */
public class PoseClassifierProcessor {
  private static final String TAG = "PoseClassifierProcessor";
  private static final String POSE_SAMPLES_FILE = "pose/fitness_pose_samples.csv";

  // Specify classes for which we want rep counting.
  // These are the labels in the given {@code POSE_SAMPLES_FILE}. You can set your own class labels
  // for your pose samples.
  private static final String PUSHUPS_CLASS = "pushups_down";
  private static final String SQUATS_CLASS = "squats_down";
  private static final String FRONT_RAISE_CLASS = "front_raise_down";
  private static final String HIP_THRUST_CLASS = "hip_thrust_down";
  private static final String WINDMILL_LEFT_CLASS = "windmill_left";
  private static final String WINDMILL_RIGHT_CLASS = "windmill_right";
  private static final String[] POSE_CLASSES = {
          PUSHUPS_CLASS,
          SQUATS_CLASS,
          FRONT_RAISE_CLASS,
          HIP_THRUST_CLASS,
          WINDMILL_LEFT_CLASS,
          WINDMILL_RIGHT_CLASS
  };

  private final boolean isStreamMode;

  private EMASmoothing emaSmoothing;
  private List<RepetitionCounter> repCounters;
  private PoseClassifier poseClassifier;
  private String lastRepResult;

  // Enhanced validation variables
  private int validPoseFrameCount = 0;
  private static final int MIN_VALID_POSE_FRAMES = 3;

  @WorkerThread
  public PoseClassifierProcessor(Context context, boolean isStreamMode) {
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
    this.isStreamMode = isStreamMode;
    if (isStreamMode) {
      emaSmoothing = new EMASmoothing();
      repCounters = new ArrayList<>();
      lastRepResult = "Exercise: 0 reps";
    }
    loadPoseSamples(context);
  }

  private void loadPoseSamples(Context context) {
    List<com.example.physiqueaiapkfinal.visionutils.classification.PoseSample> poseSamples = new ArrayList<>();
    try {
      Log.d(TAG, "Loading pose samples from " + POSE_SAMPLES_FILE);
      BufferedReader reader = new BufferedReader(
              new InputStreamReader(context.getAssets().open(POSE_SAMPLES_FILE)));
      String csvLine = reader.readLine();
      int sampleCount = 0;
      while (csvLine != null) {
        // If line is not a valid {@link PoseSample}, we'll get null and skip adding to the list.
        com.example.physiqueaiapkfinal.visionutils.classification.PoseSample poseSample = com.example.physiqueaiapkfinal.visionutils.classification.PoseSample.getPoseSample(csvLine, ",");
        if (poseSample != null) {
          poseSamples.add(poseSample);
          sampleCount++;
        } else {
          Log.w(TAG, "Could not parse pose sample: " + csvLine);
        }
        csvLine = reader.readLine();
      }
      Log.d(TAG, "Loaded " + sampleCount + " pose samples.");

      // List all loaded sample class names
      Set<String> classes = new HashSet<>();
      for (PoseSample sample : poseSamples) {
        classes.add(sample.getClassName());
      }
      Log.d(TAG, "Available classes: " + classes);
    } catch (IOException e) {
      Log.e(TAG, "Error when loading pose samples: " + e.getMessage());
      e.printStackTrace();
    }
    poseClassifier = new PoseClassifier(poseSamples);
    if (isStreamMode) {
      for (String className : POSE_CLASSES) {
        repCounters.add(new com.example.physiqueaiapkfinal.visionutils.classification.RepetitionCounter(className));
        Log.d(TAG, "Added RepetitionCounter for " + className);
      }
    }
  }

  /**
   * Given a new {@link Pose} input, returns a list of formatted {@link String}s with Pose
   * classification results.
   *
   * <p>Currently it returns up to 2 strings as following:
   * 0: PoseClass : X reps
   * 1: PoseClass : [0.0-1.0] confidence
   */
  @WorkerThread
  public List<String> getPoseResult(Pose pose) {
    // Make sure we're on a worker thread
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper(),
            "getPoseResult should not be called on the main thread");

    List<String> result = new ArrayList<>();
    Log.d(TAG, "Classifying pose with " + pose.getAllPoseLandmarks().size() + " landmarks");

    ClassificationResult classification = poseClassifier.classify(pose);
    Log.d(TAG, "Raw classification result: " + classification.getAllClasses());

    // Update {@link RepetitionCounter}s if {@code isStreamMode}.
    if (isStreamMode) {
      // Feed pose to smoothing even if no pose found.
      ClassificationResult smoothedResult = emaSmoothing.getSmoothedResult(classification);
      Log.d(TAG, "Smoothed classification result: " + smoothedResult.getAllClasses());

      // Return early without updating repCounter if no pose found.
      if (pose.getAllPoseLandmarks().isEmpty()) {
        Log.d(TAG, "No landmarks found, returning last rep result: " + lastRepResult);
        validPoseFrameCount = 0;
        result.add(lastRepResult);
        return result;
      }

      // Enhanced pose validation for push-ups, squats, front raises, hip thrusts, and windmills
      boolean isPushupPose = validatePushupPose(pose, smoothedResult);
      boolean isSquatPose = validateSquatPose(pose, smoothedResult);
      boolean isFrontRaisePose = validateFrontRaisePose(pose, smoothedResult);
      boolean isHipThrustPose = validateHipThrustPose(pose, smoothedResult);
      boolean isWindmillPose = validateWindmillPose(pose, smoothedResult);
      boolean isValidPose = isPushupPose || isSquatPose || isFrontRaisePose || isHipThrustPose || isWindmillPose;

      if (isValidPose) {
        validPoseFrameCount++;
      } else {
        validPoseFrameCount = 0;
      }

      // Only proceed with counting if we have enough consecutive valid pose frames
      if (validPoseFrameCount < MIN_VALID_POSE_FRAMES) {
        Log.d(TAG, "Not enough valid pose frames: " + validPoseFrameCount + "/" + MIN_VALID_POSE_FRAMES);
        result.add(lastRepResult);
        return result;
      }

      for (com.example.physiqueaiapkfinal.visionutils.classification.RepetitionCounter repCounter : repCounters) {
        String className = repCounter.getClassName();
        int repsBefore = repCounter.getNumRepeats();
        int repsAfter = repCounter.addClassificationResult(smoothedResult);

        float currentConfidence = smoothedResult.getClassConfidence(className);
        Log.d(TAG, "Class: " + className + ", Confidence: " + currentConfidence +
                ", Reps before: " + repsBefore + ", Reps after: " + repsAfter);

        if (repsAfter > repsBefore) {
          // Play a fun beep when rep counter updates.
          ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
          tg.startTone(ToneGenerator.TONE_PROP_BEEP);
          lastRepResult = String.format(
                  Locale.US, "%s : %d reps", repCounter.getClassName(), repsAfter);
          Log.d(TAG, "Rep count increased! New result: " + lastRepResult);
          break;
        }
      }

      if (lastRepResult.isEmpty()) {
        lastRepResult = "Exercise: 0 reps";
      }

      result.add(lastRepResult);
      Log.d(TAG, "Added to result: " + lastRepResult);
    }

    // Add maxConfidence class of current frame to result if pose is found.
    if (!pose.getAllPoseLandmarks().isEmpty()) {
      String maxConfidenceClass = classification.getMaxConfidenceClass();
      float maxConfidence = classification.getClassConfidence(maxConfidenceClass)
              / poseClassifier.confidenceRange();

      String maxConfidenceClassResult = String.format(
              Locale.US,
              "%s : %.2f confidence",
              maxConfidenceClass,
              maxConfidence);
      result.add(maxConfidenceClassResult);
      Log.d(TAG, "Added max confidence class to result: " + maxConfidenceClassResult);
    }

    Log.d(TAG, "Final result: " + result);
    return result;
  }

  // Enhanced validation method for push-up poses
  private boolean validatePushupPose(Pose pose, ClassificationResult classificationResult) {
    try {
      // Check if we have the necessary landmarks for push-up validation
      PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
      PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
      PoseLandmark leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
      PoseLandmark rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
      PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
      PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
      PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
      PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);

      if (leftShoulder == null || rightShoulder == null || leftElbow == null ||
              rightElbow == null || leftWrist == null || rightWrist == null ||
              leftHip == null || rightHip == null) {
        Log.d(TAG, "Missing required landmarks for push-up validation");
        return false;
      }

      // Check wrist position relative to shoulders (should be below for push-ups)
      boolean wristsLowerThanShoulders = (leftWrist.getPosition().y > leftShoulder.getPosition().y &&
              rightWrist.getPosition().y > rightShoulder.getPosition().y);

      // Check if body is in horizontal position
      float shoulderY = (leftShoulder.getPosition().y + rightShoulder.getPosition().y) / 2;
      float hipY = (leftHip.getPosition().y + rightHip.getPosition().y) / 2;
      boolean isHorizontal = Math.abs(shoulderY - hipY) < 0.15f;

      // Check minimum confidence for push-up classification
      float pushupConfidence = classificationResult.getClassConfidence(PUSHUPS_CLASS);
      boolean hasMinConfidence = pushupConfidence > 2.0f; // Minimum confidence threshold

      boolean isValid = wristsLowerThanShoulders && isHorizontal && hasMinConfidence;

      Log.d(TAG, "Push-up pose validation - Wrists lower: " + wristsLowerThanShoulders +
              ", Horizontal: " + isHorizontal +
              ", Confidence: " + pushupConfidence +
              ", Valid: " + isValid);

      return isValid;

    } catch (Exception e) {
      Log.e(TAG, "Error in push-up pose validation: " + e.getMessage(), e);
      return false;
    }
  }

  // Enhanced validation method for squat poses
  private boolean validateSquatPose(Pose pose, ClassificationResult classificationResult) {
    try {
      // Check if we have the necessary landmarks for squat validation
      PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
      PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
      PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
      PoseLandmark rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
      PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
      PoseLandmark rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);
      PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
      PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

      if (leftHip == null || rightHip == null || leftKnee == null ||
              rightKnee == null || leftAnkle == null || rightAnkle == null ||
              leftShoulder == null || rightShoulder == null) {
        Log.d(TAG, "Missing required landmarks for squat validation");
        return false;
      }

      // More lenient position checks for squats
      float shoulderY = (leftShoulder.getPosition().y + rightShoulder.getPosition().y) / 2;
      float hipY = (leftHip.getPosition().y + rightHip.getPosition().y) / 2;
      float kneeY = (leftKnee.getPosition().y + rightKnee.getPosition().y) / 2;
      float ankleY = (leftAnkle.getPosition().y + rightAnkle.getPosition().y) / 2;

      // Check if person is generally upright (not horizontal like push-ups)
      boolean isUpright = shoulderY < hipY; // Shoulders above hips is enough

      // Check if legs are visible and positioned correctly
      boolean legsAreVisible = hipY < ankleY; // Hips above ankles

      // Lower confidence threshold for squat classification
      float squatConfidence = classificationResult.getClassConfidence(SQUATS_CLASS);
      boolean hasMinConfidence = squatConfidence > 1.0f; // Lowered threshold

      boolean isValid = isUpright && legsAreVisible && hasMinConfidence;

      Log.d(TAG, "Squat pose validation - Upright: " + isUpright +
              ", Legs visible: " + legsAreVisible +
              ", Confidence: " + squatConfidence +
              ", Valid: " + isValid);

      return isValid;

    } catch (Exception e) {
      Log.e(TAG, "Error in squat pose validation: " + e.getMessage(), e);
      return false;
    }
  }

  // Enhanced validation method for front raise poses
  private boolean validateFrontRaisePose(Pose pose, ClassificationResult classificationResult) {
    try {
      // Check if we have the necessary landmarks for front raise validation
      PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
      PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
      PoseLandmark leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
      PoseLandmark rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
      PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
      PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
      PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
      PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);

      if (leftShoulder == null || rightShoulder == null || leftElbow == null ||
              rightElbow == null || leftWrist == null || rightWrist == null ||
              leftHip == null || rightHip == null) {
        Log.d(TAG, "Missing required landmarks for front raise validation");
        return false;
      }

      // Check if person is standing upright (not horizontal like push-ups)
      float shoulderY = (leftShoulder.getPosition().y + rightShoulder.getPosition().y) / 2;
      float hipY = (leftHip.getPosition().y + rightHip.getPosition().y) / 2;
      boolean isUpright = shoulderY < hipY; // Shoulders above hips for standing position

      // Check if arms are visible and positioned correctly
      float avgWristY = (leftWrist.getPosition().y + rightWrist.getPosition().y) / 2;
      boolean armsAreVisible = avgWristY > 0; // Basic check that arms are visible

      // Lower confidence threshold for front raise classification
      float frontRaiseConfidence = classificationResult.getClassConfidence(FRONT_RAISE_CLASS);
      boolean hasMinConfidence = frontRaiseConfidence > 1.0f; // Lowered threshold

      boolean isValid = isUpright && armsAreVisible && hasMinConfidence;

      Log.d(TAG, "Front raise pose validation - Upright: " + isUpright +
              ", Arms visible: " + armsAreVisible +
              ", Confidence: " + frontRaiseConfidence +
              ", Valid: " + isValid);

      return isValid;

    } catch (Exception e) {
      Log.e(TAG, "Error in front raise pose validation: " + e.getMessage(), e);
      return false;
    }
  }

  // Enhanced validation method for hip thrust poses
  private boolean validateHipThrustPose(Pose pose, ClassificationResult classificationResult) {
    try {
      // Check if we have the necessary landmarks for hip thrust validation
      PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
      PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
      PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
      PoseLandmark rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
      PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
      PoseLandmark rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);
      PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
      PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

      if (leftHip == null || rightHip == null || leftKnee == null ||
              rightKnee == null || leftAnkle == null || rightAnkle == null ||
              leftShoulder == null || rightShoulder == null) {
        Log.d(TAG, "Missing required landmarks for hip thrust validation");
        return false;
      }

      // Hip thrust specific position analysis
      float shoulderY = (leftShoulder.getPosition().y + rightShoulder.getPosition().y) / 2;
      float hipY = (leftHip.getPosition().y + rightHip.getPosition().y) / 2;
      float kneeY = (leftKnee.getPosition().y + rightKnee.getPosition().y) / 2;
      float ankleY = (leftAnkle.getPosition().y + rightAnkle.getPosition().y) / 2;

      // Check if person is in lying/bridge position (shoulders should be at similar level or lower than hips)
      boolean isLyingPosition = Math.abs(shoulderY - hipY) < 0.2f; // Shoulders and hips at similar height for lying position

      // Check if legs are bent properly for hip thrust (knees should be between hips and ankles)
      boolean legsBentProperly = hipY < kneeY && kneeY < ankleY; // Hip < Knee < Ankle for proper hip thrust position

      // Check confidence for hip thrust classification
      float hipThrustConfidence = classificationResult.getClassConfidence(HIP_THRUST_CLASS);
      boolean hasMinConfidence = hipThrustConfidence > 1.0f; // Minimum confidence threshold

      boolean isValid = isLyingPosition && legsBentProperly && hasMinConfidence;

      Log.d(TAG, "Hip thrust pose validation - Lying position: " + isLyingPosition +
              ", Legs bent properly: " + legsBentProperly +
              ", Confidence: " + hipThrustConfidence +
              ", Valid: " + isValid);

      return isValid;

    } catch (Exception e) {
      Log.e(TAG, "Error in hip thrust pose validation: " + e.getMessage(), e);
      return false;
    }
  }

  // Enhanced validation method for windmill poses
  private boolean validateWindmillPose(Pose pose, ClassificationResult classificationResult) {
    try {
      // Check if we have the necessary landmarks for windmill validation
      PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
      PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
      PoseLandmark leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
      PoseLandmark rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
      PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
      PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
      PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
      PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);

      if (leftShoulder == null || rightShoulder == null || leftElbow == null ||
              rightElbow == null || leftWrist == null || rightWrist == null ||
              leftHip == null || rightHip == null) {
        Log.d(TAG, "Missing required landmarks for windmill validation");
        return false;
      }

      // Check if person is standing upright
      float shoulderY = (leftShoulder.getPosition().y + rightShoulder.getPosition().y) / 2;
      float hipY = (leftHip.getPosition().y + rightHip.getPosition().y) / 2;
      boolean isUpright = shoulderY < hipY; // Shoulders above hips for standing position

      // Check for windmill arm position - one arm up, one arm down
      boolean leftArmUp = leftWrist.getPosition().y < leftShoulder.getPosition().y - 0.1f;
      boolean rightArmUp = rightWrist.getPosition().y < rightShoulder.getPosition().y - 0.1f;
      boolean leftArmDown = leftWrist.getPosition().y > leftHip.getPosition().y + 0.1f;
      boolean rightArmDown = rightWrist.getPosition().y > rightHip.getPosition().y + 0.1f;

      // Windmill position: one arm up, opposite arm down
      boolean isWindmillPosition = (leftArmUp && rightArmDown) || (rightArmUp && leftArmDown);

      // Check confidence for windmill classification
      float windmillLeftConfidence = classificationResult.getClassConfidence(WINDMILL_LEFT_CLASS);
      float windmillRightConfidence = classificationResult.getClassConfidence(WINDMILL_RIGHT_CLASS);
      float maxWindmillConfidence = Math.max(windmillLeftConfidence, windmillRightConfidence);
      boolean hasMinConfidence = maxWindmillConfidence > 1.0f; // Minimum confidence threshold

      boolean isValid = isUpright && isWindmillPosition && hasMinConfidence;

      Log.d(TAG, "Windmill pose validation - Upright: " + isUpright +
              ", Windmill position: " + isWindmillPosition +
              ", Left arm up: " + leftArmUp + ", Right arm down: " + rightArmDown +
              ", Right arm up: " + rightArmUp + ", Left arm down: " + leftArmDown +
              ", Left confidence: " + windmillLeftConfidence +
              ", Right confidence: " + windmillRightConfidence +
              ", Valid: " + isValid);

      return isValid;

    } catch (Exception e) {
      Log.e(TAG, "Error in windmill pose validation: " + e.getMessage(), e);
      return false;
    }
  }

  /**
   * Reset all repetition counters to zero
   */
  public void resetCounters() {
    if (repCounters != null) {
      for (RepetitionCounter counter : repCounters) {
        counter.reset();
        Log.d(TAG, "Reset counter for " + counter.getClassName());
      }
    }
    lastRepResult = "Exercise: 0 reps";
    validPoseFrameCount = 0;
    Log.d(TAG, "All counters reset");
  }

}
