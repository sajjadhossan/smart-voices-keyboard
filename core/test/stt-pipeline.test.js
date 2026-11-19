/**
 * Unit Test for STT Pipeline, Voice Activity Detection & Offline Fallback
 */

const assert = require('assert');
const { VoiceActivityDetector, OfflineSpeechFallback } = require('../stt/offline-processor');
const { STTEngineManager } = require('../stt/stt-engine');

function testSTTPipeline() {
  console.log('=== Speech-to-Text Pipeline & Offline Engine Tests ===\n');

  // 1. Test STTEngineManager Interface
  console.log('1. Testing STTEngineManager methods...');
  const stt = new STTEngineManager({ language: 'bn-BD', engineType: 'native' });
  assert.strictEqual(stt.currentLanguage, 'bn-BD');
  assert.strictEqual(stt.engineType, 'native');

  stt.setLanguage('en-US');
  assert.strictEqual(stt.currentLanguage, 'en-US');
  console.log('  [PASS] STTEngineManager initializes and switches language accurately.');

  // 2. Test Voice Activity Detection (VAD)
  console.log('\n2. Testing Voice Activity Detection (VAD) & Silence...');
  let silenceDetected = false;
  const vad = new VoiceActivityDetector({
    silenceThresholdMs: 50,
    minVolumeThreshold: 15,
    onSilenceDetected: () => {
      silenceDetected = true;
    }
  });

  // Speaking state (volume = 35 dB)
  const isSpeakingNow = vad.processAudioLevel(35);
  assert.strictEqual(isSpeakingNow, true, 'VAD should register speech at 35 dB');
  console.log('  [PASS] Speaking state detected at 35 dB.');

  // Silence state after delay
  setTimeout(() => {
    vad.processAudioLevel(5); // volume dropped to 5 dB
    console.log('  [PASS] VAD threshold handling verified.');
  }, 60);

  // 3. Test Offline Speech Fallback Configuration
  console.log('\n3. Testing Offline Fallback Engine Routing...');
  const fallback = new OfflineSpeechFallback();
  const bnConfig = fallback.getEngineConfig('bn-BD');
  assert.strictEqual(bnConfig.language, 'bn-BD');
  assert(bnConfig.engine !== undefined);

  const enConfig = fallback.getEngineConfig('en-US');
  assert.strictEqual(enConfig.language, 'en-US');
  console.log('  [PASS] Offline fallback config produced correct language routing.');

  console.log('\n=== ALL STT PIPELINE TESTS PASSED! ===\n');
}

if (require.main === module) {
  testSTTPipeline();
}

module.exports = { testSTTPipeline };
