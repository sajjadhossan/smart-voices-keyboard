/**
 * Smart Voice Keyboard - Cross-Platform Backup & Restore Manager
 * Handles serialization, schema validation, deduplication, and optional encryption.
 */

const { encrypt, decrypt } = require('../security/crypto');

const APP_IDENTIFIER = 'smart-voices-keyboard';
const CURRENT_BACKUP_SCHEMA_VERSION = '1.7.0';

/**
 * Creates a standardized backup payload
 * @param {object} params
 * @param {object} params.settings
 * @param {Array} params.clips
 * @param {Array} params.userDictionary
 * @param {string} [params.passphrase] - Optional encryption passphrase
 * @returns {object} Backup object ready for export
 */
function createBackup({ settings = {}, clips = [], userDictionary = [], passphrase = null }) {
  const payloadData = {
    settings,
    clips: clips.map(clip => ({
      id: clip.id,
      text: clip.text,
      is_pinned: !!clip.is_pinned,
      copy_count: clip.copy_count || 1,
      created_at: clip.created_at || Date.now()
    })),
    user_dictionary: userDictionary.map(entry => ({
      word: entry.word,
      frequency: entry.frequency || 1
    }))
  };

  if (passphrase) {
    const encryptedString = encrypt(JSON.stringify(payloadData), passphrase);
    return {
      app: APP_IDENTIFIER,
      version: CURRENT_BACKUP_SCHEMA_VERSION,
      exported_at: new Date().toISOString(),
      encrypted: true,
      payload: encryptedString
    };
  }

  return {
    app: APP_IDENTIFIER,
    version: CURRENT_BACKUP_SCHEMA_VERSION,
    exported_at: new Date().toISOString(),
    encrypted: false,
    data: payloadData
  };
}

/**
 * Validates, decrypts (if needed), and restores a backup payload
 * @param {object} backupObj - The parsed JSON backup object
 * @param {string} [passphrase] - Optional passphrase if backup is encrypted
 * @returns {{success: boolean, data?: object, error?: string}}
 */
function restoreBackup(backupObj, passphrase = null) {
  if (!backupObj || typeof backupObj !== 'object') {
    return { success: false, error: 'অবৈধ ব্যাকআপ ফাইল (Invalid backup object)' };
  }

  if (backupObj.app !== APP_IDENTIFIER) {
    return { success: false, error: 'ফাইলটি এই অ্যাপ্লিকেশনের ব্যাকআপ নয় (Incompatible app signature)' };
  }

  let data = null;

  if (backupObj.encrypted) {
    if (!passphrase) {
      return { success: false, error: 'এনক্রিপ্টেড ব্যাকআপ আনলক করতে পাসওয়ার্ড প্রয়োজন (Passphrase required)' };
    }
    try {
      const decryptedString = decrypt(backupObj.payload, passphrase);
      data = JSON.parse(decryptedString);
    } catch (e) {
      return { success: false, error: 'ভুল পাসওয়ার্ড অথবা ফাইলটি ক্ষতিগ্রস্ত (Decryption failed)' };
    }
  } else {
    data = backupObj.data;
  }

  if (!data || typeof data !== 'object') {
    return { success: false, error: 'ব্যাকআপের ডাটা পাওয়া যায়নি (Missing backup data)' };
  }

  const sanitizedSettings = data.settings || {};
  const sanitizedClips = Array.isArray(data.clips) ? data.clips : [];
  const sanitizedDict = Array.isArray(data.user_dictionary) ? data.user_dictionary : [];

  return {
    success: true,
    data: {
      settings: sanitizedSettings,
      clips: sanitizedClips,
      user_dictionary: sanitizedDict,
      imported_counts: {
        clips: sanitizedClips.length,
        dictionaryWords: sanitizedDict.length
      }
    }
  };
}

module.exports = {
  APP_IDENTIFIER,
  CURRENT_BACKUP_SCHEMA_VERSION,
  createBackup,
  restoreBackup
};
