/**
 * Unit Test for Voice Command Pattern Detection & Engine
 */

const assert = require('assert');
const { VoiceCommandEngine } = require('../gemini/voice-commands');

function testVoiceCommands() {
  console.log('=== Voice Command & Prompt Engine Tests ===\n');

  const engine = new VoiceCommandEngine('test_key');

  const testCases = [
    {
      input: 'বাংলা করো: How are you today?',
      expectedType: 'TRANSLATE_TO_BN',
      expectedPayload: 'How are you today?'
    },
    {
      input: 'ইংরেজি করো: আগামীকাল মিটিং হবে',
      expectedType: 'TRANSLATE_TO_EN',
      expectedPayload: 'আগামীকাল মিটিং হবে'
    },
    {
      input: 'ফর্মাল ইমেইল বানাও: আমি কাল অফিসে আসব না অসুস্থতার জন্য',
      expectedType: 'FORMAL_EMAIL',
      expectedPayload: 'আমি কাল অফিসে আসব না অসুস্থতার জন্য'
    },
    {
      input: 'বানান ঠিক করো: আমি কালকে গেসিলাম',
      expectedType: 'FIX_GRAMMAR',
      expectedPayload: 'আমি কালকে গেসিলাম'
    },
    {
      input: 'পয়েন্ট আকারে সাজাও: মিটিং এর প্রধান আলোচ্য বিষয়সমূহ',
      expectedType: 'SUMMARIZE_BULLETS',
      expectedPayload: 'মিটিং এর প্রধান আলোচ্য বিষয়সমূহ'
    },
    {
      input: 'আমি এখন ভাত খাবো',
      expectedType: 'DIRECT_DICTATION',
      expectedPayload: 'আমি এখন ভাত খাবো'
    }
  ];

  let passed = 0;
  for (const tc of testCases) {
    const res = engine.detectCommand(tc.input);
    const typeOk = res.type === tc.expectedType;
    const payloadOk = res.payload === tc.expectedPayload;
    console.log(`Input: "${tc.input}"`);
    console.log(`  -> Detected: ${res.type} | Payload: "${res.payload}" [${typeOk && payloadOk ? 'PASS' : 'FAIL'}]`);
    if (typeOk && payloadOk) passed++;
  }

  assert.strictEqual(passed, testCases.length, 'All command pattern detections must pass');
  console.log(`\nSummary: ${passed}/${testCases.length} Voice Command tests passed.`);
  console.log('=== ALL VOICE COMMAND TESTS PASSED! ===\n');
}

if (require.main === module) {
  testVoiceCommands();
}

module.exports = { testVoiceCommands };
