const assert = require('node:assert/strict');
const { once } = require('node:events');
const http = require('node:http');
const test = require('node:test');
const jwt = require('jsonwebtoken');

test('user preferences are not disclosed to anonymous or other users', async (t) => {
  const previousSecret = process.env.JWT_SECRET;
  const secret = 'preferences-regression-test-secret';
  process.env.JWT_SECRET = secret;
  t.after(() => {
    if (previousSecret === undefined) delete process.env.JWT_SECRET;
    else process.env.JWT_SECRET = previousSecret;
  });

  // At the Node HTTP boundary, bind the application's listener to an isolated
  // loopback port. Routes, authentication, and storage all run unchanged.
  const originalListen = http.Server.prototype.listen;
  let server;
  http.Server.prototype.listen = function (...args) {
    server = this;
    const callback = args.find((arg) => typeof arg === 'function');
    return originalListen.call(this, 0, '127.0.0.1', ...(callback ? [callback] : []));
  };
  try {
    require('../server.js');
  } finally {
    http.Server.prototype.listen = originalListen;
  }
  t.after(async () => {
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  });
  if (!server.listening) await once(server, 'listening');
  const base = `http://127.0.0.1:${server.address().port}`;
  const tokenFor = (sub) => jwt.sign({ sub }, secret, { algorithm: 'HS256' });
  const victimToken = tokenFor('victim');
  const seed = await fetch(`${base}/prefs`, {
    method: 'POST',
    headers: { authorization: `Bearer ${victimToken}`, 'content-type': 'application/json' },
    body: JSON.stringify({ paths: ['threshold'], values: [7] }),
  });
  assert.equal(seed.status, 200, 'the owner can save an ordinary preference');
  assert.deepEqual(await seed.json(), { threshold: 7 }, 'the victim preference is stored');

  for (const [name, headers] of [
    ['anonymous client', {}],
    ['different authenticated user', { authorization: `Bearer ${tokenFor('other')}` }],
  ]) {
    await t.test(name, async () => {
      const response = await fetch(`${base}/prefs/victim`, { headers });
      const body = await response.text();
      // A denial, a removed endpoint, or a response scoped to the caller are
      // all acceptable; none may return the victim's seeded preference.
      let preferences;
      try { preferences = JSON.parse(body); } catch { preferences = null; }
      assert.ok(
        !(preferences && preferences.threshold === 7),
        `${name} must not receive the victim's notification preferences`,
      );
    });
  }
});
