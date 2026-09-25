package com.knowledge.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioAccessResponse {
    private boolean canPlay;
    private boolean trial;
    private int trialSeconds;
    private String reason;

    public static AudioAccessResponse full() {
        return new AudioAccessResponse(true, false, 0, null);
    }

    public static AudioAccessResponse trial(int trialSeconds) {
        return new AudioAccessResponse(true, true, trialSeconds, null);
    }

    public static AudioAccessResponse denied(String reason) {
        return new AudioAccessResponse(false, false, 0, reason);
    }
}
