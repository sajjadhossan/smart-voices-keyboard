/**
 * Smart Voice Keyboard - Security & Cryptographic Engine
 * Provides AES-256-GCM encryption, decryption, PBKDF2 key derivation, and secure hashing.
 */

const crypto = require('crypto');

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12; // 96 bits for GCM
const SALT_LENGTH = 16;
const PBKDF2_ITERATIONS = 100000;
const KEY_LENGTH = 32; // 256 bits

/**
 * Derive a 256-bit encryption key from a passphrase and salt
 * @param {string} passphrase 
 * @param {Buffer} salt 
 * @returns {Buffer}
 */
function deriveKey(passphrase, salt) {
  return crypto.pbkdf2Sync(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH, 'sha256');
}

/**
 * Encrypt arbitrary text using AES-256-GCM with a passphrase
 * @param {string} text - Plaintext to encrypt
 * @param {string} passphrase - Secret passphrase
 * @returns {string} Base64-encoded encrypted payload
 */
function encrypt(text, passphrase) {
  if (!text || !passphrase) {
    throw new Error('Text and passphrase are required for encryption');
  }

  const salt = crypto.randomBytes(SALT_LENGTH);
  const key = deriveKey(passphrase, salt);
  const iv = crypto.randomBytes(IV_LENGTH);

  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
  let encrypted = cipher.update(text, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  const authTag = cipher.getAuthTag();

  const payload = {
    alg: ALGORITHM,
    salt: salt.toString('hex'),
    iv: iv.toString('hex'),
    tag: authTag.toString('hex'),
    data: encrypted
  };

  return Buffer.from(JSON.stringify(payload)).toString('base64');
}

/**
 * Decrypt an AES-256-GCM payload with a passphrase
 * @param {string} encryptedBase64 - Base64 payload
 * @param {string} passphrase - Secret passphrase
 * @returns {string} Decrypted plaintext
 */
function decrypt(encryptedBase64, passphrase) {
  if (!encryptedBase64 || !passphrase) {
    throw new Error('Encrypted payload and passphrase are required for decryption');
  }

  const rawJson = Buffer.from(encryptedBase64, 'base64').toString('utf8');
  const payload = JSON.parse(rawJson);

  const salt = Buffer.from(payload.salt, 'hex');
  const iv = Buffer.from(payload.iv, 'hex');
  const authTag = Buffer.from(payload.tag, 'hex');
  const key = deriveKey(passphrase, salt);

  const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
  decipher.setAuthTag(authTag);

  let decrypted = decipher.update(payload.data, 'hex', 'utf8');
  decrypted += decipher.final('utf8');

  return decrypted;
}

/**
 * Generate a secure SHA-256 hash of an API key or sensitive token
 * @param {string} value 
 * @returns {string} Hex-encoded hash
 */
function hashValue(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

module.exports = {
  encrypt,
  decrypt,
  hashValue,
  deriveKey
};
