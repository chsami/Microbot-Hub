/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2022, Damen
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.drofirecape;

/** Server-clock portion of FC Spawn Predictor, local to this plugin. The spawn
 * wheel/tables already live in WaveBook; live verification stays in WaveTracker.
 * Adapted from damencs/spawn-predictor commit 8c5a34d4c88407cda7a6c60d8014018163d97974.
 */
final class FcSpawnPredictor {
    static int rotationColumn(int serverMinutes) {
        if(serverMinutes<0)return -1;
        int column=serverMinutes%16;
        int minute=serverMinutes%60;
        // Upstream repeats rotation 4 at columns 16 and 1.
        return (column==15&&minute%2!=0)||(column==0&&minute%2==0)?1:column+1;
    }
    FcPredictorGate.Sample sample(int world,int minute,int second,boolean outsideTzhaar,long now) {
        if(!outsideTzhaar)return new FcPredictorGate.Sample(this,world,-1,-1,-1,-1,now,false,false,true,
            "Built-in spawn predictor: waiting for outer TzHaar");
        boolean ready=minute>=0&&second>=0&&second<=59;
        int column=ready?rotationColumn(minute):-1;
        return new FcPredictorGate.Sample(this,world,column,FcPredictorGate.rotationForColumn(column),minute,second,
            now,false,ready,!ready||second>=50,"Built-in FC Spawn Predictor server clock");
    }
}
