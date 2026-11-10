/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */


//
// Created by muwmix on 11/10/26.
//
#include <jni.h>
#include <cstdint>
#include <cmath>
#include <algorithm>

namespace yuma::dsp {

    constexpr double kPi = 3.14159265358979323846;
    constexpr double kQ = 0.70710678;

    struct BiquadState {
        double x1 = 0.0;
        double x2 = 0.0;
        double y1 = 0.0;
        double y2 = 0.0;

        inline double Process(const double* c, double in) {
            double out = c[0] * in + c[1] * x1 + c[2] * x2 - c[3] * y1 - c[4] * y2;
            x2 = x1;
            x1 = in;
            y2 = y1;
            y1 = out;
            return out;
        }
    };

    inline void ComputeLowPass(double* c, double hz, int sample_rate) {
        double w0 = 2.0 * kPi * (std::clamp(hz, 30.0, sample_rate * 0.45) / sample_rate);
        double cos_w0 = std::cos(w0);
        double alpha = std::sin(w0) / (2.0 * kQ);
        double a0 = 1.0 + alpha;
        c[0] = ((1.0 - cos_w0) / 2.0) / a0;
        c[1] = (1.0 - cos_w0) / a0;
        c[2] = c[0];
        c[3] = (-2.0 * cos_w0) / a0;
        c[4] = (1.0 - alpha) / a0;
    }

    inline void ComputeHighPass(double* c, double hz, int sample_rate) {
        double w0 = 2.0 * kPi * (std::clamp(hz, 30.0, sample_rate * 0.45) / sample_rate);
        double cos_w0 = std::cos(w0);
        double alpha = std::sin(w0) / (2.0 * kQ);
        double a0 = 1.0 + alpha;
        c[0] = ((1.0 + cos_w0) / 2.0) / a0;
        c[1] = (-(1.0 + cos_w0)) / a0;
        c[2] = c[0];
        c[3] = (-2.0 * cos_w0) / a0;
        c[4] = (1.0 - alpha) / a0;
    }

    inline void ComputeLowShelf(double* c, double db, int sample_rate) {
        double amplitude = std::clamp(std::pow(10.0, db / 20.0), 0.001, 4.0);
        double shelf_a = std::sqrt(amplitude);
        double w0 = 2.0 * kPi * 200.0 / sample_rate;
        double cos_w0 = std::cos(w0);
        double alpha = std::sin(w0) / 2.0 * std::sqrt((shelf_a + 1.0 / shelf_a) * 0.0 + 2.0);
        double blend = 2.0 * std::sqrt(shelf_a) * alpha;
        double a0 = (shelf_a + 1.0) + (shelf_a - 1.0) * cos_w0 + blend;
        c[0] = (shelf_a * ((shelf_a + 1.0) - (shelf_a - 1.0) * cos_w0 + blend)) / a0;
        c[1] = (2.0 * shelf_a * ((shelf_a - 1.0) - (shelf_a + 1.0) * cos_w0)) / a0;
        c[2] = (shelf_a * ((shelf_a + 1.0) - (shelf_a - 1.0) * cos_w0 - blend)) / a0;
        c[3] = (-2.0 * ((shelf_a - 1.0) + (shelf_a + 1.0) * cos_w0)) / a0;
        c[4] = ((shelf_a + 1.0) + (shelf_a - 1.0) * cos_w0 - blend) / a0;
    }

} // namespace yuma::dsp

extern "C" {

JNIEXPORT void JNICALL
Java_moe_rukamori_archivetune_audiodsp_DjFilterAudioProcessor_processDirectBufferNative(
        JNIEnv* env,
        jobject /* clazz */,
        jobject input_buf,
        jint input_pos,
        jobject output_buf,
        jint output_pos,
        jint frames,
        jint channels,
        jint encoding,
        jint sample_rate,
        jdouble cutoff_hz,
        jdouble high_pass_hz,
        jdouble bass_db,
        jdouble gain,
        jdoubleArray filter_state_array
) {
    auto* in_bytes = static_cast<const uint8_t*>(env->GetDirectBufferAddress(input_buf)) + input_pos;
    auto* out_bytes = static_cast<uint8_t*>(env->GetDirectBufferAddress(output_buf)) + output_pos;

    jdouble* state_ptr = env->GetDoubleArrayElements(filter_state_array, nullptr);

    double lp_coeff[5], hp_coeff[5], shelf_coeff[5];
    const bool filtering = cutoff_hz < 20000.0;
    const bool high_passing = high_pass_hz > 30.0;
    const bool shelving = std::abs(bass_db) >= 0.01;
    const bool apply_gain = std::abs(gain - 1.0) >= 1e-4;

    if (filtering) yuma::dsp::ComputeLowPass(lp_coeff, cutoff_hz, sample_rate);
    if (high_passing) yuma::dsp::ComputeHighPass(hp_coeff, high_pass_hz, sample_rate);
    if (shelving) yuma::dsp::ComputeLowShelf(shelf_coeff, bass_db, sample_rate);

    // 2: C.ENCODING_PCM_16BIT, 4: C.ENCODING_PCM_FLOAT
    if (encoding == 2) {
        auto* in_s16 = reinterpret_cast<const int16_t*>(in_bytes);
        auto* out_s16 = reinterpret_cast<int16_t*>(out_bytes);

        for (int f = 0; f < frames; ++f) {
            for (int ch = 0; ch < channels; ++ch) {
                double sample = static_cast<double>(in_s16[f * channels + ch]) / 32768.0;

                auto* lp = reinterpret_cast<yuma::dsp::BiquadState*>(&state_ptr[ch * 4]);
                auto* hp = reinterpret_cast<yuma::dsp::BiquadState*>(&state_ptr[(channels + ch) * 4]);
                auto* sh = reinterpret_cast<yuma::dsp::BiquadState*>(&state_ptr[(channels * 2 + ch) * 4]);

                if (filtering) sample = lp->Process(lp_coeff, sample);
                if (high_passing) sample = hp->Process(hp_coeff, sample);
                if (shelving) sample = sh->Process(shelf_coeff, sample);
                if (apply_gain) sample *= gain;

                sample = std::clamp(sample, -1.0, 1.0);
                out_s16[f * channels + ch] = static_cast<int16_t>(sample * 32767.0);
            }
        }
    } else if (encoding == 4) {
        auto* in_f32 = reinterpret_cast<const float*>(in_bytes);
        auto* out_f32 = reinterpret_cast<float*>(out_bytes);

        for (int f = 0; f < frames; ++f) {
            for (int ch = 0; ch < channels; ++ch) {
                double sample = static_cast<double>(in_f32[f * channels + ch]);

                auto* lp = reinterpret_cast<yuma::dsp::BiquadState*>(&state_ptr[ch * 4]);
                auto* hp = reinterpret_cast<yuma::dsp::BiquadState*>(&state_ptr[(channels + ch) * 4]);
                auto* sh = reinterpret_cast<yuma::dsp::BiquadState*>(&state_ptr[(channels * 2 + ch) * 4]);

                if (filtering) sample = lp->Process(lp_coeff, sample);
                if (high_passing) sample = hp->Process(hp_coeff, sample);
                if (shelving) sample = sh->Process(shelf_coeff, sample);
                if (apply_gain) sample *= gain;

                out_f32[f * channels + ch] = static_cast<float>(std::clamp(sample, -1.0, 1.0));
            }
        }
    }

    env->ReleaseDoubleArrayElements(filter_state_array, state_ptr, 0);
}

} // extern "C"