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
    department TEXT NOT NULL DEFAULT '미지정',
    created_at TEXT DEFAULT (datetime('now'))
  );
  CREATE TABLE IF NOT EXISTS gogumas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    relation TEXT,
    age INTEGER,
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
  CREATE TABLE IF NOT EXISTS post_likes (
    post_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE(post_id, user_id)
  );
  CREATE TABLE IF NOT EXISTS post_comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    post_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    content TEXT NOT NULL,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (post_id) REFERENCES posts(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
  );
  CREATE TABLE IF NOT EXISTS mission_rewards (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    mission_key TEXT NOT NULL,
    period_key TEXT NOT NULL,
    reward_hp INTEGER NOT NULL DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE(user_id, mission_key, period_key)
  );
`);

function ensureColumn(tableName, columnName, definition) {
  const columns = db.prepare(`PRAGMA table_info(${tableName})`).all();
  const exists = columns.some((column) => column.name === columnName);
  if (!exists) {
    db.exec(`ALTER TABLE ${tableName} ADD COLUMN ${columnName} ${definition}`);
  }
}

ensureColumn('user_activity', 'total_visit_days', 'INTEGER NOT NULL DEFAULT 1');
ensureColumn('user_activity', 'rewarded_cycle', 'INTEGER NOT NULL DEFAULT 0');
ensureColumn('posts', 'image_data', 'TEXT');
ensureColumn('posts', 'category', "TEXT NOT NULL DEFAULT '출석인사'");
ensureColumn('users', 'department', "TEXT NOT NULL DEFAULT '미지정'");
ensureColumn('gogumas', 'stage2_action_lock', 'TEXT');
ensureColumn('gogumas', 'stage3_action_lock', 'TEXT');
ensureColumn('gogumas', 'relation', 'TEXT');
ensureColumn('gogumas', 'age', 'INTEGER');

db.exec(`
  UPDATE user_activity
  SET total_visit_days = COALESCE(total_visit_days, 1),
      rewarded_cycle = COALESCE(rewarded_cycle, 0)
`);

const ACTION_VALUES = {
  postWrite: 1, // 게시판 작성
  bible: 1,   // 말씀읽기
  prayer: 1,  // 기도(부탁)하기
  contact: 2, // 연락&만남
  invite: 8   // 권유하기
};
const ACTION_PRIORITY = ['invite', 'contact', 'postWrite', 'bible', 'prayer'];
const MISSION_KEYS = {
  daily: 'daily_core3'
};
const MISSION_REWARDS = { daily: 2 };
const WEEKLY_MISSION_SCHEDULE = [
  {
    phase: 1,
    key: 'weekly_w1_post3',
    label: '주간 미션 (1주차)',
    description: '게시판 작성 3회 달성',
    type: 'actionCount',
    actionType: 'postWrite',
    target: 3,
    rewardHp: 6
  },
  {
    phase: 2,
    key: 'weekly_w2_meeting2',
    label: '주간 미션 (2주차)',
    description: '연락&만남 2회 달성',
    type: 'actionCount',
    actionType: 'contact',
    target: 2,
    rewardHp: 6
  },
  {
    phase: 3,
    key: 'weekly_w3_meeting1_invite1',
    label: '주간 미션 (3주차)',
    description: '연락&만남 1회 + 권유 1회 달성',
    type: 'multiActionCount',
    requirements: [
      { actionType: 'contact', target: 1 },
      { actionType: 'invite', target: 1 }
    ],
    rewardHp: 6
  },
  {
    phase: 4,
    key: 'weekly_w4_invite2',
    label: '주간 미션 (4주차)',
    description: '권유 2회 달성',
    type: 'actionCount',
    actionType: 'invite',
    target: 2,
    rewardHp: 6
  },
  {
    phase: 5,
    key: 'weekly_w5_contact3',
    label: '주간 미션 (5주차)',
    description: '연락 3회 달성',
    type: 'actionCount',
    actionType: 'contact',
    target: 3,
    rewardHp: 6
  }
];

const COMMUNITY_CATEGORIES = ['출석인사', '간식당첨', '기도부탁', '만남인증', '묵상나눔'];
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const INPUT_LIMITS = {
  userName: 20,
  gogumaName: 20,
  relation: 30,
  postTitle: 100,
  postContent: 1000,
  comment: 300
};
const ALLOWED_DEPARTMENTS = ['언약부', '밀알부', '이레부'];
const rateLimitStore = new Map();
let lastRateLimitCleanupAt = 0;

function getTodayDate() {
  // YYYY-MM-DD (KST 기준)
  return new Date(Date.now() + KST_OFFSET_MS).toISOString().slice(0, 10);
}

function sanitizeTextInput(value) {
  if (typeof value !== 'string') return '';
  return value
    .replace(/[\u0000-\u001F\u007F]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function validateDisplayName(value, maxLen) {
  const text = sanitizeTextInput(value);
  if (!text) {
    return { ok: false, value: '', error: '입력값을 확인해 주세요.' };
  }
  if (text.length > maxLen) {
    return { ok: false, value: '', error: `최대 ${maxLen}자까지 입력할 수 있습니다.` };
  }
  return { ok: true, value: text, error: null };
}

function getRateLimitActor(req) {
  if (req.session && req.session.userId) return `u:${req.session.userId}`;
  const sid = req.sessionID ? `s:${req.sessionID}` : '';
  const ip = req.ip || req.headers['x-forwarded-for'] || req.connection?.remoteAddress || 'unknown';
  return sid || `ip:${ip}`;
}

function createRateLimiter({ keyPrefix, windowMs, max, message }) {
  return (req, res, next) => {
    const actor = getRateLimitActor(req);
    const bucketKey = `${keyPrefix}:${actor}`;
    const now = Date.now();
    if (rateLimitStore.size > 5000 || now - lastRateLimitCleanupAt > 5 * 60 * 1000) {
      for (const [key, state] of rateLimitStore.entries()) {
        if (!state || state.resetAt <= now) {
          rateLimitStore.delete(key);
        }
      }
      lastRateLimitCleanupAt = now;
    }

    let state = rateLimitStore.get(bucketKey);
    if (!state || state.resetAt <= now) {
      state = { count: 0, resetAt: now + windowMs };
    }

    state.count += 1;
    rateLimitStore.set(bucketKey, state);

    if (state.count > max) {
      const retrySec = Math.max(1, Math.ceil((state.resetAt - now) / 1000));
      res.set('Retry-After', String(retrySec));
      return res.status(429).json({ error: message || '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.' });
    }
    return next();
  };
}

function applyBasicSecurityHeaders(req, res, next) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  next();
}

function getWeekStartDate(baseDateText) {
  // baseDateText는 KST 날짜 문자열이므로, 요일 계산만 UTC 메서드로 사용해도 안전하다.
  const d = new Date(baseDateText + 'T00:00:00Z');
  const day = d.getUTCDay(); // 0=Sun, 1=Mon...
  const diffToMonday = day === 0 ? 6 : day - 1;
  d.setUTCDate(d.getUTCDate() - diffToMonday);
  return d.toISOString().slice(0, 10);
}

function getWeeklyMissionPhase(weekStart) {
  const currentWeekStart = new Date(weekStart + 'T00:00:00Z');
  const firstDayOfMonth = new Date(Date.UTC(currentWeekStart.getUTCFullYear(), currentWeekStart.getUTCMonth(), 1));
  const firstDayWeek = firstDayOfMonth.getUTCDay(); // 0=Sun, 1=Mon...
  const addDaysToMonday = firstDayWeek === 0 ? 1 : (8 - firstDayWeek) % 7;
  const firstMonday = new Date(firstDayOfMonth);
  firstMonday.setUTCDate(firstDayOfMonth.getUTCDate() + addDaysToMonday);

  if (currentWeekStart.getTime() < firstMonday.getTime()) {
    return 1;
  }

  const diffWeeks = Math.floor((currentWeekStart.getTime() - firstMonday.getTime()) / (7 * 24 * 60 * 60 * 1000));
  const phase = diffWeeks + 1;
  return Math.min(WEEKLY_MISSION_SCHEDULE.length, Math.max(1, phase));
}

function pickWeeklyMissionVariant(weekStart) {
  const phase = getWeeklyMissionPhase(weekStart);
  return WEEKLY_MISSION_SCHEDULE.find((v) => v.phase === phase) || WEEKLY_MISSION_SCHEDULE[0];
}

function getMissionState(userId) {
  const today = getTodayDate();
  const weekStart = getWeekStartDate(today);
  const weekEndDate = new Date(weekStart + 'T00:00:00Z');
  weekEndDate.setUTCDate(weekEndDate.getUTCDate() + 6);
  const weekEnd = weekEndDate.toISOString().slice(0, 10);

  const dailyActionRows = db.prepare(`
    SELECT action_type
    FROM actions
    WHERE user_id = ?
      AND action_date LIKE ?
    GROUP BY action_type
  `).all(userId, today + '%');
  const dailyActionSet = new Set(dailyActionRows.map((row) => row.action_type));
  const dailyRequired = ['bible', 'prayer', 'contact'];
  const dailyCompletedCount = dailyRequired.filter((key) => dailyActionSet.has(key)).length;

  const dailyClaimed = !!db.prepare(`
    SELECT 1
    FROM mission_rewards
    WHERE user_id = ?
      AND mission_key = ?
      AND period_key = ?
  `).get(userId, MISSION_KEYS.daily, today);

  const weeklyVariant = pickWeeklyMissionVariant(weekStart);
  let weeklyProgress = 0;
  let weeklyTarget = Number(weeklyVariant.target) || 0;
  let weeklyCompleted = false;
  if (weeklyVariant.type === 'actionCount' && weeklyVariant.actionType) {
    const weeklyRow = db.prepare(`
      SELECT COUNT(*) AS count
      FROM actions
      WHERE user_id = ?
        AND action_type = ?
        AND substr(action_date, 1, 10) BETWEEN ? AND ?
    `).get(userId, weeklyVariant.actionType, weekStart, weekEnd);
    weeklyProgress = Number(weeklyRow && weeklyRow.count) || 0;
    weeklyCompleted = weeklyProgress >= weeklyTarget;
  } else if (weeklyVariant.type === 'multiActionCount' && Array.isArray(weeklyVariant.requirements)) {
    weeklyTarget = weeklyVariant.requirements.reduce((sum, req) => sum + (Number(req.target) || 0), 0);
    const actionCounts = {};
    weeklyVariant.requirements.forEach((req) => {
      const weeklyRow = db.prepare(`
        SELECT COUNT(*) AS count
        FROM actions
        WHERE user_id = ?
          AND action_type = ?
          AND substr(action_date, 1, 10) BETWEEN ? AND ?
      `).get(userId, req.actionType, weekStart, weekEnd);
      actionCounts[req.actionType] = Number(weeklyRow && weeklyRow.count) || 0;
    });
    weeklyProgress = weeklyVariant.requirements.reduce((sum, req) => {
      const target = Number(req.target) || 0;
      const count = Number(actionCounts[req.actionType]) || 0;
      return sum + Math.min(count, target);
    }, 0);
    weeklyCompleted = weeklyVariant.requirements.every((req) => {
      const target = Number(req.target) || 0;
      const count = Number(actionCounts[req.actionType]) || 0;
      return count >= target;
    });
  }

  const weeklyClaimed = !!db.prepare(`
    SELECT 1
    FROM mission_rewards
    WHERE user_id = ?
      AND mission_key = ?
      AND period_key = ?
  `).get(userId, weeklyVariant.key, weekStart);

  return {
    daily: {
      key: MISSION_KEYS.daily,
      label: '오늘의 3종 미션',
      periodKey: today,
      rewardHp: MISSION_REWARDS.daily,
      requiredActions: dailyRequired,
      completedActions: dailyRequired.filter((key) => dailyActionSet.has(key)),
      progress: { current: dailyCompletedCount, total: dailyRequired.length },
      completed: dailyCompletedCount >= dailyRequired.length,
      claimed: dailyClaimed
    },
    weekly: {
      key: weeklyVariant.key,
      label: weeklyVariant.label,
      description: weeklyVariant.description,
      periodKey: weekStart,
      rewardHp: weeklyVariant.rewardHp,
      weekStart,
      weekEnd,
      progress: { current: weeklyProgress, total: weeklyTarget },
      completed: weeklyCompleted,
      claimed: weeklyClaimed
    }
  };
}

function getGogumaActionScores(userId, gogumaId) {
  const rows = db
    .prepare(`
      SELECT action_type, COUNT(*) AS count
      FROM actions
      WHERE user_id = ? AND goguma_id = ?
      GROUP BY action_type
    `)
    .all(userId, gogumaId);

  const scores = {};
  rows.forEach((row) => {
    const actionType = row.action_type;
    if (!ACTION_VALUES[actionType]) return;
    const count = Number(row.count) || 0;
    scores[actionType] = count * ACTION_VALUES[actionType];
  });
  return scores;
}

function getTodayActionFlags(userId, gogumaId) {
  const today = getTodayDate();
  const rows = db.prepare(`
    SELECT action_type
    FROM actions
    WHERE user_id = ?
      AND goguma_id = ?
      AND action_date = ?
  `).all(userId, gogumaId, today);

  const flags = {};
  rows.forEach((row) => {
    if (!ACTION_VALUES[row.action_type]) return;
    flags[row.action_type] = true;
  });
  return flags;
}

function getRecentDateKeys(days) {
  const today = new Date(getTodayDate() + 'T00:00:00Z');
  const keys = [];
  for (let i = days - 1; i >= 0; i -= 1) {
    const d = new Date(today);
    d.setUTCDate(today.getUTCDate() - i);
    keys.push(d.toISOString().slice(0, 10));
  }
  return keys;
}

function getGogumaHistory(userId, gogumaId) {
  const dateKeys = getRecentDateKeys(7);
  const startDate = dateKeys[0];
  const endDate = dateKeys[dateKeys.length - 1];

  const rows = db.prepare(`
    SELECT substr(action_date, 1, 10) AS day,
           action_type,
           COUNT(*) AS count
    FROM actions
    WHERE user_id = ?
      AND goguma_id = ?
      AND substr(action_date, 1, 10) BETWEEN ? AND ?
    GROUP BY day, action_type
    ORDER BY day ASC
  `).all(userId, gogumaId, startDate, endDate);

  const dayMap = {};
  dateKeys.forEach((day) => {
    dayMap[day] = { date: day, score: 0, actionCount: 0, actions: {} };
  });

  rows.forEach((row) => {
    if (!dayMap[row.day]) return;
    const actionType = row.action_type;
    const count = Number(row.count) || 0;
    const value = Number(ACTION_VALUES[actionType]) || 0;
    dayMap[row.day].actions[actionType] = count;
    dayMap[row.day].actionCount += count;
    dayMap[row.day].score += count * value;
  });

  const recentActions = db.prepare(`
    SELECT action_type, action_date
    FROM actions
    WHERE user_id = ?
      AND goguma_id = ?
    ORDER BY id DESC
    LIMIT 20
  `).all(userId, gogumaId).map((row) => ({
    actionType: row.action_type,
    actionDate: String(row.action_date || '').slice(0, 10),
    score: Number(ACTION_VALUES[row.action_type]) || 0
  }));

  return {
    last7Days: dateKeys.map((day) => dayMap[day]),
    recentActions
  };
}

function getDominantAction(scores) {
  let winner = null;
  let best = -1;
  ACTION_PRIORITY.forEach((actionType) => {
    const score = Number(scores[actionType]) || 0;
    if (score > best) {
      best = score;
      winner = actionType;
    }
  });
  return best > 0 ? winner : null;
}

function getStageFromHp(hp) {
  const value = Number(hp) || 0;
  if (value < 20) return 1;
  if (value < 40) return 2;
  if (value < 60) return 3;
  if (value < 80) return 4;
  return 5;
}

function toGogumaPayload(row, userId) {
  const scores = getGogumaActionScores(userId, row.id);
  return {
    id: row.id,
    name: row.name,
    relation: row.relation || '',
    age: row.age != null ? Number(row.age) : null,
    hp: row.hp,
    dominantAction: getDominantAction(scores),
    stage2ActionLock: row.stage2_action_lock || null,
    stage3ActionLock: row.stage3_action_lock || null,
    actionScores: scores,
    todayActions: getTodayActionFlags(userId, row.id)
  };
}

function getUserGogumas(userId) {
  const rows = db
    .prepare('SELECT id, name, relation, age, hp, stage2_action_lock, stage3_action_lock FROM gogumas WHERE user_id = ? ORDER BY id')
    .all(userId);
  rows.forEach((row) => {
    const stage = getStageFromHp(row.hp);
    if (stage < 2) return;
    if (row.stage2_action_lock && (stage < 3 || row.stage3_action_lock)) return;

    const scores = getGogumaActionScores(userId, row.id);
    const dominantAction = getDominantAction(scores);
    if (!dominantAction) return;

    const nextStage2Lock = row.stage2_action_lock || (stage >= 2 ? dominantAction : null);
    const nextStage3Lock = row.stage3_action_lock || (stage >= 3 ? dominantAction : null);
    db.prepare('UPDATE gogumas SET stage2_action_lock = COALESCE(?, stage2_action_lock), stage3_action_lock = COALESCE(?, stage3_action_lock) WHERE id = ?')
      .run(nextStage2Lock, nextStage3Lock, row.id);
    row.stage2_action_lock = nextStage2Lock || row.stage2_action_lock;
    row.stage3_action_lock = nextStage3Lock || row.stage3_action_lock;
  });
  return rows.map((row) => toGogumaPayload(row, userId));
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
    .prepare('SELECT last_visit_date, total_visit_days, rewarded_cycle FROM user_activity WHERE user_id = ?')
    .get(userId);

  if (!activityRow) {
    db.prepare(
      'INSERT INTO user_activity (user_id, last_visit_date, total_visit_days, rewarded_cycle) VALUES (?, ?, 1, 0)'
    ).run(
      userId,
      today
    );
    return;
  }

  const last = activityRow.last_visit_date;
  if (!last) {
    db.prepare('UPDATE user_activity SET last_visit_date = ?, total_visit_days = MAX(COALESCE(total_visit_days, 0), 1) WHERE user_id = ?').run(
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

  db.prepare(
    'UPDATE user_activity SET last_visit_date = ?, total_visit_days = COALESCE(total_visit_days, 1) + 1 WHERE user_id = ?'
  ).run(today, userId);
}

function getRewardState(userId) {
  const activity = db
    .prepare('SELECT total_visit_days, rewarded_cycle FROM user_activity WHERE user_id = ?')
    .get(userId);

  const totalVisitDays = activity && activity.total_visit_days ? Number(activity.total_visit_days) : 1;
  const rewardedCycle = activity && activity.rewarded_cycle ? Number(activity.rewarded_cycle) : 0;
  const currentCycle = Math.floor(totalVisitDays / 7);
  const tester = isTester(userId);

  return {
    totalVisitDays,
    rewardedCycle,
    currentCycle,
    canSpinRoulette: tester ? true : currentCycle > rewardedCycle,
    nextRouletteAt: Math.ceil(totalVisitDays / 7) * 7 || 7
  };
}

function toUserPayload(user) {
  const rewardState = getRewardState(user.id);
  return {
    id: user.id,
    name: user.name,
    department: user.department || '미지정',
    loginDays: rewardState.totalVisitDays,
    canSpinRoulette: rewardState.canSpinRoulette,
    nextRouletteAt: rewardState.nextRouletteAt
  };
}

function mapPostRow(row) {
  if (!row) return null;
  return {
    id: row.id,
    userId: row.userId,
    title: row.title,
    content: row.content,
    imageData: row.imageData,
    category: row.category || '출석인사',
    created_at: row.created_at,
    userName: row.userName,
    likeCount: Number(row.likeCount) || 0,
    commentCount: Number(row.commentCount) || 0,
    likedByMe: !!row.likedByMe
  };
}

function getPostSummaries(viewerUserId) {
  const viewer = viewerUserId || null;
  const rows = db.prepare(`
    SELECT p.id,
           p.user_id AS userId,
           p.title,
           p.content,
           p.image_data AS imageData,
           p.category,
           p.created_at,
           u.name AS userName,
           (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) AS likeCount,
           (SELECT COUNT(*) FROM post_comments pc WHERE pc.post_id = p.id) AS commentCount,
           CASE
             WHEN ? IS NOT NULL AND EXISTS (
               SELECT 1 FROM post_likes pl2 WHERE pl2.post_id = p.id AND pl2.user_id = ?
             ) THEN 1
             ELSE 0
           END AS likedByMe
    FROM posts p
    JOIN users u ON p.user_id = u.id
    ORDER BY p.id DESC
    LIMIT 50
  `).all(viewer, viewer);

  return rows.map(mapPostRow);
}

function getPostDetail(postId, viewerUserId) {
  const viewer = viewerUserId || null;
  const row = db.prepare(`
    SELECT p.id,
           p.user_id AS userId,
           p.title,
           p.content,
           p.image_data AS imageData,
           p.category,
           p.created_at,
           u.name AS userName,
           (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) AS likeCount,
           (SELECT COUNT(*) FROM post_comments pc WHERE pc.post_id = p.id) AS commentCount,
           CASE
             WHEN ? IS NOT NULL AND EXISTS (
               SELECT 1 FROM post_likes pl2 WHERE pl2.post_id = p.id AND pl2.user_id = ?
             ) THEN 1
             ELSE 0
           END AS likedByMe
    FROM posts p
    JOIN users u ON p.user_id = u.id
    WHERE p.id = ?
  `).get(viewer, viewer, postId);

  if (!row) return null;

  const comments = db.prepare(`
    SELECT c.id,
           c.post_id AS postId,
           c.user_id AS userId,
           c.content,
           c.created_at,
           u.name AS userName
    FROM post_comments c
    JOIN users u ON c.user_id = u.id
    WHERE c.post_id = ?
    ORDER BY c.id ASC
  `).all(postId);

  return {
    post: mapPostRow(row),
    comments
  };
}

// 세션 (파일 저장 → 재접속·서버 재시작 후에도 유지)
app.use(session({
  store: new FileStore({ path: path.join(__dirname, 'sessions') }),
  secret: 'goguma-secret-key-change-in-production',
  resave: false,
  saveUninitialized: false,
  cookie: { maxAge: 7 * 24 * 60 * 60 * 1000 }
}));

app.use(applyBasicSecurityHeaders);
app.use(express.json({ limit: '8mb' }));
app.use(express.urlencoded({ extended: true, limit: '8mb' }));
app.use((err, req, res, next) => {
  if (err && err.type === 'entity.too.large') {
    return res.status(413).json({ error: '이미지 용량이 너무 큽니다. 더 작은 이미지를 사용해 주세요.' });
  }
  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    return res.status(400).json({ error: '요청 형식이 올바르지 않습니다.' });
  }
  return next(err);
});
app.use(express.static(__dirname));

const limitStart = createRateLimiter({
  keyPrefix: 'start',
  windowMs: 60 * 1000,
  max: 8,
  message: '시작 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
});
const limitGrow = createRateLimiter({
  keyPrefix: 'grow',
  windowMs: 60 * 1000,
  max: 90,
  message: '성장 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
});
const limitWrite = createRateLimiter({
  keyPrefix: 'write',
  windowMs: 60 * 1000,
  max: 40,
  message: '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
});
const limitCommunityWrite = createRateLimiter({
  keyPrefix: 'community-write',
  windowMs: 60 * 1000,
  max: 20,
  message: '커뮤니티 작성 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
});
const limitCommunityReact = createRateLimiter({
  keyPrefix: 'community-react',
  windowMs: 60 * 1000,
  max: 45,
  message: '커뮤니티 반응 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
});

// 루트(/) 접속 시 앱 페이지로 이동
app.get('/', (req, res) => {
  res.redirect('/goguma-app.html');
});

// 현재 유저 + 고구마 목록
app.get('/api/me', (req, res) => {
  if (!req.session.userId) {
    return res.json({ user: null, gogumas: [] });
  }
  const user = db.prepare('SELECT id, name, department FROM users WHERE id = ?').get(req.session.userId);
  if (!user) {
    req.session.userId = null;
    return res.json({ user: null, gogumas: [] });
  }

  applyInactivityPenalty(user.id);

  const gogumas = getUserGogumas(user.id);
  res.json({ user: toUserPayload(user), gogumas, missions: getMissionState(user.id) });
});

// 시작(로그인): 이름 입력 → 유저 생성/조회, 세션 설정
app.post('/api/start', limitStart, (req, res) => {
  const userNameResult = validateDisplayName(req.body.userName, INPUT_LIMITS.userName);
  if (!userNameResult.ok) {
    return res.status(400).json({ error: userNameResult.error === '입력값을 확인해 주세요.' ? '이름을 입력해 주세요.' : userNameResult.error });
  }
  const departmentRaw = typeof req.body.department === 'string' ? req.body.department.trim() : '';
  if (!ALLOWED_DEPARTMENTS.includes(departmentRaw)) {
    return res.status(400).json({ error: '부서를 선택해 주세요.' });
  }
  const userName = userNameResult.value;
  let user = db.prepare('SELECT id, name, department FROM users WHERE name = ?').get(userName);
  if (!user) {
    const stmt = db.prepare('INSERT INTO users (name, department) VALUES (?, ?)');
    stmt.run(userName, departmentRaw);
    user = db.prepare('SELECT id, name, department FROM users WHERE name = ?').get(userName);
  } else if (user.department !== departmentRaw) {
    db.prepare('UPDATE users SET department = ? WHERE id = ?').run(departmentRaw, user.id);
    user = db.prepare('SELECT id, name, department FROM users WHERE id = ?').get(user.id);
  }
  req.session.userId = user.id;

  applyInactivityPenalty(user.id);

  const gogumas = getUserGogumas(user.id);
  res.json({ user: toUserPayload(user), gogumas, missions: getMissionState(user.id) });
});

// 고구마 추가
app.post('/api/goguma/add', limitWrite, (req, res) => {
  if (!req.session.userId) return res.status(401).json({ error: '로그인이 필요합니다.' });
  const nameResult = validateDisplayName(req.body.name, INPUT_LIMITS.gogumaName);
  const relationResult = validateDisplayName(req.body.relation, INPUT_LIMITS.relation);
  const ageNum = parseInt(req.body.age, 10);
  if (!nameResult.ok) {
    return res.status(400).json({ error: nameResult.error === '입력값을 확인해 주세요.' ? '고구마 이름을 입력해 주세요.' : nameResult.error });
  }
  if (!relationResult.ok) {
    return res.status(400).json({ error: relationResult.error === '입력값을 확인해 주세요.' ? '관계를 입력해 주세요.' : relationResult.error });
  }
  if (!Number.isInteger(ageNum) || ageNum < 1 || ageNum > 120) {
    return res.status(400).json({ error: '나이는 1~120 사이 숫자로 입력해 주세요.' });
  }
  const name = nameResult.value;
  const relation = relationResult.value;
  const age = ageNum;
  const count = db.prepare('SELECT COUNT(*) as c FROM gogumas WHERE user_id = ?').get(req.session.userId).c;
  if (count >= 10) return res.status(400).json({ error: '최대 10명까지 가능합니다.' });
  const stmt = db.prepare('INSERT INTO gogumas (user_id, name, relation, age, hp) VALUES (?, ?, ?, ?, 10)');
  const info = stmt.run(req.session.userId, name, relation, age);
  const row = db.prepare('SELECT id, name, relation, age, hp, stage2_action_lock, stage3_action_lock FROM gogumas WHERE id = ?').get(info.lastInsertRowid);
  res.json({ goguma: toGogumaPayload(row, req.session.userId) });
});

// 고구마 온도 증가 (행동별로 하루 1회 제한)
app.post('/api/goguma/grow', limitGrow, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const id = parseInt(req.body.id, 10);
  const actionType = (req.body.actionType || '').trim();

  if (!id || !ACTION_VALUES[actionType]) {
    return res.status(400).json({ error: '잘못된 요청입니다.' });
  }

  const row = db
    .prepare('SELECT id, hp, stage2_action_lock, stage3_action_lock FROM gogumas WHERE id = ? AND user_id = ?')
    .get(id, req.session.userId);

  if (!row) {
    return res.status(404).json({ error: '고구마를 찾을 수 없습니다.' });
  }

  // 테스터(권진호)는 하루 제한 없이 가능
  if (isTester(req.session.userId)) {
    const valTester = ACTION_VALUES[actionType];
    const totalGainTester = valTester;
    const newHpTester = Math.min(100, row.hp + totalGainTester);
    // 테스터는 1일 제한 없이 사용 가능하되, 전직 판정을 위해 행동 점수 로그는 적재한다.
    const actionDate = getTodayDate() + 'T' + Date.now();
    db.prepare(
      'INSERT INTO actions (user_id, goguma_id, action_type, action_date) VALUES (?, ?, ?, ?)'
    ).run(req.session.userId, id, actionType, actionDate);
    const scores = getGogumaActionScores(req.session.userId, id);
    const dominantAction = getDominantAction(scores);
    const prevStage = getStageFromHp(row.hp);
    const nextStage = getStageFromHp(newHpTester);
    const nextStage2Lock = row.stage2_action_lock || ((prevStage < 2 && nextStage >= 2) ? (dominantAction || actionType) : null);
    const nextStage3Lock = row.stage3_action_lock || ((prevStage < 3 && nextStage >= 3) ? (dominantAction || actionType) : null);
    db.prepare('UPDATE gogumas SET hp = ?, stage2_action_lock = COALESCE(?, stage2_action_lock), stage3_action_lock = COALESCE(?, stage3_action_lock) WHERE id = ?')
      .run(newHpTester, nextStage2Lock, nextStage3Lock, id);
    return res.json({
      id,
      hp: newHpTester,
      dominantAction: dominantAction,
      stage2ActionLock: nextStage2Lock || null,
      stage3ActionLock: nextStage3Lock || null,
      actionScores: scores,
      todayActions: getTodayActionFlags(req.session.userId, id),
      baseGain: valTester,
      totalGain: totalGainTester,
      missions: getMissionState(req.session.userId)
    });
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
  const totalGain = val;
  const newHp = Math.min(100, row.hp + totalGain);

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

  const scores = getGogumaActionScores(req.session.userId, id);
  const dominantAction = getDominantAction(scores);
  const prevStage = getStageFromHp(row.hp);
  const nextStage = getStageFromHp(newHp);
  const nextStage2Lock = row.stage2_action_lock || ((prevStage < 2 && nextStage >= 2) ? (dominantAction || actionType) : null);
  const nextStage3Lock = row.stage3_action_lock || ((prevStage < 3 && nextStage >= 3) ? (dominantAction || actionType) : null);
  db.prepare('UPDATE gogumas SET hp = ?, stage2_action_lock = COALESCE(?, stage2_action_lock), stage3_action_lock = COALESCE(?, stage3_action_lock) WHERE id = ?')
    .run(newHp, nextStage2Lock, nextStage3Lock, id);
  res.json({
    id,
    hp: newHp,
    dominantAction: dominantAction,
    stage2ActionLock: nextStage2Lock || null,
    stage3ActionLock: nextStage3Lock || null,
    actionScores: scores,
    todayActions: getTodayActionFlags(req.session.userId, id),
    baseGain: val,
    totalGain,
    missions: getMissionState(req.session.userId)
  });
});

app.get('/api/missions', (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }
  res.json(getMissionState(req.session.userId));
});

app.post('/api/missions/:key/claim', limitWrite, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const key = String(req.params.key || '');
  const state = getMissionState(req.session.userId);

  let mission = null;
  let rewardHp = 0;
  if (key === state.daily.key) {
    mission = state.daily;
    rewardHp = Number(state.daily.rewardHp) || MISSION_REWARDS.daily;
  } else if (key === state.weekly.key) {
    mission = state.weekly;
    rewardHp = Number(state.weekly.rewardHp) || 0;
  } else {
    return res.status(400).json({ error: '잘못된 미션입니다.' });
  }

  if (!mission.completed) {
    return res.status(400).json({ error: '아직 미션을 완료하지 않았습니다.' });
  }
  if (mission.claimed) {
    return res.status(400).json({ error: '이미 보상을 받았습니다.' });
  }

  const claimTx = db.transaction((userId, missionKey, periodKey, hpBonus) => {
    db.prepare(`
      INSERT INTO mission_rewards (user_id, mission_key, period_key, reward_hp)
      VALUES (?, ?, ?, ?)
    `).run(userId, missionKey, periodKey, hpBonus);

    if (hpBonus > 0) {
      db.prepare('UPDATE gogumas SET hp = MIN(100, hp + ?) WHERE user_id = ?').run(hpBonus, userId);
    }
  });

  try {
    claimTx(req.session.userId, mission.key, mission.periodKey, rewardHp);
  } catch (e) {
    return res.status(400).json({ error: '이미 보상을 받았습니다.' });
  }

  const user = db.prepare('SELECT id, name, department FROM users WHERE id = ?').get(req.session.userId);
  const gogumas = getUserGogumas(req.session.userId);
  res.json({
    ok: true,
    missionKey: mission.key,
    rewardHp,
    user: user ? toUserPayload(user) : null,
    gogumas,
    missions: getMissionState(req.session.userId)
  });
});

// 고구마 삭제
app.post('/api/goguma/remove', limitWrite, (req, res) => {
  if (!req.session.userId) return res.status(401).json({ error: '로그인이 필요합니다.' });
  const id = parseInt(req.body.id, 10);
  if (!id) return res.status(400).json({ error: '잘못된 요청입니다.' });

  const removeTx = db.transaction((userId, gogumaId) => {
    const owned = db
      .prepare('SELECT id FROM gogumas WHERE id = ? AND user_id = ?')
      .get(gogumaId, userId);
    if (!owned) return false;

    db.prepare('DELETE FROM actions WHERE user_id = ? AND goguma_id = ?').run(userId, gogumaId);
    const removed = db.prepare('DELETE FROM gogumas WHERE id = ? AND user_id = ?').run(gogumaId, userId);
    return removed.changes > 0;
  });

  try {
    const ok = removeTx(req.session.userId, id);
    if (!ok) return res.status(404).json({ error: '고구마를 찾을 수 없습니다.' });
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: '삭제 중 오류가 발생했습니다.' });
  }
});

app.get('/api/goguma/:id/history', (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const id = parseInt(req.params.id, 10);
  if (!id) {
    return res.status(400).json({ error: '잘못된 요청입니다.' });
  }

  const owned = db
    .prepare('SELECT id FROM gogumas WHERE id = ? AND user_id = ?')
    .get(id, req.session.userId);
  if (!owned) {
    return res.status(404).json({ error: '고구마를 찾을 수 없습니다.' });
  }

  res.json(getGogumaHistory(req.session.userId, id));
});

// 로그아웃
app.post('/api/logout', (req, res) => {
  req.session.userId = null;
  req.session.destroy(() => {});
  res.json({ ok: true });
});

app.post('/api/reward/spin', limitWrite, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const user = db.prepare('SELECT id, name, department FROM users WHERE id = ?').get(req.session.userId);
  if (!user) {
    req.session.userId = null;
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  applyInactivityPenalty(user.id);

  const rewardState = getRewardState(user.id);
  if (!rewardState.canSpinRoulette) {
    const remain = Math.max(0, rewardState.nextRouletteAt - rewardState.totalVisitDays);
    return res.status(400).json({
      error: remain > 0
        ? `누적 접속 ${rewardState.nextRouletteAt}일에 룰렛을 돌릴 수 있습니다. (${remain}일 남음)`
        : '아직 룰렛을 돌릴 수 없습니다.'
    });
  }

  const segments = [
    { key: 'miss', label: '마이쮸', hpBonus: 0, segmentIndex: 0 },
    { key: 'miss', label: '마이쮸', hpBonus: 0, segmentIndex: 1 },
    { key: 'chupachups', label: '츄팝츄스', hpBonus: 3, segmentIndex: 2 },
    { key: 'miss', label: '마이쮸', hpBonus: 0, segmentIndex: 3 },
    { key: 'miss', label: '마이쮸', hpBonus: 0, segmentIndex: 4 },
    { key: 'miyjju', label: '마이쮸', hpBonus: 2, segmentIndex: 5 },
    { key: 'chupachups', label: '츄팝츄스', hpBonus: 3, segmentIndex: 6 },
    { key: 'miyjju', label: '마이쮸', hpBonus: 2, segmentIndex: 7 }
  ];

  const reward = segments[Math.floor(Math.random() * segments.length)];

  if (reward.hpBonus > 0) {
    db.prepare('UPDATE gogumas SET hp = MIN(100, hp + ?) WHERE user_id = ?').run(reward.hpBonus, user.id);
  }

  db.prepare('UPDATE user_activity SET rewarded_cycle = ? WHERE user_id = ?').run(
    rewardState.currentCycle,
    user.id
  );

  const gogumas = db
    .prepare('SELECT id, name, hp FROM gogumas WHERE user_id = ? ORDER BY id')
    .all(user.id);

  res.json({
    reward,
    user: toUserPayload(user),
    gogumas
  });
});

// 랭킹 조회
app.get('/api/ranking', (req, res) => {
  const rows = db.prepare(`
    SELECT users.department AS department,
           ROUND(AVG(gogumas.hp), 1) AS avgHp,
           COUNT(gogumas.id) AS gogumaCount,
           COUNT(DISTINCT users.id) AS userCount
    FROM gogumas
    JOIN users ON gogumas.user_id = users.id
    WHERE users.department IN ('언약부', '밀알부', '이레부')
    GROUP BY users.department
    ORDER BY avgHp DESC, users.department ASC
  `).all();
  res.json(rows);
});

// 게시글 목록
app.get('/api/posts', (req, res) => {
  res.json(getPostSummaries(req.session.userId));
});

app.get('/api/posts/:id', (req, res) => {
  const postId = parseInt(req.params.id, 10);
  if (!postId) {
    return res.status(400).json({ error: '잘못된 게시글입니다.' });
  }

  const detail = getPostDetail(postId, req.session.userId);
  if (!detail) {
    return res.status(404).json({ error: '게시글을 찾을 수 없습니다.' });
  }

  res.json(detail);
});

// 게시글 추가
app.post('/api/posts/add', limitCommunityWrite, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const title = sanitizeTextInput(req.body.title);
  const content = sanitizeTextInput(req.body.content);
  const imageData = typeof req.body.imageData === 'string' ? req.body.imageData.trim() : '';
  const category = sanitizeTextInput(req.body.category) || '출석인사';

  if (!title) {
    return res.status(400).json({ error: '제목을 입력해 주세요.' });
  }
  if (!content) {
    return res.status(400).json({ error: '내용을 입력해 주세요.' });
  }
  if (title.length > INPUT_LIMITS.postTitle) {
    return res.status(400).json({ error: '제목은 100자 이내로 작성해 주세요.' });
  }
  if (content.length > INPUT_LIMITS.postContent) {
    return res.status(400).json({ error: '내용은 1000자 이내로 작성해 주세요.' });
  }
  if (!COMMUNITY_CATEGORIES.includes(category)) {
    return res.status(400).json({ error: '올바른 카테고리를 선택해 주세요.' });
  }
  if (imageData && !/^data:image\/[a-zA-Z0-9.+-]+;base64,/.test(imageData)) {
    return res.status(400).json({ error: '이미지 데이터 형식이 올바르지 않습니다.' });
  }
  if (imageData && imageData.length > 6_000_000) {
    return res.status(400).json({ error: '이미지 용량이 너무 큽니다. 더 작은 이미지를 사용해 주세요.' });
  }

  const insert = db.prepare(`
    INSERT INTO posts (user_id, title, content, image_data, category)
    VALUES (?, ?, ?, ?, ?)
  `);
  const info = insert.run(req.session.userId, title, content, imageData || null, category);

  res.json(getPostDetail(info.lastInsertRowid, req.session.userId));
});

app.post('/api/posts/:id/like', limitCommunityReact, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const postId = parseInt(req.params.id, 10);
  if (!postId) {
    return res.status(400).json({ error: '잘못된 게시글입니다.' });
  }

  const postExists = db.prepare('SELECT id FROM posts WHERE id = ?').get(postId);
  if (!postExists) {
    return res.status(404).json({ error: '게시글을 찾을 수 없습니다.' });
  }

  const existing = db.prepare('SELECT 1 FROM post_likes WHERE post_id = ? AND user_id = ?').get(postId, req.session.userId);
  if (existing) {
    db.prepare('DELETE FROM post_likes WHERE post_id = ? AND user_id = ?').run(postId, req.session.userId);
  } else {
    db.prepare('INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)').run(postId, req.session.userId);
  }

  const detail = getPostDetail(postId, req.session.userId);
  res.json({
    likedByMe: detail.post.likedByMe,
    likeCount: detail.post.likeCount,
    commentCount: detail.post.commentCount
  });
});

app.post('/api/posts/:id/comments', limitCommunityWrite, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const postId = parseInt(req.params.id, 10);
  if (!postId) {
    return res.status(400).json({ error: '잘못된 게시글입니다.' });
  }

  const postExists = db.prepare('SELECT id FROM posts WHERE id = ?').get(postId);
  if (!postExists) {
    return res.status(404).json({ error: '게시글을 찾을 수 없습니다.' });
  }

  const content = sanitizeTextInput(req.body.content);
  if (!content) {
    return res.status(400).json({ error: '댓글을 입력해 주세요.' });
  }
  if (content.length > INPUT_LIMITS.comment) {
    return res.status(400).json({ error: '댓글은 300자 이내로 작성해 주세요.' });
  }

  const info = db.prepare(`
    INSERT INTO post_comments (post_id, user_id, content)
    VALUES (?, ?, ?)
  `).run(postId, req.session.userId, content);

  const comment = db.prepare(`
    SELECT c.id,
           c.post_id AS postId,
           c.user_id AS userId,
           c.content,
           c.created_at,
           u.name AS userName
    FROM post_comments c
    JOIN users u ON c.user_id = u.id
    WHERE c.id = ?
  `).get(info.lastInsertRowid);

  const countRow = db.prepare('SELECT COUNT(*) AS count FROM post_comments WHERE post_id = ?').get(postId);

  res.json({
    comment,
    commentCount: Number(countRow.count) || 0
  });
});

app.post('/api/comments/:id/update', limitCommunityWrite, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const commentId = parseInt(req.params.id, 10);
  if (!commentId) {
    return res.status(400).json({ error: '잘못된 댓글입니다.' });
  }

  const ownedComment = db.prepare('SELECT id FROM post_comments WHERE id = ? AND user_id = ?').get(commentId, req.session.userId);
  if (!ownedComment) {
    return res.status(404).json({ error: '댓글을 찾을 수 없거나 권한이 없습니다.' });
  }

  const content = sanitizeTextInput(req.body.content);
  if (!content) {
    return res.status(400).json({ error: '댓글을 입력해 주세요.' });
  }
  if (content.length > INPUT_LIMITS.comment) {
    return res.status(400).json({ error: '댓글은 300자 이내로 작성해 주세요.' });
  }

  db.prepare('UPDATE post_comments SET content = ? WHERE id = ? AND user_id = ?').run(content, commentId, req.session.userId);

  const comment = db.prepare(`
    SELECT c.id,
           c.post_id AS postId,
           c.user_id AS userId,
           c.content,
           c.created_at,
           u.name AS userName
    FROM post_comments c
    JOIN users u ON c.user_id = u.id
    WHERE c.id = ?
  `).get(commentId);

  res.json({ comment });
});

app.post('/api/comments/:id/delete', limitCommunityWrite, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const commentId = parseInt(req.params.id, 10);
  if (!commentId) {
    return res.status(400).json({ error: '잘못된 댓글입니다.' });
  }

  const ownedComment = db.prepare('SELECT id, post_id AS postId FROM post_comments WHERE id = ? AND user_id = ?').get(commentId, req.session.userId);
  if (!ownedComment) {
    return res.status(404).json({ error: '댓글을 찾을 수 없거나 권한이 없습니다.' });
  }

  db.prepare('DELETE FROM post_comments WHERE id = ? AND user_id = ?').run(commentId, req.session.userId);

  const countRow = db.prepare('SELECT COUNT(*) AS count FROM post_comments WHERE post_id = ?').get(ownedComment.postId);
  res.json({
    ok: true,
    postId: ownedComment.postId,
    commentCount: Number(countRow.count) || 0
  });
});

// 게시글 삭제
app.post('/api/posts/delete', limitCommunityWrite, (req, res) => {
  if (!req.session.userId) {
    return res.status(401).json({ error: '로그인이 필요합니다.' });
  }

  const id = parseInt(req.body.id, 10);
  if (!id) {
    return res.status(400).json({ error: '잘못된 요청입니다.' });
  }

  const ownedPost = db.prepare('SELECT id FROM posts WHERE id = ? AND user_id = ?').get(id, req.session.userId);
  if (!ownedPost) {
    return res.status(404).json({ error: '게시글을 찾을 수 없거나 권한이 없습니다.' });
  }

  db.prepare('DELETE FROM post_likes WHERE post_id = ?').run(id);
  db.prepare('DELETE FROM post_comments WHERE post_id = ?').run(id);
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
