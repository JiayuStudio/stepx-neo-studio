(function () {
  'use strict';

  var SIZE = 16;
  var STEP_MS = 300;
  var STORAGE_KEY = 'neo-snake-best';

  var boardEl = document.getElementById('board');
  var overlayEl = document.getElementById('overlay');
  var overlayTextEl = document.getElementById('overlay-text');
  var statusEl = document.getElementById('status');
  var scoreEl = document.getElementById('score');
  var bestEl = document.getElementById('best');
  var btnStart = document.getElementById('btn-start');
  var btnPause = document.getElementById('btn-pause');
  var btnRestart = document.getElementById('btn-restart');
  var dirButtons = document.querySelectorAll('.dir');

  var cells = [];

  var state = 'idle'; // idle | running | paused | over | won
  var snake = [];
  var direction = { x: 1, y: 0 };
  var pendingDirections = [];
  var food = null;
  var score = 0;
  var best = 0;
  var timer = null;

  function safeGetBest() {
    try {
      var raw = window.localStorage.getItem(STORAGE_KEY);
      var n = parseInt(raw, 10);
      return isFinite(n) && n > 0 ? n : 0;
    } catch (e) {
      return 0;
    }
  }

  function safeSetBest(value) {
    try {
      window.localStorage.setItem(STORAGE_KEY, String(value));
    } catch (e) {
      /* 存储不可用时静默回退 */
    }
  }

  function buildBoard() {
    boardEl.innerHTML = '';
    cells = [];
    for (var y = 0; y < SIZE; y++) {
      cells[y] = [];
      for (var x = 0; x < SIZE; x++) {
        var d = document.createElement('div');
        d.className = 'cell';
        boardEl.appendChild(d);
        cells[y][x] = d;
      }
    }
  }

  function keyOf(p) {
    return p.x + ',' + p.y;
  }

  function render() {
    for (var y = 0; y < SIZE; y++) {
      for (var x = 0; x < SIZE; x++) {
        cells[y][x].className = 'cell';
      }
    }
    if (food) {
      cells[food.y][food.x].className = 'cell food';
    }
    for (var i = 0; i < snake.length; i++) {
      var seg = snake[i];
      var cls = i === 0 ? 'cell snake head' : 'cell snake';
      cells[seg.y][seg.x].className = cls;
    }
  }

  function setStatus(text) {
    statusEl.textContent = text;
  }

  function showOverlay(text) {
    overlayTextEl.textContent = text;
    overlayEl.hidden = false;
  }

  function hideOverlay() {
    overlayEl.hidden = true;
  }

  function updateScoreUI() {
    scoreEl.textContent = String(score);
    bestEl.textContent = String(best);
  }

  function updateButtons() {
    btnStart.disabled = !(state === 'idle' || state === 'over' || state === 'won');
    btnStart.textContent = (state === 'over' || state === 'won') ? '再来一局' : '开始游戏';
    btnPause.disabled = !(state === 'running' || state === 'paused');
    btnPause.textContent = state === 'paused' ? '继续' : '暂停';
    btnRestart.disabled = state === 'idle';
  }

  function isOpposite(a, b) {
    return a.x + b.x === 0 && a.y + b.y === 0;
  }

  function baseDirection() {
    if (pendingDirections.length > 0) {
      return pendingDirections[pendingDirections.length - 1];
    }
    return direction;
  }

  function queueDirection(next) {
    if (state !== 'running' && state !== 'paused') {
      return;
    }
    var base = baseDirection();
    if (isOpposite(base, next)) {
      return;
    }
    if (base.x === next.x && base.y === next.y) {
      return;
    }
    if (pendingDirections.length >= 2) {
      return;
    }
    pendingDirections.push(next);
  }

  function freeCells() {
    var occupied = {};
    for (var i = 0; i < snake.length; i++) {
      occupied[keyOf(snake[i])] = true;
    }
    if (food) {
      occupied[keyOf(food)] = true;
    }
    var result = [];
    for (var y = 0; y < SIZE; y++) {
      for (var x = 0; x < SIZE; x++) {
        if (!occupied[x + ',' + y]) {
          result.push({ x: x, y: y });
        }
      }
    }
    return result;
  }

  function placeRandomFood() {
    var free = freeCells();
    if (free.length === 0) {
      food = null;
      return false;
    }
    food = free[Math.floor(Math.random() * free.length)];
    return true;
  }

  function resetGame() {
    stopTimer();
    state = 'idle';
    snake = [
      { x: 6, y: 8 },
      { x: 5, y: 8 },
      { x: 4, y: 8 }
    ];
    direction = { x: 1, y: 0 };
    pendingDirections = [];
    score = 0;
    var head = snake[0];
    food = { x: head.x + 2, y: head.y };
    updateScoreUI();
    render();
    showOverlay('点“开始游戏”出发');
    setStatus('准备开始：点“开始游戏”。' + '吃果实得分，撞墙或撞到自己会结束。');
    updateButtons();
  }

  function startGame() {
    if (state === 'running') {
      return;
    }
    if (state === 'over' || state === 'won' || state === 'idle') {
      resetGame();
    }
    state = 'running';
    hideOverlay();
    setStatus('游戏中：用方向键或滑动改变方向。');
    updateButtons();
    startTimer();
  }

  function pauseGame() {
    if (state !== 'running') {
      return;
    }
    state = 'paused';
    stopTimer();
    showOverlay('已暂停');
    setStatus('已暂停：点“继续”回到游戏。');
    updateButtons();
  }

  function resumeGame() {
    if (state !== 'paused') {
      return;
    }
    state = 'running';
    hideOverlay();
    setStatus('游戏中：用方向键或滑动改变方向。');
    updateButtons();
    startTimer();
  }

  function togglePause() {
    if (state === 'running') {
      pauseGame();
    } else if (state === 'paused') {
      resumeGame();
    }
 }

  function endGame(reason) {
    stopTimer();
    state = 'over';
    if (score > best) {
      best = score;
      safeSetBest(best);
    }
    updateScoreUI();
    showOverlay('游戏结束');
    setStatus('游戏结束：' + reason + '。得分 ' + score + '，最高分 ' + best + '。点“再来一局”。');
    updateButtons();
    render();
  }

  function winGame() {
    stopTimer();
    state = 'won';
    if (score > best) {
      best = score;
      safeSetBest(best);
    }
    updateScoreUI();
    showOverlay('你填满了棋盘！');
    setStatus('胜利：棋盘已满，得分 ' + score + '。点“再来一局”。');
    updateButtons();
    render();
  }

  function step() {
    if (pendingDirections.length > 0) {
      var next = pendingDirections.shift();
      if (!isOpposite(direction, next)) {
        direction = next;
      }
    }

    var head = snake[0];
    var nx = head.x + direction.x;
    var ny = head.y + direction.y;

    if (nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE) {
      endGame('撞到墙');
      return;
    }

    var willEat = food && nx === food.x && ny === food.y;
    var bodyLimit = willEat ? snake.length : snake.length - 1;
    for (var i = 0; i < bodyLimit; i++) {
      if (snake[i].x === nx && snake[i].y === ny) {
        endGame('撞到自己');
        return;
      }
    }

    snake.unshift({ x: nx, y: ny });

    if (willEat) {
      score += 1;
      if (score > best) {
        best = score;
        safeSetBest(best);
      }
      updateScoreUI();
      var placed = placeRandomFood();
      render();
      if (!placed) {
        winGame();
        return;
      }
      setStatus('很好吃！当前得分 ' + score + '。');
    } else {
      snake.pop();
      render();
    }
  }

  function startTimer() {
    stopTimer();
    timer = window.setInterval(function () {
      if (state === 'running') {
        step();
      }
    }, STEP_MS);
  }

  function stopTimer() {
    if (timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  }

  var directionMap = {
    up: { x: 0, y: -1 },
    down: { x: 0, y: 1 },
    left: { x: -1, y: 0 },
    right: { x: 1, y: 0 }
  };

  function handleDir(name) {
    var d = directionMap[name];
    if (d) {
      queueDirection(d);
    }
  }

  for (var i = 0; i < dirButtons.length; i++) {
    (function (btn) {
      btn.addEventListener('click', function () {
        handleDir(btn.getAttribute('data-dir'));
      });
    })(dirButtons[i]);
  }

  btnStart.addEventListener('click', function () {
    startGame();
  });

  btnPause.addEventListener('click', function () {
    togglePause();
  });

  btnRestart.addEventListener('click', function () {
    resetGame();
    startGame();
  });

  var keyMap = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right',
    w: 'up',
    s: 'down',
    a: 'left',
    d: 'right'
  };

  document.addEventListener('keydown', function (e) {
    var key = e.key;
    var name = keyMap[key] || keyMap[String(key).toLowerCase()];
    if (name) {
      e.preventDefault();
      handleDir(name);
      return;
    }
    if (key === ' ' || key === 'Spacebar') {
      e.preventDefault();
      if (state === 'idle' || state === 'over' || state === 'won') {
        startGame();
      } else {
        togglePause();
      }
    }
  });

  var touchStart = null;

  boardEl.addEventListener('touchstart', function (e) {
    if (e.touches.length !== 1) {
      return;
    }
    var t = e.touches[0];
    touchStart = { x: t.clientX, y: t.clientY };
  }, { passive: true });

  boardEl.addEventListener('touchmove', function (e) {
    if (e.touches.length !== 1) {
      return;
    }
    if (touchStart) {
      e.preventDefault();
    }
  }, { passive: false });

  boardEl.addEventListener('touchend', function (e) {
    if (!touchStart) {
      return;
    }
    var t = e.changedTouches && e.changedTouches[0];
    if (!t) {
      touchStart = null;
      return;
    }
    var dx = t.clientX - touchStart.x;
    var dy = t.clientY - touchStart.y;
    touchStart = null;
    var ax = Math.abs(dx);
    var ay = Math.abs(dy);
    var threshold = 18;
    if (ax < threshold && ay < threshold) {
      return;
    }
    if (ax > ay) {
      handleDir(dx > 0 ? 'right' : 'left');
    } else {
      handleDir(dy > 0 ? 'down' : 'up');
    }
  }, { passive: true });

  boardEl.addEventListener('touchcancel', function () {
    touchStart = null;
  }, { passive: true });

  window.addEventListener('blur', function () {
    if (state === 'running') {
      pauseGame();
    }
  });

  document.addEventListener('visibilitychange', function () {
    if (document.hidden && state === 'running') {
      pauseGame();
    }
  });

  best = safeGetBest();
  buildBoard();
  resetGame();
})();
