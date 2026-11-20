const { describe, it } = require('node:test');
const assert = require('node:assert');
const {
  calculateRepeatInterval,
  getWordDeletionBoundary,
  calculateSwipeDeleteCount,
  calculateSpacebarCursorOffset
} = require('../gesture/delete-gesture-engine');

describe('Gesture & Accelerated Continuous Deletion Engine (Phase 6)', () => {

  it('should calculate adaptive repeat interval and accelerate over time', () => {
    // Initial delay phase (< 350ms)
    const initial = calculateRepeatInterval(100);
    assert.strictEqual(initial.mode, 'char');
    assert.strictEqual(initial.intervalMs, 250);

    // Mid character acceleration phase (350ms - 1200ms)
    const mid500 = calculateRepeatInterval(500);
    assert.strictEqual(mid500.mode, 'char');
    assert(mid500.intervalMs <= 70 && mid500.intervalMs >= 50);

    const mid1000 = calculateRepeatInterval(1000);
    assert.strictEqual(mid1000.mode, 'char');
    assert(mid1000.intervalMs < mid500.intervalMs);

    // Rapid word deletion phase (> 1200ms)
    const rapid1500 = calculateRepeatInterval(1500);
    assert.strictEqual(rapid1500.mode, 'word');
    assert.strictEqual(rapid1500.intervalMs, 80);
  });

  it('should accurately calculate word deletion boundaries for English text', () => {
    const text1 = 'The quick brown fox';
    const count1 = getWordDeletionBoundary(text1);
    assert.strictEqual(count1, 3); // "fox" = 3
    assert.strictEqual(text1.slice(0, text1.length - count1), 'The quick brown ');

    const textWithSpaces = 'Hello world   ';
    const countSpaces = getWordDeletionBoundary(textWithSpaces);
    assert.strictEqual(countSpaces, 8); // "world   " = 8
    assert.strictEqual(textWithSpaces.slice(0, textWithSpaces.length - countSpaces), 'Hello ');

    const textWithPunctuation = 'Good morning!!!';
    const countPunct = getWordDeletionBoundary(textWithPunctuation);
    assert.strictEqual(countPunct, 3); // "!!!" = 3
  });

  it('should accurately calculate word deletion boundaries for Bangla unicode text', () => {
    const banglaText1 = 'আমি বাংলায় ভালোবাসি';
    const countBn1 = getWordDeletionBoundary(banglaText1);
    assert.strictEqual(banglaText1.slice(banglaText1.length - countBn1), 'ভালোবাসি');
    assert.strictEqual(banglaText1.slice(0, banglaText1.length - countBn1), 'আমি বাংলায় ');

    const banglaWithDari = 'বাংলাদেশ আমার অহংকার।';
    const countDari = getWordDeletionBoundary(banglaWithDari);
    assert.strictEqual(banglaWithDari.slice(banglaWithDari.length - countDari), '।');

    const banglaConjunct = 'বিশ্ববিদ্যালয় ও প্রযুক্তি';
    const countConjunct = getWordDeletionBoundary(banglaConjunct);
    assert.strictEqual(banglaConjunct.slice(banglaConjunct.length - countConjunct), 'প্রযুক্তি');
  });

  it('should calculate swipe-to-delete count based on horizontal drag distance', () => {
    assert.strictEqual(calculateSwipeDeleteCount(0), 0);
    assert.strictEqual(calculateSwipeDeleteCount(100), 0); // Right swipe should not delete
    assert.strictEqual(calculateSwipeDeleteCount(-45), 1); // 45px swipe left = 1 word
    assert.strictEqual(calculateSwipeDeleteCount(-90), 2); // 90px swipe left = 2 words
    assert.strictEqual(calculateSwipeDeleteCount(-225), 5); // 225px swipe left = 5 words
  });

  it('should calculate spacebar cursor navigation offset correctly', () => {
    assert.strictEqual(calculateSpacebarCursorOffset(10), 0); // Below threshold
    assert.strictEqual(calculateSpacebarCursorOffset(-10), 0); // Below threshold
    assert.strictEqual(calculateSpacebarCursorOffset(-36), -2); // 36px left = -2 characters
    assert.strictEqual(calculateSpacebarCursorOffset(54), 3); // 54px right = +3 characters
  });

});
