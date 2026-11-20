/**
 * Smart Voices Keyboard - Desktop Quick Mode & Hotkey Engine (Phase 8, v2.0.0)
 * Manages mode switching, shortcut normalization across Windows & macOS,
 * and direct Unicode Bangla character transliteration.
 */

const MODES = {
  VOICE: 'voice',
  BANGLISH: 'banglish',
  ENGLISH: 'english',
  BANGLA: 'bangla',
  CLIPBOARD: 'clipboard',
  SETTINGS: 'settings'
};

const MODE_LABELS = {
  [MODES.VOICE]: { bn: '🎙️ ভয়েস ইনপুট', en: 'Voice Input', shortcut: 'Ctrl+Alt+1' },
  [MODES.BANGLISH]: { bn: '⌨️ বাংলিশ ফোনেটিক', en: 'Banglish Phonetic', shortcut: 'Ctrl+Alt+2' },
  [MODES.ENGLISH]: { bn: '🇺🇸 ইংরেজি (English)', en: 'English QWERTY', shortcut: 'Ctrl+Alt+3' },
  [MODES.BANGLA]: { bn: '🇧🇩 খাঁটি বাংলা', en: 'Pure Bangla', shortcut: 'Ctrl+Alt+4' },
  [MODES.CLIPBOARD]: { bn: '📋 ক্লিপবোর্ড হিস্টোরি', en: 'Clipboard History', shortcut: 'Ctrl+Alt+V' },
  [MODES.SETTINGS]: { bn: '⚙️ সেটিংস ও এপিআই', en: 'Settings & API', shortcut: 'Ctrl+Alt+S' }
};

/**
 * Normalizes hotkey for current operating system.
 * @param {string} hotkeyStr - e.g. "Control+Alt+Space"
 * @param {boolean} isMac - Whether target platform is macOS
 * @returns {string} Platform-specific accelerator
 */
function normalizeHotkey(hotkeyStr, isMac = false) {
  if (!hotkeyStr) return '';
  if (isMac) {
    return hotkeyStr
      .replace(/Control/gi, 'Command')
      .replace(/Ctrl/gi, 'Command');
  }
  return hotkeyStr
    .replace(/Command/gi, 'Control')
    .replace(/Cmd/gi, 'Control');
}

/**
 * Validates requested mode against supported mode list.
 * @param {string} mode
 * @returns {string} Safe valid mode key
 */
function validateMode(mode) {
  const normalized = (mode || '').toLowerCase().trim();
  if (Object.values(MODES).includes(normalized)) {
    return normalized;
  }
  return MODES.VOICE; // Default fallback mode
}

/**
 * Direct character mapping table for pure Unicode Bangla mode.
 */
const DIRECT_BANGLA_MAP = {
  'k': 'ক', 'K': 'খ', 'g': 'গ', 'G': 'ঘ', 'N': 'ঙ',
  'c': 'চ', 'C': 'ছ', 'j': 'জ', 'J': 'ঝ',
  't': 'ট', 'T': 'ঠ', 'd': 'ড', 'D': 'ঢ',
  'r': 'র', 'R': 'ড়', 'l': 'ল', 'L': 'ল',
  'p': 'প', 'P': 'ফ', 'b': 'ব', 'B': 'ভ', 'm': 'ম',
  's': 'স', 'S': 'শ', 'h': 'হ', 'H': 'ঃ',
  'a': 'া', 'A': 'আ', 'i': 'ি', 'I': 'ী',
  'u': 'ু', 'U': 'ূ', 'e': 'ে', 'E': 'ৈ', 'o': 'ো', 'O': 'ৌ'
};

/**
 * Converts a sequence of characters directly to Bangla in real-time.
 * @param {string} input
 * @returns {string} Direct Bangla text
 */
function convertDirectBangla(input) {
  if (!input || typeof input !== 'string') return '';
  let output = '';
  for (let i = 0; i < input.length; i++) {
    const ch = input[i];
    output += DIRECT_BANGLA_MAP[ch] || ch;
  }
  return output;
}

module.exports = {
  MODES,
  MODE_LABELS,
  normalizeHotkey,
  validateMode,
  convertDirectBangla
};
