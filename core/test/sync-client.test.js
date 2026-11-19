/**
 * Unit Test for Cross-Device Clipboard Sync Client
 */

const assert = require('assert');
const { SyncClient } = require('../sync/sync-client');

async function testSyncClient() {
  console.log('=== Cross-Device Sync Client Unit Tests ===\n');

  const client = new SyncClient({
    serverUrl: 'http://localhost:4000',
    authToken: 'test_jwt_token',
    deviceType: 'windows'
  });

  // 1. Queue Clip
  console.log('1. Testing Offline Queueing of Clips...');
  client.queueClip({ id: 'clip_1', text: 'Important text copied on Windows' });
  client.queueClip({ id: 'clip_2', text: 'Meeting notes', is_pinned: true });
  // Duplicate check
  client.queueClip({ id: 'clip_1', text: 'Important text copied on Windows' });

  assert.strictEqual(client.getPendingCount(), 2, 'Queue should hold 2 distinct items');
  console.log('  [PASS] Clips correctly queued with deduplication.');

  // 2. Test Sync without token error handling
  console.log('\n2. Testing Sync with missing token...');
  const noTokenClient = new SyncClient({ authToken: '' });
  noTokenClient.queueClip({ id: 'c1', text: 'sample' });
  const failRes = await noTokenClient.sync();
  assert.strictEqual(failRes.error, 'AUTH_TOKEN_MISSING');
  assert.strictEqual(noTokenClient.syncStatus, 'failed');
  console.log('  [PASS] Missing token gracefully caught.');

  // 3. Test Successful Mocked Sync
  console.log('\n3. Testing Successful Sync with Backend Mock...');
  const mockFetch = async (url, options) => {
    assert(url.includes('/api/clipboard/sync'));
    assert.strictEqual(options.method, 'POST');
    assert(options.headers['Authorization'].includes('Bearer test_jwt_token'));
    const body = JSON.parse(options.body);
    assert.strictEqual(body.clips.length, 2);

    return {
      ok: true,
      json: async () => ({
        success: true,
        message: 'Sync completed',
        data: { synced: body.clips, total: 2 }
      })
    };
  };

  const syncRes = await client.sync(mockFetch);
  assert.strictEqual(syncRes.syncedCount, 2);
  assert.strictEqual(client.getPendingCount(), 0, 'Pending queue should be cleared after sync');
  assert.strictEqual(client.syncStatus, 'synced');
  assert(client.lastSyncedAt !== null);
  console.log('  [PASS] Delta sync completed and state updated to synced.');

  console.log('\n=== ALL SYNC CLIENT TESTS PASSED! ===\n');
}

if (require.main === module) {
  testSyncClient().catch(err => {
    console.error('Test Failed:', err);
    process.exit(1);
  });
}

module.exports = { testSyncClient };
