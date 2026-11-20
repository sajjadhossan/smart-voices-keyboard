const { describe, it } = require('node:test');
const assert = require('node:assert');
const {
  MODES,
  MODE_LABELS,
  normalizeHotkey,
  validateMode,
  convertDirectBangla
} = require('../desktop/quick-mode-engine');

describe('Desktop Quick Mode & Global Hotkey Engine (Phase 8, v2.0.0)', () => {

  it('should define all 6 required enterprise desktop popup modes with labels and shortcuts', () => {
    assert.strictEqual(MODES.VOICE, 'voice');
    assert.strictEqual(MODES.BANGLISH, 'banglish');
    assert.strictEqual(MODES.ENGLISH, 'english');
    assert.strictEqual(MODES.BANGLA, 'bangla');
    assert.strictEqual(MODES.CLIPBOARD, 'clipboard');
    assert.strictEqual(MODES.SETTINGS, 'settings');

    Object.values(MODES).forEach(mode => {
      assert(MODE_LABELS[mode]);
      assert(MODE_LABELS[mode].bn);
      assert(MODE_LABELS[mode].shortcut);
    });
  });

  it('should normalize hotkeys correctly between Windows and macOS', () => {
    const winHotkey = 'Control+Alt+Space';
    const macHotkey = normalizeHotkey(winHotkey, true);
    assert.strictEqual(macHotkey, 'Command+Alt+Space');

    const backToWin = normalizeHotkey(macHotkey, false);
    assert.strictEqual(backToWin, 'Control+Alt+Space');
  });

  it('should validate requested mode and fallback safely', () => {
    assert.strictEqual(validateMode('BANGLISH'), 'banglish');
    assert.strictEqual(validateMode('english'), 'english');
    assert.strictEqual(validateMode('invalid_mode_xyz'), 'voice');
    assert.strictEqual(validateMode(null), 'voice');
  });

  it('should map direct keyboard characters to pure Unicode Bangla correctly', () => {
    const sample = 'k';
    assert.strictEqual(convertDirectBangla(sample), 'ক');

    const sampleWord = 'amar';
    // 'a' -> 'া', 'm' -> 'ম', 'a' -> 'া', 'r' -> 'র'
    assert.strictEqual(convertDirectBangla('m'), 'ম');
    assert.strictEqual(convertDirectBangla('r'), 'র');
    assert.strictEqual(convertDirectBangla('A'), 'আ');
  });

});
