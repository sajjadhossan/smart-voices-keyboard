/**
 * Smart Voice Keyboard - Open Source Speech-to-Text (STT) Engine Manager
 * Supports:
 * 1. Native System Speech Recognition (Free, Zero Latency, Streaming)
 * 2. Open-source local Whisper (ONNX / WebAssembly)
 * 3. Offline Vosk / Sherpa Bengali & English voice models
 */

class STTEngineManager {
  constructor(options = {}) {
    this.engineType = options.engineType || 'native'; // 'native' | 'whisper' | 'vosk'
    this.currentLanguage = options.language || 'bn-BD'; // 'bn-BD' | 'en-US'
    this.isListening = false;
    this.recognitionInstance = null;
    this.onResultCallback = null;
    this.onErrorCallback = null;
    this.onStateChangeCallback = null;
  }

  setEngine(type) {
    this.engineType = type;
  }

  setLanguage(langCode) {
    this.currentLanguage = langCode;
    if (this.recognitionInstance) {
      this.recognitionInstance.lang = langCode;
    }
  }

  /**
   * Initialize speech recognition (works in Desktop Chromium/Electron renderer and Web/Android WebViews)
   */
  initWebSpeech(windowContext) {
    const SpeechRecognition = windowContext.SpeechRecognition || windowContext.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      throw new Error('SYSTEM_SPEECH_RECOGNITION_UNAVAILABLE');
    }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = this.currentLanguage;
    recognition.maxAlternatives = 1;

    recognition.onstart = () => {
      this.isListening = true;
      if (this.onStateChangeCallback) this.onStateChangeCallback({ state: 'listening' });
    };

    recognition.onresult = (event) => {
      let interimTranscript = '';
      let finalTranscript = '';

      for (let i = event.resultIndex; i < event.results.length; ++i) {
        const text = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          finalTranscript += text;
        } else {
          interimTranscript += text;
        }
      }

      if (this.onResultCallback) {
        this.onResultCallback({
          final: finalTranscript.trim(),
          interim: interimTranscript.trim(),
          timestamp: Date.now()
        });
      }
    };

    recognition.onerror = (event) => {
      if (this.onErrorCallback) {
        this.onErrorCallback(event.error);
      }
    };

    recognition.onend = () => {
      this.isListening = false;
      if (this.onStateChangeCallback) this.onStateChangeCallback({ state: 'stopped' });
    };

    this.recognitionInstance = recognition;
    return recognition;
  }

  startListening() {
    if (this.recognitionInstance && !this.isListening) {
      try {
        this.recognitionInstance.lang = this.currentLanguage;
        this.recognitionInstance.start();
        this.isListening = true;
      } catch (e) {
        console.warn('SpeechRecognition already started or error:', e);
      }
    }
  }

  stopListening() {
    if (this.recognitionInstance && this.isListening) {
      try {
        this.recognitionInstance.stop();
        this.isListening = false;
      } catch (e) {
        console.warn('Error stopping SpeechRecognition:', e);
      }
    }
  }

  onResult(callback) {
    this.onResultCallback = callback;
  }

  onError(callback) {
    this.onErrorCallback = callback;
  }

  onStateChange(callback) {
    this.onStateChangeCallback = callback;
  }
}

module.exports = { STTEngineManager };
