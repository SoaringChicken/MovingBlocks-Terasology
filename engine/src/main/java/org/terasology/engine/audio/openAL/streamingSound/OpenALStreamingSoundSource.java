// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.engine.audio.openAL.streamingSound;

import org.lwjgl.BufferUtils;
import org.terasology.engine.audio.openAL.BaseSoundSource;
import org.terasology.engine.audio.openAL.OpenALException;
import org.terasology.engine.audio.openAL.SoundPool;
import org.terasology.engine.audio.openAL.SoundSource;

import java.nio.IntBuffer;

import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED;
import static org.lwjgl.openal.AL10.AL_PLAYING;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourceQueueBuffers;
import static org.lwjgl.openal.AL10.alSourceRewind;
import static org.lwjgl.openal.AL10.alSourceStop;
import static org.lwjgl.openal.AL10.alSourceUnqueueBuffers;
import static org.lwjgl.openal.AL10.alSourcei;

public class OpenALStreamingSoundSource extends BaseSoundSource<OpenALStreamingSound> {

    private OpenALStreamingSound audio;
    
    public OpenALStreamingSoundSource(SoundPool<OpenALStreamingSound, OpenALStreamingSoundSource> owningPool) {
        super(owningPool);
    }

    @Override
    public SoundSource<OpenALStreamingSound> stop() {
        if (audio != null) {
            audio.reset();
        }
        return super.stop();
    }

    @Override
    public boolean isLooping() {
        return false;
    }
    
    @Override
    public OpenALStreamingSound getAudio() {
        return audio;
    }

    @Override
    // TODO: Implement looping support for streaming sounds
    public OpenALStreamingSoundSource setLooping(boolean looping) {
        if (looping) {
            throw new UnsupportedOperationException("Looping is unsupported on streaming sounds!");
        }

        return this;
    }

    @Override
    public void update(float delta) {
        int buffersProcessed = alGetSourcei(this.getSourceId(), AL_BUFFERS_PROCESSED);

        while (buffersProcessed-- > 0) {
            int buffer = alSourceUnqueueBuffers(this.getSourceId());
            OpenALException.checkState("Buffer unqueue");

            if (audio.updateBuffer(buffer)) {
                alSourceQueueBuffers(this.getSourceId(), buffer);
                OpenALException.checkState("Buffer refill");
            } else {
                stop(); // we aren't playing anymore, because stream seems to end
            }
        }

        super.update(delta);
    }

    @Override
    protected void updateState() {
        // Start playing if playback for stopped by end of buffers
        if (isPlaying() && alGetSourcei(getSourceId(), AL_SOURCE_STATE) != AL_PLAYING) {
            alSourcePlay(this.getSourceId());
        }
    }

    @Override
    public OpenALStreamingSoundSource setAudio(OpenALStreamingSound sound) {
        boolean isPlaying = this.isPlaying();
        if (isPlaying) {
            alSourceStop(getSourceId());
            alSourceRewind(getSourceId());
        }

        alSourcei(this.getSourceId(), AL_BUFFER, 0);

        this.audio = sound;

        sound.reset();

        int[] buffers = sound.getBuffers();

        for (int buffer : buffers) {
            sound.updateBuffer(buffer);
        }

        alSourceQueueBuffers(this.getSourceId(), (IntBuffer) BufferUtils.createIntBuffer(buffers.length).put(buffers).flip());

        if (isPlaying) {
            this.play();
        }

        return this;
    }

    @Override
    public void purge() {
        if (isPlaying()) {
            alSourceStop(getSourceId());
            alSourceRewind(getSourceId());
        }

        alSourcei(this.getSourceId(), AL_BUFFER, 0);
    }

}
