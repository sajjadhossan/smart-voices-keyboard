/**
 * Smart Voice Keyboard - Unlimited Clipboard Manager
 * Robust persistent storage with deduplication, pinning, tags, and full-text search.
 */

const fs = require('fs');
const path = require('path');

class ClipboardStore {
  constructor(storageDir) {
    this.storageDir = storageDir || path.join(process.cwd(), 'data');
    this.storageFile = path.join(this.storageDir, 'clipboard_history.json');
    this.items = [];
    this.init();
  }

  init() {
    try {
      if (!fs.existsSync(this.storageDir)) {
        fs.mkdirSync(this.storageDir, { recursive: true });
      }
      if (fs.existsSync(this.storageFile)) {
        const raw = fs.readFileSync(this.storageFile, 'utf8');
        this.items = JSON.parse(raw);
      } else {
        this.items = [];
        this.save();
      }
    } catch (e) {
      console.error('Failed to load clipboard store:', e);
      this.items = [];
    }
  }

  save() {
    try {
      fs.writeFileSync(this.storageFile, JSON.stringify(this.items, null, 2), 'utf8');
    } catch (e) {
      console.error('Failed to persist clipboard data:', e);
    }
  }

  /**
   * Add a new clip to clipboard history
   * @param {string} text - Copied text content
   * @param {string} source - 'desktop' | 'android' | 'voice' | 'translation'
   */
  addClip(text, source = 'desktop') {
    if (!text || typeof text !== 'string' || !text.trim()) return null;
    const trimmed = text.trim();

    // Check for duplicate
    const existingIndex = this.items.findIndex(item => item.text === trimmed);
    const now = Date.now();

    if (existingIndex !== -1) {
      // Bring duplicate to top and increase count
      const existing = this.items.splice(existingIndex, 1)[0];
      existing.updated_at = now;
      existing.copy_count = (existing.copy_count || 1) + 1;
      this.items.unshift(existing);
      this.save();
      return existing;
    }

    const newItem = {
      id: 'clip_' + now + '_' + Math.random().toString(36).substr(2, 6),
      text: trimmed,
      source: source,
      is_pinned: false,
      copy_count: 1,
      char_count: trimmed.length,
      created_at: now,
      updated_at: now
    };

    this.items.unshift(newItem);
    this.save();
    return newItem;
  }

  /**
   * Search clips by keyword
   */
  search(query = '', filterPinned = false) {
    let result = this.items;
    if (filterPinned) {
      result = result.filter(item => item.is_pinned);
    }
    if (query && query.trim()) {
      const q = query.toLowerCase().trim();
      result = result.filter(item => item.text.toLowerCase().includes(q));
    }
    return result;
  }

  /**
   * Toggle pinned status
   */
  togglePin(id) {
    const item = this.items.find(i => i.id === id);
    if (item) {
      item.is_pinned = !item.is_pinned;
      item.updated_at = Date.now();
      this.save();
      return item;
    }
    return null;
  }

  /**
   * Delete specific clip
   */
  deleteClip(id) {
    const initialLen = this.items.length;
    this.items = this.items.filter(i => i.id !== id);
    const deleted = this.items.length < initialLen;
    if (deleted) this.save();
    return deleted;
  }

  /**
   * Clear all non-pinned clips
   */
  clearHistory(keepPinned = true) {
    if (keepPinned) {
      this.items = this.items.filter(i => i.is_pinned);
    } else {
      this.items = [];
    }
    this.save();
    return this.items.length;
  }

  getAll() {
    return this.items;
  }

  getCount() {
    return this.items.length;
  }
}

module.exports = { ClipboardStore };
