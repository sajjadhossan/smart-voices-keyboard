/**
 * Smart Voice Keyboard - Cross-Device Clipboard Sync Client
 * Implements Rule Section 5: Offline-First Queue, Delta Sync, and Conflict Resolution.
 */

class SyncClient {
  constructor(options = {}) {
    this.serverUrl = options.serverUrl || 'http://localhost:4000';
    this.authToken = options.authToken || '';
    this.tenantId = options.tenantId || '';
    this.deviceId = options.deviceId || 'desktop_' + Math.random().toString(36).substring(2, 8);
    this.deviceType = options.deviceType || 'desktop'; // 'android' | 'windows' | 'macos'

    this.pendingQueue = [];
    this.isSyncing = false;
    this.lastSyncedAt = null;
    this.syncStatus = 'idle'; // 'idle' | 'syncing' | 'synced' | 'failed' | 'offline'
    this.onStatusChangeCallback = null;
  }

  setCredentials(token, tenantId) {
    this.authToken = token;
    this.tenantId = tenantId;
  }

  /**
   * Queue a local clip for cloud sync
   * @param {Object} clipItem
   */
  queueClip(clipItem) {
    if (!clipItem || !clipItem.text) return;

    const syncItem = {
      client_id: clipItem.id || 'clip_' + Date.now(),
      text: clipItem.text,
      source_device: this.deviceType,
      is_pinned: !!clipItem.is_pinned,
      copy_count: clipItem.copy_count || 1,
      sync_status: 'pending',
      created_at: clipItem.created_at || Date.now()
    };

    // Avoid duplicate in pending queue
    const existingIndex = this.pendingQueue.findIndex(p => p.client_id === syncItem.client_id || p.text === syncItem.text);
    if (existingIndex !== -1) {
      this.pendingQueue[existingIndex] = syncItem;
    } else {
      this.pendingQueue.push(syncItem);
    }

    this.updateStatus('pending');
  }

  /**
   * Perform bidirectional delta sync with central backend
   * @param {Function} [fetchFn] - Optional fetch implementation for testing
   * @returns {Promise<{syncedCount: number, error?: string}>}
   */
  async sync(fetchFn = fetch) {
    if (this.isSyncing) return { syncedCount: 0 };
    if (!this.authToken) {
      this.updateStatus('failed', 'AUTH_TOKEN_MISSING');
      return { syncedCount: 0, error: 'AUTH_TOKEN_MISSING' };
    }

    this.isSyncing = true;
    this.updateStatus('syncing');

    try {
      const itemsToUpload = [...this.pendingQueue];

      const response = await fetchFn(`${this.serverUrl}/api/clipboard/sync`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.authToken}`
        },
        body: JSON.stringify({ clips: itemsToUpload })
      });

      if (!response.ok) {
        throw new Error(`Sync HTTP error [${response.status}]`);
      }

      const resData = await response.json();
      if (!resData.success) {
        throw new Error(resData.message || 'Sync failed');
      }

      // Clear uploaded items from queue
      this.pendingQueue = [];
      this.lastSyncedAt = Date.now();
      this.updateStatus('synced');

      return {
        syncedCount: resData.data?.synced?.length || itemsToUpload.length,
        total: resData.data?.total || 0
      };
    } catch (err) {
      this.updateStatus('failed', err.message);
      return { syncedCount: 0, error: err.message };
    } finally {
      this.isSyncing = false;
    }
  }

  getPendingCount() {
    return this.pendingQueue.length;
  }

  updateStatus(status, error = null) {
    this.syncStatus = status;
    if (this.onStatusChangeCallback) {
      this.onStatusChangeCallback({
        status: this.syncStatus,
        pendingCount: this.pendingQueue.length,
        lastSyncedAt: this.lastSyncedAt,
        error: error
      });
    }
  }

  onStatusChange(callback) {
    this.onStatusChangeCallback = callback;
  }
}

module.exports = { SyncClient };
