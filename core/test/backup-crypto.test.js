const { describe, it } = require('node:test');
const assert = require('node:assert');
const { encrypt, decrypt, hashValue } = require('../security/crypto');
const { createBackup, restoreBackup, APP_IDENTIFIER } = require('../backup/backup-manager');

describe('Cross-Platform Backup & AES-256-GCM Cryptographic Security (Phase 5)', () => {

  const secretPassphrase = 'EnterpriseUltraSecretPassphrase123!@#';
  const sampleSettings = {
    theme: 'amoled',
    default_mode: 'banglish',
    gemini_api_key: 'AIzaSyExampleSecretKey999',
    enable_vibration: true,
    enable_sound: true
  };
  const sampleClips = [
    { id: 'clip_1', text: 'বাংলা টাইপিং সমাধান', is_pinned: true, copy_count: 5, created_at: Date.now() },
    { id: 'clip_2', text: 'Meeting notes 2026', is_pinned: false, copy_count: 1, created_at: Date.now() }
  ];
  const sampleDictionary = [
    { word: 'ধন্যবাদ', frequency: 15 },
    { word: 'বাংলাদেশ', frequency: 10 }
  ];

  it('should encrypt and decrypt plaintext using AES-256-GCM without data loss', () => {
    const originalText = 'গোপন তথ্য ও পাসওয়ার্ড (Top Secret Content 12345)';
    const encryptedBase64 = encrypt(originalText, secretPassphrase);

    assert.notStrictEqual(encryptedBase64, originalText);
    assert.match(encryptedBase64, /^[A-Za-z0-9+/=]+$/);

    const decrypted = decrypt(encryptedBase64, secretPassphrase);
    assert.strictEqual(decrypted, originalText);
  });

  it('should fail decryption and throw error if ciphertext is tampered with', () => {
    const originalText = 'Tamper Verification Test';
    const encryptedBase64 = encrypt(originalText, secretPassphrase);

    // Decode, tamper data payload, and re-encode
    const jsonStr = Buffer.from(encryptedBase64, 'base64').toString('utf8');
    const parsed = JSON.parse(jsonStr);
    parsed.data = 'ff' + parsed.data.slice(2); // Corrupt first byte of ciphertext
    const tamperedBase64 = Buffer.from(JSON.stringify(parsed)).toString('base64');

    assert.throws(() => {
      decrypt(tamperedBase64, secretPassphrase);
    }, Error);
  });

  it('should generate standardized unencrypted backup package', () => {
    const backup = createBackup({
      settings: sampleSettings,
      clips: sampleClips,
      userDictionary: sampleDictionary
    });

    assert.strictEqual(backup.app, APP_IDENTIFIER);
    assert.strictEqual(backup.encrypted, false);
    assert.strictEqual(backup.data.settings.theme, 'amoled');
    assert.strictEqual(backup.data.clips.length, 2);
    assert.strictEqual(backup.data.user_dictionary.length, 2);

    const restoreRes = restoreBackup(backup);
    assert.strictEqual(restoreRes.success, true);
    assert.strictEqual(restoreRes.data.clips.length, 2);
    assert.strictEqual(restoreRes.data.settings.default_mode, 'banglish');
  });

  it('should create and restore password-protected encrypted backup', () => {
    const encryptedBackup = createBackup({
      settings: sampleSettings,
      clips: sampleClips,
      userDictionary: sampleDictionary,
      passphrase: secretPassphrase
    });

    assert.strictEqual(encryptedBackup.encrypted, true);
    assert.strictEqual(typeof encryptedBackup.payload, 'string');
    assert.strictEqual(encryptedBackup.data, undefined);

    // Restore without passphrase should fail
    const failedNoPass = restoreBackup(encryptedBackup, null);
    assert.strictEqual(failedNoPass.success, false);
    assert.match(failedNoPass.error, /পাসওয়ার্ড প্রয়োজন/);

    // Restore with wrong passphrase should fail
    const failedWrongPass = restoreBackup(encryptedBackup, 'WrongPassword123');
    assert.strictEqual(failedWrongPass.success, false);
    assert.match(failedWrongPass.error, /ভুল পাসওয়ার্ড/);

    // Restore with correct passphrase should succeed
    const successRes = restoreBackup(encryptedBackup, secretPassphrase);
    assert.strictEqual(successRes.success, true);
    assert.strictEqual(successRes.data.clips.length, 2);
    assert.strictEqual(successRes.data.settings.theme, 'amoled');
  });

  it('should reject incompatible backup files', () => {
    const foreignBackup = {
      app: 'some-other-unrelated-app',
      data: {}
    };

    const res = restoreBackup(foreignBackup);
    assert.strictEqual(res.success, false);
    assert.match(res.error, /ফাইলটি এই অ্যাপ্লিকেশনের ব্যাকআপ নয়/);
  });
});
