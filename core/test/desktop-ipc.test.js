/**
 * Desktop Core & IPC Logic Verification Test
 */

const assert = require('assert');
const path = require('path');
const fs = require('fs');

const { PhoneticEngine } = require('../phonetic/phonetic-engine');
const { ClipboardStore } = require('../clipboard/clipboard-store');
const { GeminiTranslator } = require('../gemini/translator');

async function testDesktopCore() {
  console.log('=== Desktop Core & Logic Verification ===\n');

  // 1. Verify Renderer Assets
  console.log('1. Checking Desktop Renderer files...');
  const rendererFiles = [
    'desktop/src/renderer/index.html',
    'desktop/src/renderer/styles.css',
    'desktop/src/renderer/app.js',
    'desktop/src/main/index.js',
    'desktop/src/main/preload.js',
    'desktop/src/assets/tray-icon.png'
  ];

  for (const file of rendererFiles) {
    const fullPath = path.join(process.cwd(), file);
    assert(fs.existsSync(fullPath), `Missing file: ${file}`);
    console.log(`  [OK] Found ${file}`);
  }

  // 2. Verify Unlimited Clipboard Store Logic
  console.log('\n2. Testing Clipboard Store operations...');
  const testDataDir = path.join(process.cwd(), 'data', 'test_clipboard');
  const clipStore = new ClipboardStore(testDataDir);

  // Add clips
  const clip1 = clipStore.addClip('প্রথম টেস্ট ক্লিপ (First test clip)', 'desktop');
  const clip2 = clipStore.addClip('English text clip for translation', 'desktop');
  const clipDup = clipStore.addClip('প্রথম টেস্ট ক্লিপ (First test clip)', 'desktop');

  assert.strictEqual(clipDup.copy_count, 2, 'Deduplication counter failed');
  console.log('  [OK] Deduplication and copy_count tracking verified.');

  // Pinning
  clipStore.togglePin(clip1.id);
  const pinnedList = clipStore.search('', true);
  assert(pinnedList.some(c => c.id === clip1.id), 'Pinning clip failed');
  console.log('  [OK] Pinning and search filtering verified.');

  // Full Text Search
  const searchResult = clipStore.search('English');
  assert.strictEqual(searchResult.length, 1, 'Search query failed');
  console.log('  [OK] Keyword search verified.');

  // Clean test directory
  try {
    fs.rmSync(testDataDir, { recursive: true, force: true });
  } catch (e) {}

  // 3. Verify Banglish to Bangla Phonetic Engine
  console.log('\n3. Testing Banglish to Bangla Conversion...');
  const banglishSample = 'amar shonar bangla ami tomay bhalobashi';
  const banglaOutput = PhoneticEngine.transliterateText(banglishSample);
  console.log(`  Input:  "${banglishSample}"`);
  console.log(`  Output: "${banglaOutput}"`);
  assert(banglaOutput.includes('বাংলা') && banglaOutput.includes('ভালোবাসি'), 'Phonetic mismatch');
  console.log('  [OK] Phonetic conversion verified.');

  // 4. Verify Gemini Client Mocking
  console.log('\n4. Testing Gemini Client Interface...');
  const translator = new GeminiTranslator('test_key');
  assert(typeof translator.translate === 'function');
  assert(typeof translator.executeVoiceCommand === 'function');
  console.log('  [OK] Gemini Client interface verified.');

  console.log('\n=== ALL DESKTOP CORE TESTS PASSED! ===');
}

testDesktopCore().catch(err => {
  console.error('Test Failed:', err);
  process.exit(1);
});
