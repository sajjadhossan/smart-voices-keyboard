/**
 * Smart Voice Keyboard - Centralized Multi-Tenant Backend API
 * Shared API contract for Android APK, Windows PC, and macOS.
 */

const http = require('http');
const url = require('url');
const crypto = require('crypto');

const PORT = process.env.PORT || 4000;

// Standard Response Helpers (Rule Section 6)
function sendSuccess(res, statusCode, message, data = {}, meta = null) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  const response = {
    success: true,
    message: message,
    data: data,
    meta: meta || {
      page: 1,
      limit: 20,
      total: Array.isArray(data) ? data.length : 1,
      totalPages: 1
    },
    errors: []
  };
  res.end(JSON.stringify(response));
}

function sendError(res, statusCode, message, errorCode, errors = []) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  const response = {
    success: false,
    message: message,
    errorCode: errorCode,
    errors: errors
  };
  res.end(JSON.stringify(response));
}

// In-Memory Database Store for Testing / Fallback
const db = {
  users: [
    {
      id: 'usr_default_admin',
      tenant_id: 'ten_default',
      email: 'admin@smartvoices.com',
      password_hash: crypto.createHash('sha256').update('Admin1234!').digest('hex'),
      full_name: 'Super Admin',
      role: 'admin',
      created_at: new Date().toISOString()
    }
  ],
  clips: [],
  version: {
    latestVersion: '1.4.1',
    minimumSupportedVersion: '1.0.0',
    apkDownloadUrl: 'https://github.com/sajjadhossan/smart-voices-keyboard/releases/latest/download/app-v1.4.1-build6-android.apk',
    windowsDownloadUrl: 'https://github.com/sajjadhossan/smart-voices-keyboard/releases/latest/download/SmartVoiceKeyboard-v1.4.1-windows-x64.exe',
    macosDownloadUrl: 'https://github.com/sajjadhossan/smart-voices-keyboard/releases/latest/download/SmartVoiceKeyboard-v1.4.1-macOS-universal.dmg',
    releaseNotes: {
      bn: 'নতুন সংস্করণ ১.৪.১ (Phase 1): পূর্ণাঙ্গ জিবোর্ড-স্টাইল লাইভ ইনলাইন অনুবাদ বার — টাইপ করার সাথে সাথে লাইভ প্রিভিউ, ডেবউন্সড এআই ট্রান্সলেশন, কুইক পাঠান বাটন এবং ডেস্কটপ লাইভ অটো-ইনজেক্ট।',
      en: 'New version 1.4.1 (Phase 1): Full Gboard-style live inline translation bar with real-time preview, debounced AI translation, quick commit button, and desktop live auto-inject.'
    },
    forceUpdate: false
  }
};

// Simple JWT-like Bearer token generator
function generateToken(user) {
  const payload = {
    id: user.id,
    tenant_id: user.tenant_id,
    role: user.role,
    exp: Date.now() + 24 * 60 * 60 * 1000
  };
  const str = Buffer.from(JSON.stringify(payload)).toString('base64');
  const signature = crypto.createHmac('sha256', 'SMART_KEYBOARD_SECRET_KEY').update(str).digest('hex');
  return `${str}.${signature}`;
}

function verifyToken(authHeader) {
  if (!authHeader || !authHeader.startsWith('Bearer ')) return null;
  const token = authHeader.substring(7);
  const parts = token.split('.');
  if (parts.length !== 2) return null;
  const [b64, signature] = parts;
  const expectedSig = crypto.createHmac('sha256', 'SMART_KEYBOARD_SECRET_KEY').update(b64).digest('hex');
  if (signature !== expectedSig) return null;
  try {
    const payload = JSON.parse(Buffer.from(b64, 'base64').toString('utf8'));
    if (payload.exp < Date.now()) return null;
    return payload;
  } catch (e) {
    return null;
  }
}

// Parse request body
function parseBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (e) {
        reject(e);
      }
    });
  });
}

const requestHandler = async (req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Tenant-Id');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = parsedUrl.pathname;
  const method = req.method;

  // 1. Version Parity Endpoint (Rule Section 10)
  if (pathname === '/api/app/version' && method === 'GET') {
    return sendSuccess(res, 200, 'App version info retrieved', db.version);
  }

  // 2. Health check
  if (pathname === '/api/health' && method === 'GET') {
    return sendSuccess(res, 200, 'Smart Voice Keyboard API is healthy', {
      status: 'UP',
      timestamp: new Date().toISOString()
    });
  }

  // 3. Auth Register
  if (pathname === '/api/auth/register' && method === 'POST') {
    try {
      const body = await parseBody(req);
      const { email, password, full_name, tenant_name } = body;

      if (!email || !password || !full_name) {
        return sendError(res, 422, 'Validation failed', 'validation_error', [
          { field: 'email', message: 'Email, password, and full_name are required' }
        ]);
      }

      if (db.users.find(u => u.email === email)) {
        return sendError(res, 409, 'User with this email already exists', 'duplicate_record');
      }

      const tenantId = 'ten_' + crypto.randomUUID().substring(0, 8);
      const newUser = {
        id: 'usr_' + crypto.randomUUID().substring(0, 8),
        tenant_id: tenantId,
        email: email,
        password_hash: crypto.createHash('sha256').update(password).digest('hex'),
        full_name: full_name,
        role: 'admin',
        created_at: new Date().toISOString()
      };

      db.users.push(newUser);
      const token = generateToken(newUser);

      return sendSuccess(res, 201, 'User registered successfully', {
        user: { id: newUser.id, email: newUser.email, full_name: newUser.full_name, tenant_id: newUser.tenant_id },
        token: token
      });
    } catch (e) {
      return sendError(res, 400, 'Invalid JSON body', 'validation_error');
    }
  }

  // 4. Auth Login
  if (pathname === '/api/auth/login' && method === 'POST') {
    try {
      const body = await parseBody(req);
      const { email, password } = body;

      const user = db.users.find(u => u.email === email);
      if (!user) {
        return sendError(res, 401, 'Invalid credentials', 'auth_invalid_credentials');
      }

      const inputHash = crypto.createHash('sha256').update(password).digest('hex');
      if (user.password_hash !== inputHash) {
        return sendError(res, 401, 'Invalid credentials', 'auth_invalid_credentials');
      }

      const token = generateToken(user);
      return sendSuccess(res, 200, 'Login successful', {
        user: { id: user.id, email: user.email, full_name: user.full_name, tenant_id: user.tenant_id },
        token: token
      });
    } catch (e) {
      return sendError(res, 400, 'Invalid JSON body', 'validation_error');
    }
  }

  // Auth Guard for Protected Endpoints
  const authUser = verifyToken(req.headers['authorization']);
  if (!authUser && pathname.startsWith('/api/clipboard')) {
    return sendError(res, 401, 'Unauthorized token missing or invalid', 'auth_token_expired');
  }

  // 5. Get Synced Clips
  if (pathname === '/api/clipboard' && method === 'GET') {
    const userClips = db.clips.filter(c => c.tenant_id === authUser.tenant_id && !c.deleted_at);
    return sendSuccess(res, 200, 'Clips retrieved', userClips, {
      page: 1,
      limit: 50,
      total: userClips.length,
      totalPages: 1
    });
  }

  // 6. Batch Sync Clips (Delta Sync from Android, Windows, macOS)
  if (pathname === '/api/clipboard/sync' && method === 'POST') {
    try {
      const body = await parseBody(req);
      const incomingClips = Array.isArray(body.clips) ? body.clips : [];
      const syncedResults = [];

      for (const item of incomingClips) {
        if (!item.text || !item.client_id) continue;

        // Check if exists
        const existing = db.clips.find(c => c.client_id === item.client_id && c.tenant_id === authUser.tenant_id);
        const now = new Date().toISOString();

        if (existing) {
          // Update existing
          existing.copy_count = (existing.copy_count || 1) + 1;
          existing.is_pinned = item.is_pinned !== undefined ? item.is_pinned : existing.is_pinned;
          existing.updated_at = now;
          existing.sync_version = (existing.sync_version || 1) + 1;
          syncedResults.push(existing);
        } else {
          const newRecord = {
            id: 'clip_' + crypto.randomUUID().substring(0, 8),
            tenant_id: authUser.tenant_id,
            user_id: authUser.id,
            client_id: item.client_id,
            text: item.text,
            source_device: item.source_device || 'unknown',
            is_pinned: !!item.is_pinned,
            copy_count: item.copy_count || 1,
            sync_version: 1,
            is_synced: true,
            created_at: now,
            updated_at: now,
            deleted_at: null
          };
          db.clips.unshift(newRecord);
          syncedResults.push(newRecord);
        }
      }

      return sendSuccess(res, 200, 'Sync completed successfully', {
        synced: syncedResults,
        total: db.clips.filter(c => c.tenant_id === authUser.tenant_id && !c.deleted_at).length
      });
    } catch (e) {
      return sendError(res, 400, 'Sync processing error', 'sync_conflict');
    }
  }

  // 7. Soft Delete Clip (Rule Section 1)
  if (pathname.startsWith('/api/clipboard/') && method === 'DELETE') {
    const clipId = pathname.replace('/api/clipboard/', '');
    const clip = db.clips.find(c => c.id === clipId && c.tenant_id === authUser.tenant_id);

    if (!clip) {
      return sendError(res, 404, 'Clip not found', 'not_found');
    }

    clip.deleted_at = new Date().toISOString();
    return sendSuccess(res, 200, 'Clip deleted successfully (soft delete)', { id: clipId });
  }

  // Fallback 404
  return sendError(res, 404, 'Endpoint not found', 'not_found');
};

const server = http.createServer(requestHandler);

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Smart Voice Keyboard Backend API running on port ${PORT}`);
  });
}

module.exports = { server, requestHandler, db, generateToken, verifyToken };
