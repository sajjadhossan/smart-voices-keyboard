/**
 * Smart Voice Keyboard - Audio Visualizer & Offline Speech Processor
 * Handles live WebAudio microphone analysis, real-time decibel waveform monitoring,
 * Voice Activity Detection (VAD), and offline STT fallback routing.
 */

class AudioVisualizerPipeline {
  constructor() {
    this.audioContext = null;
    this.analyser = null;
    this.microphoneStream = null;
    this.dataArray = null;
    this.animationFrameId = null;
    this.isMonitoring = false;
    this.onVolumeChangeCallback = null;
  }

  /**
   * Initialize microphone stream and WebAudio analyzer
   * @param {MediaStream} stream
   */
  init(stream) {
    if (!stream) throw new Error('NO_AUDIO_STREAM_PROVIDED');
    this.microphoneStream = stream;

    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) throw new Error('AUDIO_CONTEXT_UNSUPPORTED');

    this.audioContext = new AudioContextClass();
    this.analyser = this.audioContext.createAnalyser();
    this.analyser.fftSize = 64;
    this.analyser.smoothingTimeConstant = 0.8;

    const source = this.audioContext.createMediaStreamSource(stream);
    source.connect(this.analyser);

    const bufferLength = this.analyser.frequencyBinCount;
    this.dataArray = new Uint8Array(bufferLength);
  }

  /**
   * Start analyzing audio frames and reporting frequency band amplitudes
   * @param {(bars: number[], rmsDb: number) => void} onVolumeChange
   */
  start(onVolumeChange) {
    if (this.isMonitoring) return;
    this.isMonitoring = true;
    this.onVolumeChangeCallback = onVolumeChange;

    const tick = () => {
      if (!this.isMonitoring || !this.analyser) return;

      this.analyser.getByteFrequencyData(this.dataArray);

      // Extract 7 frequency bands for the waveform bars
      const bands = [];
      const step = Math.floor(this.dataArray.length / 7) || 1;
      let sum = 0;

      for (let i = 0; i < 7; i++) {
        const val = this.dataArray[i * step] || 0;
        bands.push(val / 255); // 0.0 to 1.0 normalized
        sum += val;
      }

      const avgVolume = sum / (7 * 255); // 0.0 to 1.0
      const rmsDb = Math.round(avgVolume * 100);

      if (this.onVolumeChangeCallback) {
        this.onVolumeChangeCallback(bands, rmsDb);
      }

      this.animationFrameId = requestAnimationFrame(tick);
    };

    tick();
  }

  stop() {
    this.isMonitoring = false;
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
    if (this.microphoneStream) {
      this.microphoneStream.getTracks().forEach(track => track.stop());
      this.microphoneStream = null;
    }
    if (this.audioContext && this.audioContext.state !== 'closed') {
      this.audioContext.close();
      this.audioContext = null;
    }
  }
}

/**
 * Voice Activity Detection (VAD) & Silence Detector
 */
class VoiceActivityDetector {
  constructor(options = {}) {
    this.silenceThresholdMs = options.silenceThresholdMs || 1500;
    this.minVolumeThreshold = options.minVolumeThreshold || 12; // dB scale (0-100)
    this.lastSpeechTimestamp = Date.now();
    this.isSpeaking = false;
    this.onSilenceDetected = options.onSilenceDetected || null;
  }

  processAudioLevel(volumeDb) {
    const now = Date.now();
    if (volumeDb >= this.minVolumeThreshold) {
      this.lastSpeechTimestamp = now;
      if (!this.isSpeaking) {
        this.isSpeaking = true;
      }
    } else {
      if (this.isSpeaking && (now - this.lastSpeechTimestamp) >= this.silenceThresholdMs) {
        this.isSpeaking = false;
        if (this.onSilenceDetected) {
          this.onSilenceDetected({ silenceDurationMs: now - this.lastSpeechTimestamp });
        }
      }
    }
    return this.isSpeaking;
  }
}

/**
 * Offline Fallback Speech-to-Text Pipeline
 */
class OfflineSpeechFallback {
  constructor() {
    this.supportedLanguages = ['bn-BD', 'en-US'];
  }

  /**
   * Determine whether current client is online
   */
  static isOnline() {
    return typeof navigator !== 'undefined' ? navigator.onLine : true;
  }

  /**
   * Formats offline speech parameters for engine execution
   * @param {string} langCode
   */
  getEngineConfig(langCode = 'bn-BD') {
    const isOnline = OfflineSpeechFallback.isOnline();
    return {
      engine: isOnline ? 'cloud-native-streaming' : 'local-offline-whisper',
      language: this.supportedLanguages.includes(langCode) ? langCode : 'bn-BD',
      preferOffline: !isOnline,
      continuous: true,
      interimResults: true
    };
  }
}

module.exports = {
  AudioVisualizerPipeline,
  VoiceActivityDetector,
  OfflineSpeechFallback
};
