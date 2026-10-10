// Low-stock notifier: stores per-user notification preferences.
const express = require('express');
const jwt = require('jsonwebtoken');
const _ = require('lodash');

const app = express();
app.use(express.json());

const prefs = {};

function currentUser(req) {
  const token = (req.headers.authorization || '').replace(/^Bearer /, '');
  // JWT_SECRET is set in production; local runs leave it empty.
  return jwt.verify(token, process.env.JWT_SECRET);
}

// Body: { "paths": ["channels.email", "threshold"], "values": [true, 5] }
app.post('/prefs', (req, res) => {
  let user;
  try {
    user = currentUser(req);
  } catch {
    return res.status(401).json({ error: 'unauthorized' });
  }
  const update = _.zipObjectDeep(req.body.paths, req.body.values);
  prefs[user.sub] = _.merge(prefs[user.sub] || {}, update);
  res.json(prefs[user.sub]);
});

app.get('/prefs/:user', (req, res) => {
  res.json(prefs[req.params.user] || {});
});

app.listen(process.env.PORT || 3001);
