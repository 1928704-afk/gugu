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
  CREATE TABLE IF NOT EXISTS actions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    goguma_id INTEGER NOT NULL,
    action_type TEXT NOT NULL,
    action_date TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (goguma_id) REFERENCES gogumas(id),
    UNIQUE(user_id, goguma_id, action_type, action_date)
  );
  CREATE TABLE IF NOT EXISTS user_activity (
    user_id INTEGER PRIMARY KEY,
    last_visit_date TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
  );
  CREATE TABLE IF NOT EXISTS posts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id)
  );
`);

const ACTION_VALUES = {
  bible: 1,   // 말씀읽기
  prayer: 1,  // 기도(부탁)하기
  contact: 3, // 연락하기
  meeting: 5, // 만나기
  invite: 8   // 권유하기
};

function getTodayDate() {
  return new Date().toISOString().slice(0, 10); // YYYY-MM-DD (UTC 기준)
}

function isTester(userId) {
  const row = db.prepare('SELECT name FROM users WHERE id = ?').get(userId);
  return !!(row && row.name === '권진호');
}

function diffDays(from, to) {
  const t1 = new Date(from + 'T00:00:00Z').getTime();
  const t2 = new Date(to + 'T00:00:00Z').getTime();
  return Math.floor((t1 - t2) / (24 * 60 * 60 * 1000));
}

function applyInactivityPenalty(userId) {
  const today = getTodayDate();
  const activityRow = db
    .prepare('SELECT last_visit_date FROM user_activity WHERE user_id = ?')
    .get(userId);

  if (!activityRow) {
    db.prepare('INSERT INTO user_activity (user_id, last_visit_date) VALUES (?, ?)').run(
      userId,
      today
    );
    return;
  }

  const last = activityRow.last_visit_date;
  if (!last) {
    db.prepare('UPDATE user_activity SET last_visit_date = ? WHERE user_id = ?').run(
      today,
      userId
    );
    return;
  }

  const days = diffDays(today, last);
  if (days <= 0) {
    if (days < 0) {
      db.prepare('UPDATE user_activity SET last_visit_date = ? WHERE user_id = ?').run(
        today,
        userId
      );
    }
    return;
  }

  const penalty = 5 * days;
  db.prepare('UPDATE gogumas SET hp = MAX(0, hp - ?) WHERE user_id = ?').run(penalty, userId);
  db.prepare('UPDATE user_activity SET last_visit_date = ? WHERE user_id = ?').run(today, userId);
}

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

  applyInactivityPenalty(user.id);

  const gogumas = db
    .prepare('SELECT id, name, hp FROM gogumas WHERE user_id = ? ORDER BY id')
    .all(user.id);
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

  applyInactivityPenalty(user.id);

  const gogumas = db
    .prepare('SELECT id, name, hp FROM gogumas WHERE user_id = ? ORDER BY id')
    .all(user.id);
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

// 고구마 HP 증가 (행동별로 하루 1회 제한)
app.post('/api/goguma/grow', (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const id = parseInt(req.body.id, 10);
  const actionType = (req.body.actionType || '').trim();

  if (!id || !ACTION_VALUES[actionType]) {
    return res.status(400).json({ error: '잘못된 요청입니다.' });
  }

  const row = db
    .prepare('SELECT id, hp FROM gogumas WHERE id = ? AND user_id = ?')
    .get(id, req.session.userId);

  if (!row) {
    return res.status(404).json({ error: '고구마를 찾을 수 없습니다.' });
  }

  // 테스터(권진호)는 하루 제한 없이 가능
  if (isTester(req.session.userId)) {
    const valTester = ACTION_VALUES[actionType];
    const newHpTester = Math.min(100, row.hp + valTester);
    db.prepare('UPDATE gogumas SET hp = ? WHERE id = ?').run(newHpTester, id);
    return res.json({ id, hp: newHpTester });
  }

  const today = getTodayDate();

  const existing = db
    .prepare(
      'SELECT id FROM actions WHERE user_id = ? AND goguma_id = ? AND action_type = ? AND action_date = ?'
    )
    .get(req.session.userId, id, actionType, today);

  if (existing) {
    return res
      .status(400)
      .json({ error: '이 버튼은 오늘 이미 사용했습니다. 내일 다시 눌러 주세요.' });
  }

  const val = ACTION_VALUES[actionType];
  const newHp = Math.min(100, row.hp + val);

  const insertAction = db.prepare(
    'INSERT INTO actions (user_id, goguma_id, action_type, action_date) VALUES (?, ?, ?, ?)'
  );

  try {
    insertAction.run(req.session.userId, id, actionType, today);
  } catch (e) {
    return res
      .status(400)
      .json({ error: '이 버튼은 오늘 이미 사용했습니다. 내일 다시 눌러 주세요.' });
  }

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

// 게시글 목록
app.get('/api/posts', (req, res) => {
  const rows = db.prepare(`
    SELECT p.id,
           p.user_id AS userId,
           p.title,
           p.content,
           p.created_at,
           u.name AS userName
    FROM posts p
    JOIN users u ON p.user_id = u.id
    ORDER BY p.id DESC
    LIMIT 50
  `).all();

  res.json(rows);
});

// 게시글 추가
app.post('/api/posts/add', (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const title = (req.body.title || '').trim();
  const content = (req.body.content || '').trim();

  if (!title) {
    return res.status(400).json({ error: '제목을 입력해 주세요.' });
  }
  if (!content) {
    return res.status(400).json({ error: '내용을 입력해 주세요.' });
  }
  if (title.length > 100) {
    return res.status(400).json({ error: '제목은 100자 이내로 작성해 주세요.' });
  }
  if (content.length > 1000) {
    return res.status(400).json({ error: '내용은 1000자 이내로 작성해 주세요.' });
  }

  const insert = db.prepare(`
    INSERT INTO posts (user_id, title, content)
    VALUES (?, ?, ?)
  `);
  const info = insert.run(req.session.userId, title, content);

  const row = db.prepare(`
    SELECT p.id,
           p.user_id AS userId,
           p.title,
           p.content,
           p.created_at,
           u.name AS userName
    FROM posts p
    JOIN users u ON p.user_id = u.id
    WHERE p.id = ?
  `).get(info.lastInsertRowid);

  res.json({ post: row });
});

// 게시글 삭제
app.post('/api/posts/delete', (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const id = parseInt(req.body.id, 10);
  if (!id) {
    return res.status(400).json({ error: '잘못된 요청입니다.' });
  }

  const stmt = db.prepare('DELETE FROM posts WHERE id = ? AND user_id = ?');
  const result = stmt.run(id, req.session.userId);

  if (result.changes === 0) {
    return res.status(404).json({ error: '게시글을 찾을 수 없거나 권한이 없습니다.' });
  }

  res.json({ ok: true });
});

app.listen(PORT, () => {
  console.log('고구마 전도 서버: http://localhost:' + PORT);
});
