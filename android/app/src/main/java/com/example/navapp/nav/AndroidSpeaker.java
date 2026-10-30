package com.example.navapp.nav;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

// Speaks prompts with Android's text-to-speech, so they play while the web page
// is hidden. Other audio (music) is lowered for the prompt and restored after.
// A prompt that arrives before the engine is ready is dropped, not queued: a
// late "In 400 metres" would be wrong by the time it played.
public final class AndroidSpeaker implements VoicePrompter.Speaker, TextToSpeech.OnInitListener {

    private final AudioManager audio;
    private final TextToSpeech tts;
    private volatile boolean ready = false;
    private AudioFocusRequest focus;

    public AndroidSpeaker(Context context) {
        audio = context.getSystemService(AudioManager.class);
        tts = new TextToSpeech(context.getApplicationContext(), this);
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { releaseFocus(); }
            @Override public void onError(String id) { releaseFocus(); }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            ready = true;
        }
    }

    @Override
    public void speak(String text) {
        if (!ready || text == null || text.isEmpty()) return;
        requestFocus();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prompt");
    }

    public void shutdown() {
        releaseFocus();
        tts.shutdown();
    }

    private void requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .build();
            audio.requestAudioFocus(focus);
        }
    }

    private void releaseFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focus != null) audio.abandonAudioFocusRequest(focus);
    }
}
