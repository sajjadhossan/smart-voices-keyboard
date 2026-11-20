/**
 * Smart Voices Keyboard - Delete & Gesture Engine (Phase 6)
 * Handles continuous accelerated deletion, word boundary detection for Bangla & English,
 * and swipe gesture calculations.
 */

/**
 * Calculates adaptive repeat interval based on how long backspace has been held.
 * @param {number} holdDurationMs - Elapsed time in milliseconds
 * @returns {{ intervalMs: number, mode: 'char' | 'word' }}
 */
function calculateRepeatInterval(holdDurationMs) {
  if (holdDurationMs < 350) {
    // Initial debounce delay
    return { intervalMs: 350 - holdDurationMs, mode: 'char' };
  } else if (holdDurationMs < 1200) {
    // Character deletion phase with acceleration (70ms -> 35ms)
    const progress = (holdDurationMs - 350) / (1200 - 350);
    const intervalMs = Math.round(70 - (progress * 35));
    return { intervalMs: Math.max(intervalMs, 30), mode: 'char' };
  } else {
    // Rapid word/chunk deletion phase (> 1.2s)
    return { intervalMs: 80, mode: 'word' };
  }
}

/**
 * Finds the number of characters to delete backwards from cursor to remove the previous word.
 * Safely handles Bangla Unicode graphemes (hasant, vowel signs, conjuncts), punctuation, and whitespace.
 * @param {string} textBeforeCursor - Substring preceding the cursor
 * @returns {number} Number of characters to delete
 */
function getWordDeletionBoundary(textBeforeCursor) {
  if (!textBeforeCursor || typeof textBeforeCursor !== 'string' || textBeforeCursor.length === 0) {
    return 0;
  }

  let index = textBeforeCursor.length;

  // Step 1: Skip trailing whitespace if any
  while (index > 0 && /\s/.test(textBeforeCursor[index - 1])) {
    index--;
  }

  // If text was only whitespace, delete all trailing spaces
  if (index === 0) {
    return textBeforeCursor.length;
  }

  // Step 2: If previous character is punctuation, delete sequence of punctuation
  const isPunctuation = (ch) => /[\.,!?;:\-–—"'`\(\)\[\]\{\}।॥]/.test(ch);
  if (isPunctuation(textBeforeCursor[index - 1])) {
    while (index > 0 && isPunctuation(textBeforeCursor[index - 1])) {
      index--;
    }
    return textBeforeCursor.length - index;
  }

  // Step 3: Scan backwards through alphanumeric/Bangla characters until whitespace or punctuation
  while (index > 0 && !/\s/.test(textBeforeCursor[index - 1]) && !isPunctuation(textBeforeCursor[index - 1])) {
    index--;
  }

  return textBeforeCursor.length - index;
}

/**
 * Translates swipe-left delta distance (in pixels/dp) to number of words to delete.
 * @param {number} deltaX - Horizontal swipe distance in pixels (negative for left swipe)
 * @param {number} thresholdPx - Pixel distance per word
 * @returns {number} Number of words to select/delete
 */
function calculateSwipeDeleteCount(deltaX, thresholdPx = 45) {
  if (deltaX >= 0) return 0;
  const distance = Math.abs(deltaX);
  return Math.min(Math.floor(distance / thresholdPx), 25);
}

/**
 * Translates spacebar swipe horizontal drag into cursor step increments.
 * @param {number} deltaX - Horizontal movement in pixels
 * @param {number} stepPx - Movement required per cursor step
 * @returns {number} Net cursor shift (-N for left, +N for right)
 */
function calculateSpacebarCursorOffset(deltaX, stepPx = 18) {
  if (Math.abs(deltaX) < stepPx) return 0;
  return Math.trunc(deltaX / stepPx);
}

module.exports = {
  calculateRepeatInterval,
  getWordDeletionBoundary,
  calculateSwipeDeleteCount,
  calculateSpacebarCursorOffset
};
