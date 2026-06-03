#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "MagicsPoolEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

float calculateDistance(float x1, float y1, float x2, float y2) {
    return std::sqrt(std::pow(x2 - x1, 2) + std::pow(y2 - y1, 2));
}

extern "C" JNIEXPORT void JNICALL
Java_com_magics_pool8_GameEngine_stepPhysics(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray ballX,
        jfloatArray ballY,
        jfloatArray ballVX,
        jfloatArray ballVY,
        jint ballCount,
        jfloat deltaTime) {

    jfloat* xData = env->GetFloatArrayElements(ballX, nullptr);
    jfloat* yData = env->GetFloatArrayElements(ballY, nullptr);
    jfloat* vxData = env->GetFloatArrayElements(ballVX, nullptr);
    jfloat* vyData = env->GetFloatArrayElements(ballVY, nullptr);

    float friction = 0.985f; // Slight deceleration friction
    float boundaryWidth = 1000.0f;
    float boundaryHeight = 2000.0f;
    float radius = 30.0f;

    // 1. Update positions and apply friction
    for (int i = 0; i < ballCount; i++) {
        xData[i] += vxData[i] * deltaTime;
        yData[i] += vyData[i] * deltaTime;

        vxData[i] *= friction;
        vyData[i] *= friction;

        // Bounce off rails (restitution coefficient 0.82)
        if (xData[i] - radius < 0) {
            xData[i] = radius;
            vxData[i] = -vxData[i] * 0.82f;
        } else if (xData[i] + radius > boundaryWidth) {
            xData[i] = boundaryWidth - radius;
            vxData[i] = -vxData[i] * 0.82f;
        }

        if (yData[i] - radius < 0) {
            yData[i] = radius;
            vyData[i] = -vyData[i] * 0.82f;
        } else if (yData[i] + radius > boundaryHeight) {
            yData[i] = boundaryHeight - radius;
            vyData[i] = -vyData[i] * 0.82f;
        }
    }

    // 2. Ball to Ball elastic collisions (with overlap safety checks)
    for (int i = 0; i < ballCount; i++) {
        for (int j = i + 1; j < ballCount; j++) {
            float dist = calculateDistance(xData[i], yData[i], xData[j], yData[j]);
            if (dist < (radius * 2.0f)) {
                // Safety separation if exactly overlapping
                if (dist < 0.01f) {
                    xData[i] -= radius;
                    xData[j] += radius;
                    continue;
                }

                // Push overlapping spheres apart equally
                float overlap = 0.5f * (radius * 2.0f - dist);
                float nx = (xData[j] - xData[i]) / dist;
                float ny = (yData[j] - yData[i]) / dist;

                xData[i] -= overlap * nx;
                yData[i] -= overlap * ny;
                xData[j] += overlap * nx;
                yData[j] += overlap * ny;

                // Relative velocities along collision normal vector
                float kx = (vxData[i] - vxData[j]);
                float ky = (vyData[i] - vyData[j]);
                float v_normal = nx * kx + ny * ky;

                // Exchange momentum only if moving towards each other
                if (v_normal > 0.0f) {
                    float p = v_normal; // Equal mass elastic bounce impulse
                    vxData[i] -= p * nx;
                    vyData[i] -= p * ny;
                    vxData[j] += p * nx;
                    vyData[j] += p * ny;
                }
            }
        }
    }

    env->ReleaseFloatArrayElements(ballX, xData, 0);
    env->ReleaseFloatArrayElements(ballY, yData, 0);
    env->ReleaseFloatArrayElements(ballVX, vxData, 0);
    env->ReleaseFloatArrayElements(ballVY, vyData, 0);
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_magics_pool8_GameEngine_calculateBotMove(
        JNIEnv* env,
        jobject /* this */,
        jfloat cueX,
        jfloat cueY,
        jfloatArray targetXArray,
        jfloatArray targetYArray,
        jint targetCount) {

    jfloat* targetXData = env->GetFloatArrayElements(targetXArray, nullptr);
    jfloat* targetYData = env->GetFloatArrayElements(targetYArray, nullptr);

    // Standard pool table pockets (top-left, top-right, left-middle, right-middle, bottom-left, bottom-right)
    float pocketsX[] = {0.0f, 1000.0f, 0.0f, 1000.0f, 0.0f, 1000.0f};
    float pocketsY[] = {0.0f, 0.0f, 1000.0f, 1000.0f, 2000.0f, 2000.0f};
    int pocketCount = 6;
    float ballRadius = 30.0f;

    float bestVx = 0.0f;
    float bestVy = 0.0f;
    float bestScore = -1e9f;

    for (int b = 0; b < targetCount; b++) {
        float tx = targetXData[b];
        float ty = targetYData[b];

        for (int p = 0; p < pocketCount; p++) {
            float px = pocketsX[p];
            float py = pocketsY[p];

            // 1. Vector: target ball center -> pocket center
            float dx_tp = px - tx;
            float dy_tp = py - ty;
            float dist_tp = std::sqrt(dx_tp * dx_tp + dy_tp * dy_tp);
            if (dist_tp < 0.01f) continue;

            float ux_tp = dx_tp / dist_tp;
            float uy_tp = dy_tp / dist_tp;

            // 2. Ghost ball center (2 * ballRadius away from target center, opposite to pocket direction)
            float gx = tx - ux_tp * (2.0f * ballRadius);
            float gy = ty - uy_tp * (2.0f * ballRadius);

            // 3. Vector: cue ball center -> ghost ball center (shooting direction)
            float dx_cg = gx - cueX;
            float dy_cg = gy - cueY;
            float dist_cg = std::sqrt(dx_cg * dx_cg + dy_cg * dy_cg);
            if (dist_cg < 0.01f) continue;

            float ux_cg = dx_cg / dist_cg;
            float uy_cg = dy_cg / dist_cg;

            // 4. Cut angle check: dot product between cue->target direction and target->pocket direction
            float dx_ct = tx - cueX;
            float dy_ct = ty - cueY;
            float dist_ct = std::sqrt(dx_ct * dx_ct + dy_ct * dy_ct);
            if (dist_ct < 0.01f) continue;

            float ux_ct = dx_ct / dist_ct;
            float uy_ct = dy_ct / dist_ct;

            float cosAngle = ux_ct * ux_tp + uy_ct * uy_tp;

            // If cosAngle is negative, pocket is behind target from cue ball (backward cut - impossible straight shot)
            if (cosAngle < 0.15f) continue; 

            // Aiming score: prefer straight-in alignments (cosAngle near 1.0) and shorter paths
            float score = (cosAngle * 1200.0f) - dist_tp * 0.25f - dist_ct * 0.1f;

            if (score > bestScore) {
                bestScore = score;
                // Hit harder if target is further away. Standard professional physics speed multipliers.
                float forceMultiplier = 48.0f;
                if (cosAngle < 0.8f) {
                    forceMultiplier = 38.0f; // Soft touch for tight cut shots
                }
                bestVx = ux_cg * forceMultiplier;
                bestVy = uy_cg * forceMultiplier;
            }
        }
    }

    // Fallback if no target ball is directly shootable (e.g., completely snookered or all cuts are negative)
    if (bestScore == -1e9f && targetCount > 0) {
        float tx = targetXData[0];
        float ty = targetYData[0];
        float dx = tx - cueX;
        float dy = ty - cueY;
        float dist = std::sqrt(dx * dx + dy * dy);
        if (dist > 0.01f) {
            bestVx = (dx / dist) * 36.0f;
            bestVy = (dy / dist) * 36.0f;
        } else {
            bestVx = 36.0f;
            bestVy = 0.0f;
        }
    }

    env->ReleaseFloatArrayElements(targetXArray, targetXData, 0);
    env->ReleaseFloatArrayElements(targetYArray, targetYData, 0);

    jfloatArray result = env->NewFloatArray(2);
    jfloat fill[2];
    fill[0] = bestVx;
    fill[1] = bestVy;
    env->SetFloatArrayRegion(result, 0, 2, fill);

    return result;
}
