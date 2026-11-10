//
// Created by muwmix on 11/10/26.
//

#ifndef YUMAPLAYER_DJ_FILTER_H
#define YUMAPLAYER_DJ_FILTER_H

#endif //YUMAPLAYER_DJ_FILTER_H

#pragma once

#include <cstdint>
#include <cmath>
#include <algorithm>

namespace yuma::dsp {

    struct BiquadState {
        double x1 = 0.0, x2 = 0.0;
        double y1 = 0.0, y2 = 0.0;

        void Reset() { x1 = x2 = y1 = y2 = 0.0; }

        inline double Process(const double* c, double in) {
            double out = c[0] * in + c[1] * x1 + c[2] * x2 - c[3] * y1 - c[4] * y2;
            x2 = x1;
            x1 = in;
            y2 = y1;
            y1 = out;
            return out;
        }
    };

    void ProcessPcmBlock(
            const uint8_t* in_bytes,
            uint8_t* out_bytes,
            int frames,
            int channels,
            int encoding,
            double cutoff_hz,
            double high_pass_hz,
            double bass_db,
            double gain,
            int sample_rate,
            BiquadState* lp_state,
            BiquadState* hp_state,
            BiquadState* shelf_state
    );

} // namespace yuma::dsp