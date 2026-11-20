/**
 * Live Inline Translation Engine Tests (Phase 1)
 * Validates Gboard-style inline translation, debouncing, language swap, and commit contracts.
 */

const assert = require('assert');

class MockLiveTranslator {
  constructor() {
    this.sourceLang = 'bn';
    this.targetLang = 'en';
    this.buffer = '';
    this.isLiveActive = false;
    this.lastPreview = '';
    this.committedTexts = [];
  }

  toggle(active) {
    this.isLiveActive = active;
    this.buffer = '';
    this.lastPreview = '';
  }

  swapLanguages() {
    const temp = this.sourceLang;
    this.sourceLang = this.targetLang;
    this.targetLang = temp;
  }

  async mockTranslate(text, source, target) {
    if (!text || text.trim() === '') return '';
    const clean = text.trim();
    if (source === 'bn' && target === 'en') {
      if (clean === 'আমি ভালো আছি') return 'I am fine';
      if (clean === 'ধন্যবাদ') return 'Thank you';
      return `[EN] ${clean}`;
    } else {
      if (clean === 'I am fine') return 'আমি ভালো আছি';
      return `[BN] ${clean}`;
    }
  }

  typeChar(char) {
    this.buffer += char;
  }

  async processDebounce() {
    if (!this.buffer) return;
    this.lastPreview = await this.mockTranslate(this.buffer, this.sourceLang, this.targetLang);
  }

  async commit() {
    if (this.lastPreview) {
      this.committedTexts.push(this.lastPreview);
      this.lastPreview = '';
      this.buffer = '';
    } else if (this.buffer) {
      const translated = await this.mockTranslate(this.buffer, this.sourceLang, this.targetLang);
      this.committedTexts.push(translated);
      this.buffer = '';
    }
  }
}

async function runLiveTranslationTests() {
  console.log('=== Gboard Live Inline Translation Engine Tests ===\n');

  const engine = new MockLiveTranslator();

  // Test 1: Language swap
  console.log('1. Testing Language Pair Swapping...');
  assert.strictEqual(engine.sourceLang, 'bn');
  assert.strictEqual(engine.targetLang, 'en');
  engine.swapLanguages();
  assert.strictEqual(engine.sourceLang, 'en');
  assert.strictEqual(engine.targetLang, 'bn');
  engine.swapLanguages(); // back to bn -> en
  console.log('  [PASS] Language swapping verified.');

  // Test 2: Typing and Debounced Translation
  console.log('2. Testing Typing and Debounced Translation...');
  engine.toggle(true);
  engine.typeChar('ধ');
  engine.typeChar('ন');
  engine.typeChar('্');
  engine.typeChar('য');
  engine.typeChar('ব');
  engine.typeChar('া');
  engine.typeChar('দ');
  assert.strictEqual(engine.buffer, 'ধন্যবাদ');

  await engine.processDebounce();
  assert.strictEqual(engine.lastPreview, 'Thank you');
  console.log('  [PASS] Composing buffer and debounce translation verified: "ধন্যবাদ" -> "Thank you"');

  // Test 3: Commit / Send action
  console.log('3. Testing Instant Commit to Active Text Field...');
  await engine.commit();
  assert.strictEqual(engine.committedTexts.length, 1);
  assert.strictEqual(engine.committedTexts[0], 'Thank you');
  assert.strictEqual(engine.buffer, '');
  assert.strictEqual(engine.lastPreview, '');
  console.log('  [PASS] Auto-commit and buffer cleanup verified.');

  // Test 4: Full sentence translation
  console.log('4. Testing Complex Sentence Translation & Commit...');
  for (const c of 'আমি ভালো আছি') {
    engine.typeChar(c);
  }
  await engine.processDebounce();
  assert.strictEqual(engine.lastPreview, 'I am fine');
  await engine.commit();
  assert.strictEqual(engine.committedTexts[1], 'I am fine');
  console.log('  [PASS] Sentence level translation verified: "আমি ভালো আছি" -> "I am fine"');

  console.log('\n=== ALL LIVE INLINE TRANSLATION TESTS PASSED (4/4)! ===\n');
}

runLiveTranslationTests().catch(err => {
  console.error('Test Failed:', err);
  process.exit(1);
});
