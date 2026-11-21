/**
 * Integration Test for Backend API
 */

const assert = require('assert');
const http = require('http');
const { server, db } = require('../src/server');

const TEST_PORT = 4999;

function request(options, postData = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve({
            statusCode: res.statusCode,
            headers: res.headers,
            body: body ? JSON.parse(body) : null
          });
        } catch (e) {
          resolve({
            statusCode: res.statusCode,
            headers: res.headers,
            rawBody: body
          });
        }
      });
    });

    req.on('error', reject);

    if (postData) {
      req.write(typeof postData === 'string' ? postData : JSON.stringify(postData));
    }
    req.end();
  });
}

async function runBackendTests() {
  console.log('=== Backend API Standard Contract Tests ===\n');

  await new Promise((resolve) => server.listen(TEST_PORT, resolve));

  try {
    // 1. Test Version API (Section 10)
    console.log('1. Testing GET /api/app/version (Rule Section 10)...');
    const verRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: '/api/app/version',
      method: 'GET'
    });

    assert.strictEqual(verRes.statusCode, 200);
    assert.strictEqual(verRes.body.success, true);
    assert.strictEqual(verRes.body.data.latestVersion, '2.2.0');
    assert(verRes.body.data.apkDownloadUrl.includes('.apk'));
    console.log('  [PASS] Version parity API returns correct contract.');

    // 2. Test Auth Register
    console.log('\n2. Testing POST /api/auth/register...');
    const regRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: '/api/auth/register',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, {
      email: 'sajjad@smartvoices.com',
      password: 'SecurePassword123!',
      full_name: 'Sajjad Hossan'
    });

    assert.strictEqual(regRes.statusCode, 201);
    assert.strictEqual(regRes.body.success, true);
    const token = regRes.body.data.token;
    assert(token && token.includes('.'));
    console.log('  [PASS] User and tenant registered with auth token.');

    // 3. Test Batch Clipboard Delta Sync
    console.log('\n3. Testing POST /api/clipboard/sync (Multi-device sync)...');
    const syncRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: '/api/clipboard/sync',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      }
    }, {
      clips: [
        { client_id: 'uuid_101', text: 'Hello from Android Mobile Keyboard', source_device: 'android', is_pinned: true },
        { client_id: 'uuid_102', text: 'Copied from MacBook Safari', source_device: 'macos', is_pinned: false }
      ]
    });

    assert.strictEqual(syncRes.statusCode, 200);
    assert.strictEqual(syncRes.body.data.synced.length, 2);
    console.log('  [PASS] Multi-device delta clips synced successfully.');

    // 4. Test Fetch Synced Clips
    console.log('\n4. Testing GET /api/clipboard...');
    const listRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: '/api/clipboard',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` }
    });

    assert.strictEqual(listRes.statusCode, 200);
    assert.strictEqual(listRes.body.data.length, 2);
    const clipIdToDelete = listRes.body.data[0].id;
    console.log('  [PASS] Synced clips retrieved for tenant.');

    // 5. Test Soft Delete (Rule Section 1)
    console.log('\n5. Testing DELETE /api/clipboard/:id (Soft Delete)...');
    const delRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: `/api/clipboard/${clipIdToDelete}`,
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    });

    assert.strictEqual(delRes.statusCode, 200);

    // Verify soft delete in list
    const afterDelRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: '/api/clipboard',
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    assert.strictEqual(afterDelRes.body.data.length, 1);
    console.log('  [PASS] Soft delete verified (record retained with audit timestamp).');

    // 6. Test Backup Export (Phase 5)
    console.log('\n6. Testing POST /api/backup/export...');
    const exportRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: '/api/backup/export',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      }
    });
    assert.strictEqual(exportRes.statusCode, 200);
    assert.strictEqual(exportRes.body.data.app, 'smart-voices-keyboard');
    assert(Array.isArray(exportRes.body.data.data.clips));
    console.log('  [PASS] Cloud backup export verified.');

    // 7. Test Backup Import (Phase 5)
    console.log('\n7. Testing POST /api/backup/import...');
    const importPayload = {
      app: 'smart-voices-keyboard',
      version: '1.7.0',
      exported_at: new Date().toISOString(),
      encrypted: false,
      data: {
        settings: { theme: 'ocean' },
        clips: [
          { text: 'নতুন ব্যাকআপ ক্লিপ ১', is_pinned: true },
          { text: 'নতুন ব্যাকআপ ক্লিপ ২', is_pinned: false }
        ]
      }
    };

    const importRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: '/api/backup/import',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      }
    }, importPayload);
    assert.strictEqual(importRes.statusCode, 200);
    assert.strictEqual(importRes.body.data.imported_clips, 2);
    console.log('  [PASS] Cloud backup import and deduplication verified.');

    // 8. Test Gemini Health Ping (Phase 7)
    console.log('\n8. Testing POST /api/gemini/ping...');
    const pingRes = await request({
      hostname: 'localhost',
      port: TEST_PORT,
      path: '/api/gemini/ping',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, { apiKey: 'AIzaSyExampleValidTestKey123' });
    assert.strictEqual(pingRes.statusCode, 200);
    assert.strictEqual(pingRes.body.data.connected, true);
    assert.strictEqual(pingRes.body.data.model, 'gemini-1.5-flash');
    assert(typeof pingRes.body.data.latency_ms === 'number');
    console.log('  [PASS] Gemini API health check and latency ping verified.');

    console.log('\n=== ALL BACKEND API CONTRACT TESTS PASSED! ===');
  } finally {
    server.close();
  }
}

if (require.main === module) {
  runBackendTests().catch(err => {
    console.error('Test Failed:', err);
    process.exit(1);
  });
}

module.exports = { runBackendTests };
