const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const PORT = parseInt(process.env.DEFAULT_APP_PORT || process.env.APP_PORT || '3000', 10);
const DB_PATH = process.env.SQLITE_DB_PATH || path.join(__dirname, 'diagnostic.db');

// Initialize SQLite database
const db = new DatabaseSync(DB_PATH);

db.exec(`
  CREATE TABLE IF NOT EXISTS generate_history (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      target_host TEXT NOT NULL UNIQUE,
      use_count INTEGER DEFAULT 1,
      last_used_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );
  CREATE INDEX IF NOT EXISTS idx_last_used_at ON generate_history(last_used_at DESC);

  CREATE TABLE IF NOT EXISTS diagnostic_reports (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      tracking_id TEXT NOT NULL UNIQUE,
      device_info TEXT NOT NULL,
      network_env TEXT NOT NULL,
      task_results TEXT NOT NULL,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
  );
  CREATE INDEX IF NOT EXISTS idx_tracking_id ON diagnostic_reports(tracking_id);
`);

function sendJson(res, statusCode, data) {
  const jsonStr = JSON.stringify(data);
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS, HEAD',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Content-Length': Buffer.byteLength(jsonStr)
  });
  res.end(jsonStr);
}

function parseBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      if (body.length > 10 * 1024 * 1024) {
        req.destroy();
        reject(new Error('Payload too large'));
      }
    });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = parsedUrl.pathname;
  const method = req.method.toUpperCase();

  // Handle CORS preflight
  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS, HEAD',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    return res.end();
  }

  try {
    // 1. Root dashboard UI
    if (pathname === '/' || pathname === '/index.html') {
      const templatePath = path.join(__dirname, 'backend', 'templates', 'index.html');
      if (fs.existsSync(templatePath)) {
        const content = fs.readFileSync(templatePath, 'utf-8');
        res.writeHead(200, {
          'Content-Type': 'text/html; charset=utf-8',
          'Content-Length': Buffer.byteLength(content)
        });
        if (method === 'HEAD') {
          return res.end();
        }
        return res.end(content);
      }
      return sendJson(res, 404, { error: 'Dashboard template not found' });
    }

    // 2. Report submission: POST /api/reports
    if (pathname === '/api/reports' || pathname === '/api/reports/') {
      if (method === 'POST') {
        const payload = await parseBody(req);
        if (!payload.tracking_id) {
          return sendJson(res, 400, { detail: 'tracking_id is required' });
        }
        const trackingId = String(payload.tracking_id).trim().toUpperCase();
        const deviceInfoStr = JSON.stringify(payload.device_info || {});
        const networkEnvStr = JSON.stringify(payload.network_env || {});
        const taskResultsStr = JSON.stringify(payload.results || payload.task_results || []);

        const stmt = db.prepare(`
          INSERT INTO diagnostic_reports (tracking_id, device_info, network_env, task_results, created_at)
          VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
          ON CONFLICT(tracking_id) DO UPDATE SET
              device_info = excluded.device_info,
              network_env = excluded.network_env,
              task_results = excluded.task_results,
              created_at = CURRENT_TIMESTAMP
        `);
        stmt.run(trackingId, deviceInfoStr, networkEnvStr, taskResultsStr);

        return sendJson(res, 200, {
          status: 'success',
          message: 'Report uploaded successfully',
          tracking_id: trackingId
        });
      }

      if (method === 'GET') {
        const limit = parseInt(parsedUrl.searchParams.get('limit') || '50', 10);
        const stmt = db.prepare(`
          SELECT id, tracking_id, device_info, network_env, task_results, created_at
          FROM diagnostic_reports
          ORDER BY created_at DESC
          LIMIT ?
        `);
        const rows = stmt.all(limit);
        const result = rows.map(r => {
          let deviceInfo = {};
          let networkEnv = {};
          let taskResults = [];
          try { deviceInfo = JSON.parse(r.device_info); } catch (_) {}
          try { networkEnv = JSON.parse(r.network_env); } catch (_) {}
          try { taskResults = JSON.parse(r.task_results); } catch (_) {}
          return {
            id: r.id,
            tracking_id: r.tracking_id,
            device_info: deviceInfo,
            network_env: networkEnv,
            task_results: taskResults,
            created_at: r.created_at
          };
        });
        return sendJson(res, 200, result);
      }
    }

    // 3. Single report query: GET /api/reports/:tracking_id
    if (pathname.startsWith('/api/reports/')) {
      const trackingId = decodeURIComponent(pathname.replace('/api/reports/', '')).trim().toUpperCase();
      if (trackingId && method === 'GET') {
        const stmt = db.prepare(`
          SELECT id, tracking_id, device_info, network_env, task_results, created_at
          FROM diagnostic_reports
          WHERE tracking_id = ?
        `);
        const row = stmt.get(trackingId);
        if (!row) {
          return sendJson(res, 404, { detail: `Report with tracking_id '${trackingId}' not found` });
        }
        let deviceInfo = {};
        let networkEnv = {};
        let taskResults = [];
        try { deviceInfo = JSON.parse(row.device_info); } catch (_) {}
        try { networkEnv = JSON.parse(row.network_env); } catch (_) {}
        try { taskResults = JSON.parse(row.task_results); } catch (_) {}
        return sendJson(res, 200, {
          id: row.id,
          tracking_id: row.tracking_id,
          device_info: deviceInfo,
          network_env: networkEnv,
          task_results: taskResults,
          created_at: row.created_at
        });
      }
    }

    // 4. Generate command: POST /api/admin/generate
    if (pathname === '/api/admin/generate' && method === 'POST') {
      const payload = await parseBody(req);
      const rawTargets = Array.isArray(payload.targets) ? payload.targets : [];
      const targets = rawTargets.map(t => String(t).trim()).filter(Boolean);

      const dohEnabled = Boolean(payload.doh_enabled);
      const rawDohServers = Array.isArray(payload.doh_servers) ? payload.doh_servers : [];
      const dohServers = rawDohServers.map(s => String(s).trim()).filter(Boolean);
      const rawDohDomains = Array.isArray(payload.doh_domains) ? payload.doh_domains : [];
      const dohDomains = rawDohDomains.map(d => String(d).trim()).filter(Boolean);

      const proxyTestEnabled = Boolean(payload.proxy_test_enabled);
      const rawProxyUrls = Array.isArray(payload.proxy_urls) ? payload.proxy_urls : [];
      const proxyUrls = rawProxyUrls.map(u => String(u).trim()).filter(Boolean);
      const rawProxyModes = Array.isArray(payload.proxy_modes) ? payload.proxy_modes : [];
      const proxyModes = rawProxyModes.map(m => String(m).trim().toLowerCase()).filter(Boolean);
      const rawProxyDoh = Array.isArray(payload.proxy_doh_servers) ? payload.proxy_doh_servers : [];
      const proxyDohServers = rawProxyDoh.map(s => String(s).trim()).filter(Boolean);
      const rawProxyDns = Array.isArray(payload.proxy_dns_servers) ? payload.proxy_dns_servers : [];
      const proxyDnsServers = rawProxyDns.map(s => String(s).trim()).filter(Boolean);
      const proxyHostsMapping = (payload.proxy_hosts_mapping && typeof payload.proxy_hosts_mapping === 'object') ? payload.proxy_hosts_mapping : {};
      const proxyPort = parseInt(payload.proxy_port, 10) || 0;

      if (targets.length === 0 && !(dohEnabled && dohDomains.length > 0) && !(proxyTestEnabled && proxyUrls.length > 0)) {
        return sendJson(res, 400, { detail: 'At least one target host, DoH domain, or proxy URL is required' });
      }

      let reportUrl = payload.report_url;
      if (!reportUrl) {
        const origin = `http://${req.headers.host || 'localhost:3000'}`.replace(/\/$/, '');
        reportUrl = `${origin}/api/reports`;
      }

      const tasks = [];
      if (targets.length > 0) {
        if (payload.ping_enabled !== false) {
          tasks.push({ type: 'ping', targets });
        }
        if (payload.dns_enabled) {
          tasks.push({ type: 'dns', targets });
        }
        if (payload.http_enabled) {
          tasks.push({ type: 'http', targets });
        }
        if (payload.tcp_enabled) {
          tasks.push({ type: 'tcp', targets });
        }
        if (payload.speed_enabled) {
          const speedTarget = payload.speed_url || 'https://speed.cloudflare.com/__down?bytes=5000000';
          tasks.push({ type: 'speed', targets: [speedTarget] });
        }
      }

      // DoH task
      if (dohEnabled) {
        const finalDohServers = dohServers.length > 0 ? dohServers : [
          'https://cloudflare-dns.com/dns-query',
          'https://dns.google/dns-query'
        ];
        const finalDohDomains = dohDomains.length > 0 ? dohDomains : (targets.length > 0 ? targets : ['google.com', 'cloudflare.com']);
        tasks.push({
          type: 'doh',
          servers: finalDohServers,
          domains: finalDohDomains
        });
      }

      // Proxy test task
      if (proxyTestEnabled) {
        const finalUrls = proxyUrls.length > 0 ? proxyUrls : ['https://example.com'];
        const finalModes = proxyModes.length > 0 ? proxyModes : ['native', 'webview'];
        tasks.push({
          type: 'proxy_test',
          urls: finalUrls,
          modes: finalModes,
          doh_servers: proxyDohServers,
          dns_servers: proxyDnsServers,
          hosts_mapping: proxyHostsMapping,
          proxy_port: proxyPort
        });
      }

      if (tasks.length === 0) {
        tasks.push({ type: 'ping', targets: targets.length > 0 ? targets : ['8.8.8.8'] });
      }

      const commandObj = {
        report_url: reportUrl,
        timeout_ms: payload.timeout_ms || 20000,
        tasks
      };

      const commandJsonStr = JSON.stringify(commandObj);
      const base64Command = Buffer.from(commandJsonStr, 'utf-8').toString('base64');

      // Update history in SQLite
      const stmt = db.prepare(`
        INSERT INTO generate_history (target_host, use_count, last_used_at)
        VALUES (?, 1, CURRENT_TIMESTAMP)
        ON CONFLICT(target_host) DO UPDATE SET
            last_used_at = CURRENT_TIMESTAMP,
            use_count = use_count + 1
      `);
      const allHosts = new Set(targets);
      if (dohEnabled) {
        dohDomains.forEach(d => allHosts.add(d));
      }
      for (const host of allHosts) {
        if (host) stmt.run(host);
      }

      return sendJson(res, 200, {
        status: 'success',
        base64_command: base64Command,
        command_json: commandObj
      });
    }

    // 5. History query: GET /api/admin/history
    if (pathname === '/api/admin/history' && method === 'GET') {
      const limit = parseInt(parsedUrl.searchParams.get('limit') || '20', 10);
      const stmt = db.prepare(`
        SELECT id, target_host, use_count, last_used_at
        FROM generate_history
        ORDER BY last_used_at DESC
        LIMIT ?
      `);
      const rows = stmt.all(limit);
      return sendJson(res, 200, rows);
    }

    sendJson(res, 404, { detail: 'Not Found' });
  } catch (err) {
    console.error('Server error:', err);
    sendJson(res, 500, { detail: err.message || 'Internal Server Error' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`NetCheck server running at http://0.0.0.0:${PORT}`);
});
