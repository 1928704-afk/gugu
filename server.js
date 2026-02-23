const express = require('express');
const path = require('path');
const Database = require('better-sqlite3');
const session = require('express-session');
const FileStore = require('session-file-store')(session);

const app = express();
const PORT = process.env.PORT || 3001;

// DB 초기화
const db = new Database(path.join(__dirname, 'goguma.db'));

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    created_at TEXT DEFAULT (datetime('now'))
  );
  CREATE TABLE IF NOT EXISTS gogumas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    hp INTEGER DEFAULT 10,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id)
  );
`);

// 세션 (파일 저장 → 재접속·서버 재시작 후에도 유지)
app.use(session({
  store: new FileStore({ path: path.join(__dirname, 'sessions') }),
  secret: 'goguma-secret-key-change-in-production',
  resave: false,
  saveUninitialized: false,
  cookie: { maxAge: 7 * 24 * 60 * 60 * 1000 }
}));

app.use(express.json());
app.use(express.static(__dirname));

// 루트(/) 접속 시 앱 페이지로 이동
app.get('/', (req, res) => {
  res.redirect('/goguma-app.html');
});

// 현재 유저 + 고구마 목록
app.get('/api/me', (req, res) => {
  if (!req.session.userId) {
    return res.json({ user: null, gogumas: [] });
  }
  const user = db.prepare('SELECT id, name FROM users WHERE id = ?').get(req.session.userId);
  if (!user) {
    req.session.userId = null;
    return res.json({ user: null, gogumas: [] });
  }
  const gogumas = db.prepare('SELECT id, name, hp FROM gogumas WHERE user_id = ? ORDER BY id').all(user.id);
  res.json({ user: { id: user.id, name: user.name }, gogumas });
});

// 시작(로그인): 이름 입력 → 유저 생성/조회, 세션 설정
app.post('/api/start', (req, res) => {
  const userName = (req.body.userName || '').trim();
  if (!userName) {
    return res.status(400).json({ error: '이름을 입력해 주세요.' });
  }
  let user = db.prepare('SELECT id, name FROM users WHERE name = ?').get(userName);
  if (!user) {
    const stmt = db.prepare('INSERT INTO users (name) VALUES (?)');
    stmt.run(userName);
    user = db.prepare('SELECT id, name FROM users WHERE name = ?').get(userName);
  }
  req.session.userId = user.id;
  const gogumas = db.prepare('SELECT id, name, hp FROM gogumas WHERE user_id = ? ORDER BY id').all(user.id);
  res.json({ user: { id: user.id, name: user.name }, gogumas });
});

// 고구마 추가
app.post('/api/goguma/add', (req, res) => {
  if (!req.session.userId) return res.status(401).json({ error: '로그인이 필요합니다.' });
  const name = (req.body.name || '').trim();
  if (!name) return res.status(400).json({ error: '고구마 이름을 입력해 주세요.' });
  const count = db.prepare('SELECT COUNT(*) as c FROM gogumas WHERE user_id = ?').get(req.session.userId).c;
  if (count >= 10) return res.status(400).json({ error: '최대 10명까지 가능합니다.' });
  const stmt = db.prepare('INSERT INTO gogumas (user_id, name, hp) VALUES (?, ?, 10)');
  const info = stmt.run(req.session.userId, name);
  const row = db.prepare('SELECT id, name, hp FROM gogumas WHERE id = ?').get(info.lastInsertRowid);
  res.json({ goguma: row });
});

// 고구마 HP 증가
app.post('/api/goguma/grow', (req, res) => {
  if (!req.session.userId) return res.status(401).json({ error: '로그인이 필요합니다.' });
  const id = parseInt(req.body.id, 10);
  const val = parseInt(req.body.val, 10) || 0;
  const row = db.prepare('SELECT id, hp FROM gogumas WHERE id = ? AND user_id = ?').get(id, req.session.userId);
  if (!row) return res.status(404).json({ error: '고구마를 찾을 수 없습니다.' });
  const newHp = Math.min(100, row.hp + val);
  db.prepare('UPDATE gogumas SET hp = ? WHERE id = ?').run(newHp, id);
  res.json({ id, hp: newHp });
});

// 고구마 삭제
app.post('/api/goguma/remove', (req, res) => {
  if (!req.session.userId) return res.status(401).json({ error: '로그인이 필요합니다.' });
  const id = parseInt(req.body.id, 10);
  const result = db.prepare('DELETE FROM gogumas WHERE id = ? AND user_id = ?').run(id, req.session.userId);
  if (result.changes === 0) return res.status(404).json({ error: '고구마를 찾을 수 없습니다.' });
  res.json({ ok: true });
});

// 로그아웃
app.post('/api/logout', (req, res) => {
  req.session.userId = null;
  req.session.destroy(() => {});
  res.json({ ok: true });
});

// 랭킹 조회
app.get('/api/ranking', (req, res) => {
  const rows = db.prepare(`
    SELECT users.name as userName,
           gogumas.name as gogumaName,
           gogumas.hp as hp
    FROM gogumas
    JOIN users ON gogumas.user_id = users.id
    ORDER BY gogumas.hp DESC,
             gogumas.name ASC
    LIMIT 50
  `).all();

  res.json(rows);
});

app.listen(PORT, () => {
  console.log('고구마 전도 서버: http://localhost:' + PORT);
});
