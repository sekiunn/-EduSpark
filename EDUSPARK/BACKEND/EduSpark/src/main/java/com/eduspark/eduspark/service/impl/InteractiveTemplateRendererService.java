package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.dto.interactive.InteractiveTemplatePlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class InteractiveTemplateRendererService {

    private static final String COMMON_STYLE = """
            :root {
              color-scheme: light;
              --bg: #f6f7fb;
              --panel: #ffffff;
              --panel-soft: #f2f5fb;
              --border: #d9e2f2;
              --text: #1f2937;
              --muted: #64748b;
              --primary: #2563eb;
              --primary-soft: #dbeafe;
              --success: #0f766e;
              --success-soft: #ecfdf5;
              --danger: #dc2626;
              --danger-soft: #fef2f2;
              --shadow: 0 14px 40px rgba(15, 23, 42, 0.08);
              --radius: 18px;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", sans-serif;
              color: var(--text);
              background:
                radial-gradient(circle at top left, #eff6ff 0, transparent 42%),
                linear-gradient(180deg, #f8fbff 0%, #f5f7fb 100%);
              min-height: 0;
            }
            .shell {
              max-width: 980px;
              margin: 0 auto;
              padding: 20px;
            }
            .page-header {
              display: flex;
              justify-content: space-between;
              align-items: flex-start;
              gap: 16px;
              margin-bottom: 16px;
            }
            .page-header h1 {
              margin: 0;
              font-size: clamp(24px, 4vw, 34px);
              line-height: 1.15;
            }
            .page-subtitle {
              margin: 8px 0 0;
              color: var(--muted);
              font-size: 14px;
            }
            .page-card {
              background: var(--panel);
              border: 1px solid var(--border);
              border-radius: var(--radius);
              box-shadow: var(--shadow);
              padding: 16px;
            }
            .chip {
              display: inline-flex;
              align-items: center;
              gap: 6px;
              padding: 8px 12px;
              border-radius: 999px;
              background: var(--primary-soft);
              color: var(--primary);
              font-size: 13px;
              font-weight: 700;
            }
            .row {
              display: flex;
              flex-wrap: wrap;
              gap: 12px;
            }
            .button,
            button {
              border: none;
              border-radius: 12px;
              padding: 11px 16px;
              font-size: 14px;
              font-weight: 700;
              cursor: pointer;
            }
            .button-primary {
              background: var(--primary);
              color: #fff;
            }
            .button-secondary {
              background: #e2e8f0;
              color: var(--text);
            }
            .button-ghost {
              background: transparent;
              border: 1px solid var(--border);
              color: var(--text);
            }
            .button-ghost.active {
              border-color: var(--primary);
              background: var(--primary-soft);
              color: var(--primary);
            }
            .muted {
              color: var(--muted);
            }
            .panel-title {
              margin: 0 0 10px;
              font-size: 15px;
              font-weight: 700;
            }
            .stats-grid {
              display: grid;
              grid-template-columns: repeat(4, minmax(0, 1fr));
              gap: 12px;
            }
            .stat-card {
              padding: 14px;
              border-radius: 14px;
              background: var(--panel-soft);
              border: 1px solid var(--border);
            }
            .stat-label {
              color: var(--muted);
              font-size: 13px;
            }
            .stat-value {
              margin-top: 6px;
              font-size: 20px;
              font-weight: 800;
            }
            .note-box {
              border-radius: 14px;
              padding: 12px 14px;
              background: var(--success-soft);
              color: var(--success);
              border: 1px solid #bae6d3;
              font-size: 14px;
              font-weight: 700;
            }
            .danger-box {
              background: var(--danger-soft);
              color: var(--danger);
              border: 1px solid #fecaca;
            }
            @media (max-width: 760px) {
              .shell { padding: 14px; }
              .page-header { flex-direction: column; }
              .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
            }
            """;

    private final ObjectMapper objectMapper;

    public InteractiveTemplateRendererService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String render(InteractiveTemplatePlan plan, InteractiveContext context) {
        if (plan == null || !hasText(plan.getTemplateId())) {
            throw new IllegalArgumentException("Interactive template plan is missing templateId");
        }
        return switch (plan.getTemplateId()) {
            case InteractiveTemplateCatalog.TEMPLATE_PHYSICS_COLLISION_V1 -> renderPhysicsCollision(plan, context);
            case InteractiveTemplateCatalog.TEMPLATE_PPT_DEMO_V1 -> renderPptDemo(plan, context);
            case InteractiveTemplateCatalog.TEMPLATE_FLOW_DEMO_V1 -> renderFlowDemo(plan, context);
            case InteractiveTemplateCatalog.TEMPLATE_ANIMATION_STEPPER_V1 -> renderAnimationStepper(plan, context);
            case InteractiveTemplateCatalog.TEMPLATE_PARAMETER_EXPLORER_V1 -> renderParameterExplorer(plan, context);
            case InteractiveTemplateCatalog.TEMPLATE_QUIZ_PRACTICE_V1 -> renderQuizPractice(plan, context);
            case InteractiveTemplateCatalog.TEMPLATE_DRAG_MATCH_V1 -> renderDragMatch(plan, context);
            case InteractiveTemplateCatalog.TEMPLATE_HOTSPOT_EXPLORE_V1 -> renderHotspotExplore(plan, context);
            default -> throw new IllegalArgumentException("Unsupported interactive template: " + plan.getTemplateId());
        };
    }

    private String renderPhysicsCollision(InteractiveTemplatePlan plan, InteractiveContext context) {
        Map<String, Object> spec = prepareSpec(plan, context, "调节质量与速度，观察系统总动量在碰撞前后如何保持不变。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell collision-shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="row">
                      <button class="button button-ghost mode-btn" data-mode="elastic">弹性碰撞</button>
                      <button class="button button-ghost mode-btn" data-mode="inelastic">完全非弹性</button>
                    </div>
                  </header>

                  <section class="page-card control-panel">
                    <div class="control-grid">
                      <label class="control-item">
                        <span>物体 A 质量</span>
                        <strong id="massALabel"></strong>
                        <input id="massA" type="range" min="1" max="5" step="0.5" />
                      </label>
                      <label class="control-item">
                        <span>物体 A 速度</span>
                        <strong id="velocityALabel"></strong>
                        <input id="velocityA" type="range" min="-6" max="6" step="0.5" />
                      </label>
                      <label class="control-item">
                        <span>物体 B 质量</span>
                        <strong id="massBLabel"></strong>
                        <input id="massB" type="range" min="1" max="5" step="0.5" />
                      </label>
                      <label class="control-item">
                        <span>物体 B 速度</span>
                        <strong id="velocityBLabel"></strong>
                        <input id="velocityB" type="range" min="-6" max="6" step="0.5" />
                      </label>
                    </div>
                    <div class="row actions">
                      <button class="button button-primary" id="startBtn">开始演示</button>
                      <button class="button button-secondary" id="resetBtn">重置</button>
                    </div>
                  </section>

                  <section class="page-card scene-card">
                    <canvas id="collisionCanvas" height="240"></canvas>
                    <div class="scene-hint" id="hintBox"></div>
                  </section>

                  <section class="stats-grid">
                    <article class="stat-card">
                      <div class="stat-label">碰撞前总动量</div>
                      <div class="stat-value" id="beforeMomentum"></div>
                    </article>
                    <article class="stat-card">
                      <div class="stat-label">当前总动量</div>
                      <div class="stat-value" id="currentMomentum"></div>
                    </article>
                    <article class="stat-card" id="energyBeforeCard">
                      <div class="stat-label">碰撞前总动能</div>
                      <div class="stat-value" id="beforeEnergy"></div>
                    </article>
                    <article class="stat-card" id="energyCurrentCard">
                      <div class="stat-label">当前总动能</div>
                      <div class="stat-value" id="currentEnergy"></div>
                    </article>
                  </section>

                  <section class="page-card">
                    <div class="note-box" id="conclusionBox"></div>
                    <div class="formula-box" id="formulaBox"></div>
                  </section>
                </div>
                """,
                """
                .control-panel { margin-bottom: 16px; }
                .control-grid {
                  display: grid;
                  grid-template-columns: repeat(4, minmax(0, 1fr));
                  gap: 12px;
                }
                .control-item {
                  display: flex;
                  flex-direction: column;
                  gap: 8px;
                  padding: 14px;
                  border-radius: 14px;
                  background: var(--panel-soft);
                  border: 1px solid var(--border);
                  font-size: 14px;
                }
                .control-item strong {
                  color: var(--primary);
                  font-size: 15px;
                }
                .control-item input { width: 100%; }
                .actions { margin-top: 12px; }
                .scene-card { margin: 16px 0; }
                #collisionCanvas {
                  width: 100%;
                  display: block;
                  border-radius: 16px;
                  background:
                    linear-gradient(180deg, rgba(37, 99, 235, 0.05), rgba(255, 255, 255, 0)),
                    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
                  border: 1px solid var(--border);
                }
                .scene-hint {
                  margin-top: 12px;
                  font-size: 14px;
                  color: var(--muted);
                }
                .formula-box {
                  margin-top: 12px;
                  padding: 12px 14px;
                  border-radius: 14px;
                  background: #f8fafc;
                  border: 1px dashed var(--border);
                  color: var(--muted);
                  font-size: 14px;
                }
                @media (max-width: 760px) {
                  .control-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                }
                @media (max-width: 520px) {
                  .control-grid { grid-template-columns: minmax(0, 1fr); }
                }
                """,
                """
                const titleEl = document.getElementById('titleText');
                const subtitleEl = document.getElementById('subtitleText');
                const modeButtons = Array.from(document.querySelectorAll('.mode-btn'));
                const controls = {
                  massA: document.getElementById('massA'),
                  velocityA: document.getElementById('velocityA'),
                  massB: document.getElementById('massB'),
                  velocityB: document.getElementById('velocityB')
                };
                const labels = {
                  massA: document.getElementById('massALabel'),
                  velocityA: document.getElementById('velocityALabel'),
                  massB: document.getElementById('massBLabel'),
                  velocityB: document.getElementById('velocityBLabel')
                };
                const startBtn = document.getElementById('startBtn');
                const resetBtn = document.getElementById('resetBtn');
                const hintBox = document.getElementById('hintBox');
                const conclusionBox = document.getElementById('conclusionBox');
                const formulaBox = document.getElementById('formulaBox');
                const energyBeforeCard = document.getElementById('energyBeforeCard');
                const energyCurrentCard = document.getElementById('energyCurrentCard');
                const canvas = document.getElementById('collisionCanvas');
                const ctx = canvas.getContext('2d');

                titleEl.textContent = spec.title || '动量守恒碰撞演示';
                subtitleEl.textContent = spec.subtitle || '调节质量与速度，观察系统总动量在碰撞前后如何保持不变。';

                const state = {
                  mode: spec.defaultCollisionType === 'inelastic' ? 'inelastic' : 'elastic',
                  running: false,
                  collided: false,
                  rafId: 0,
                  lastTs: 0,
                  before: null,
                  ballA: null,
                  ballB: null
                };

                function format(num, digits = 2) {
                  return Number(num || 0).toFixed(digits);
                }

                function momentum(m, v) {
                  return m * v;
                }

                function kinetic(m, v) {
                  return 0.5 * m * v * v;
                }

                function readConfig() {
                  return {
                    massA: parseFloat(controls.massA.value),
                    velocityA: parseFloat(controls.velocityA.value),
                    massB: parseFloat(controls.massB.value),
                    velocityB: parseFloat(controls.velocityB.value)
                  };
                }

                function syncLabels() {
                  labels.massA.textContent = format(controls.massA.value, 1) + ' kg';
                  labels.velocityA.textContent = format(controls.velocityA.value, 1) + ' m/s';
                  labels.massB.textContent = format(controls.massB.value, 1) + ' kg';
                  labels.velocityB.textContent = format(controls.velocityB.value, 1) + ' m/s';
                }

                function syncModeButtons() {
                  modeButtons.forEach((button) => {
                    button.classList.toggle('active', button.dataset.mode === state.mode);
                  });
                }

                function resizeCanvas() {
                  const dpr = window.devicePixelRatio || 1;
                  const width = Math.max(320, canvas.clientWidth || 320);
                  const height = 240;
                  canvas.width = width * dpr;
                  canvas.height = height * dpr;
                  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                }

                function getWidth() {
                  return canvas.width / (window.devicePixelRatio || 1);
                }

                function buildSnapshot(ballA, ballB) {
                  return {
                    totalMomentum: momentum(ballA.m, ballA.v) + momentum(ballB.m, ballB.v),
                    totalEnergy: kinetic(ballA.m, ballA.v) + kinetic(ballB.m, ballB.v)
                  };
                }

                function updateReadout() {
                  const current = buildSnapshot(state.ballA, state.ballB);
                  document.getElementById('beforeMomentum').textContent = format(state.before.totalMomentum) + ' kg·m/s';
                  document.getElementById('currentMomentum').textContent = format(current.totalMomentum) + ' kg·m/s';
                  document.getElementById('beforeEnergy').textContent = format(state.before.totalEnergy) + ' J';
                  document.getElementById('currentEnergy').textContent = format(current.totalEnergy) + ' J';

                  const deltaP = Math.abs(current.totalMomentum - state.before.totalMomentum);
                  const deltaE = Math.abs(current.totalEnergy - state.before.totalEnergy);
                  let summary = deltaP < 0.02
                    ? '系统总动量保持不变。'
                    : '系统总动量出现了较大的数值偏差。';
                  if (spec.showKineticEnergy !== false) {
                    summary += state.mode === 'elastic'
                      ? (deltaE < 0.02 ? ' 在弹性碰撞中，总动能也近似保持不变。' : ' 理论上弹性碰撞应保持总动能不变。')
                      : ' 完全非弹性碰撞中，总动能会转化为其他形式的能量。';
                  }
                  conclusionBox.textContent = summary;
                }

                function setHint(text, isDanger) {
                  hintBox.textContent = text;
                  hintBox.style.color = isDanger ? 'var(--danger)' : 'var(--muted)';
                }

                function updateButtons() {
                  startBtn.disabled = state.running;
                }

                function applyCollision() {
                  const m1 = state.ballA.m;
                  const m2 = state.ballB.m;
                  const u1 = state.ballA.v;
                  const u2 = state.ballB.v;

                  if (state.mode === 'elastic') {
                    state.ballA.v = ((m1 - m2) * u1 + 2 * m2 * u2) / (m1 + m2);
                    state.ballB.v = ((m2 - m1) * u2 + 2 * m1 * u1) / (m1 + m2);
                    setHint('已发生弹性碰撞。继续观察碰撞后两物体的速度变化。');
                  } else {
                    const v = (m1 * u1 + m2 * u2) / (m1 + m2);
                    state.ballA.v = v;
                    state.ballB.v = v;
                    setHint('已发生完全非弹性碰撞。两个物体随后保持共同速度前进。');
                  }
                  state.collided = true;
                }

                function resetScene(message) {
                  cancelAnimationFrame(state.rafId);
                  state.running = false;
                  state.collided = false;
                  state.lastTs = 0;
                  resizeCanvas();

                  const cfg = readConfig();
                  const width = getWidth();
                  state.ballA = { label: 'A', color: '#2563eb', m: cfg.massA, v: cfg.velocityA, x: width * 0.2, r: 24 };
                  state.ballB = { label: 'B', color: '#ef4444', m: cfg.massB, v: cfg.velocityB, x: width * 0.8, r: 24 };
                  if (state.ballB.x - state.ballA.x < 180) {
                    state.ballA.x = width * 0.16;
                    state.ballB.x = width * 0.84;
                  }
                  state.before = buildSnapshot(state.ballA, state.ballB);
                  energyBeforeCard.style.display = spec.showKineticEnergy === false ? 'none' : '';
                  energyCurrentCard.style.display = spec.showKineticEnergy === false ? 'none' : '';
                  formulaBox.style.display = spec.showFormula === false ? 'none' : '';
                  formulaBox.innerHTML = state.mode === 'elastic'
                    ? '弹性碰撞: p 前 = p 后，且总动能近似守恒。'
                    : '完全非弹性碰撞: p 前 = p 后，但碰撞后二者具有共同速度。';
                  updateButtons();
                  updateReadout();
                  setHint(message || '点击“开始演示”查看碰撞过程。');
                  render();
                }

                function drawVelocityArrow(x, y, velocity, color) {
                  const length = velocity * 16;
                  if (Math.abs(length) < 6) {
                    return;
                  }
                  ctx.save();
                  ctx.strokeStyle = color;
                  ctx.fillStyle = color;
                  ctx.lineWidth = 3;
                  ctx.beginPath();
                  ctx.moveTo(x, y);
                  ctx.lineTo(x + length, y);
                  ctx.stroke();
                  const direction = length >= 0 ? 1 : -1;
                  ctx.beginPath();
                  ctx.moveTo(x + length, y);
                  ctx.lineTo(x + length - 10 * direction, y - 6);
                  ctx.lineTo(x + length - 10 * direction, y + 6);
                  ctx.closePath();
                  ctx.fill();
                  ctx.restore();
                }

                function drawBall(ball, baselineY) {
                  ctx.save();
                  ctx.fillStyle = ball.color;
                  ctx.beginPath();
                  ctx.arc(ball.x, baselineY, ball.r, 0, Math.PI * 2);
                  ctx.fill();
                  ctx.lineWidth = 2;
                  ctx.strokeStyle = 'rgba(255,255,255,0.85)';
                  ctx.stroke();
                  ctx.fillStyle = '#ffffff';
                  ctx.font = 'bold 16px Microsoft YaHei';
                  ctx.textAlign = 'center';
                  ctx.textBaseline = 'middle';
                  ctx.fillText(ball.label, ball.x, baselineY);
                  ctx.restore();

                  ctx.save();
                  ctx.fillStyle = '#0f172a';
                  ctx.font = '13px Microsoft YaHei';
                  ctx.textAlign = 'center';
                  ctx.fillText('m=' + format(ball.m, 1) + 'kg', ball.x, baselineY + 44);
                  ctx.fillText('v=' + format(ball.v, 1) + 'm/s', ball.x, baselineY + 60);
                  ctx.restore();
                }

                function render() {
                  resizeCanvas();
                  const width = getWidth();
                  const height = 240;
                  ctx.clearRect(0, 0, width, height);
                  const baselineY = 118;

                  ctx.strokeStyle = '#cbd5e1';
                  ctx.lineWidth = 2;
                  ctx.beginPath();
                  ctx.moveTo(24, baselineY + 32);
                  ctx.lineTo(width - 24, baselineY + 32);
                  ctx.stroke();

                  drawBall(state.ballA, baselineY);
                  drawBall(state.ballB, baselineY);
                  drawVelocityArrow(state.ballA.x, baselineY - 42, state.ballA.v, '#2563eb');
                  drawVelocityArrow(state.ballB.x, baselineY - 42, state.ballB.v, '#ef4444');
                }

                function tick(timestamp) {
                  if (!state.running) {
                    return;
                  }
                  if (!state.lastTs) {
                    state.lastTs = timestamp;
                  }
                  const dt = Math.min((timestamp - state.lastTs) / 16.666, 2.2);
                  state.lastTs = timestamp;
                  const speedScale = 7.5;

                  state.ballA.x += state.ballA.v * speedScale * dt;
                  state.ballB.x += state.ballB.v * speedScale * dt;

                  if (!state.collided && Math.abs(state.ballB.x - state.ballA.x) <= state.ballA.r + state.ballB.r) {
                    const center = (state.ballA.x + state.ballB.x) / 2;
                    state.ballA.x = center - state.ballA.r;
                    state.ballB.x = center + state.ballB.r;
                    applyCollision();
                  }
                  if (state.collided && state.mode === 'inelastic') {
                    state.ballB.x = state.ballA.x + state.ballA.r + state.ballB.r;
                  }

                  render();
                  updateReadout();

                  const width = getWidth();
                  if (state.ballA.x < -80 || state.ballA.x > width + 80 || state.ballB.x < -80 || state.ballB.x > width + 80) {
                    state.running = false;
                    updateButtons();
                    setHint('演示结束。你可以修改参数后再次观察。');
                    return;
                  }
                  state.rafId = requestAnimationFrame(tick);
                }

                function startSimulation() {
                  if (state.running) {
                    return;
                  }
                  if (state.ballA.v <= state.ballB.v) {
                    setHint('当前速度设置下两物体不会相撞，请调整参数后再开始。', true);
                    return;
                  }
                  state.running = true;
                  updateButtons();
                  setHint('正在演示碰撞过程...');
                  state.rafId = requestAnimationFrame(tick);
                }

                controls.massA.value = spec.massA ?? 2;
                controls.velocityA.value = spec.velocityA ?? 4;
                controls.massB.value = spec.massB ?? 1;
                controls.velocityB.value = spec.velocityB ?? 0;
                syncLabels();
                syncModeButtons();
                resetScene();

                Object.values(controls).forEach((control) => {
                  control.addEventListener('input', () => {
                    syncLabels();
                    resetScene('参数已更新，场景已重置。');
                  });
                });
                modeButtons.forEach((button) => {
                  button.addEventListener('click', () => {
                    state.mode = button.dataset.mode;
                    syncModeButtons();
                    resetScene('碰撞类型已切换。');
                  });
                });
                startBtn.addEventListener('click', startSimulation);
                resetBtn.addEventListener('click', () => resetScene('已重置为当前参数。'));
                window.addEventListener('resize', () => render());
                """);
    }

    private String renderPptDemo(InteractiveTemplatePlan plan, InteractiveContext context) {
        Map<String, Object> spec = prepareSpec(plan, context, "按顺序浏览讲解卡片，用一个页面完成概念引入、展开与总结。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell ppt-demo-shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="row">
                      <button class="button button-primary" id="playBtn">自动播放</button>
                      <button class="button button-secondary" id="restartBtn">重新开始</button>
                    </div>
                  </header>

                  <section class="page-card demo-intro-card">
                    <div class="chip">演示讲解</div>
                    <p id="introText" class="demo-intro-text"></p>
                  </section>

                  <section class="page-card slide-stage-card">
                    <div class="slide-stage-top">
                      <div class="slide-counter" id="slideCounter"></div>
                      <div class="chip" id="slideTag"></div>
                    </div>
                    <h2 id="slideTitle"></h2>
                    <p id="slideSummary" class="slide-summary"></p>
                    <div class="highlight-banner" id="slideHighlight"></div>
                    <ul class="bullet-list" id="bulletList"></ul>
                  </section>

                  <section class="slide-tab-grid" id="slideTabs"></section>
                </div>
                """,
                """
                .demo-intro-card { margin-bottom: 16px; }
                .demo-intro-text {
                  margin: 12px 0 0;
                  color: var(--text);
                  line-height: 1.8;
                }
                .slide-stage-card {
                  position: relative;
                  overflow: hidden;
                  background:
                    radial-gradient(circle at top right, rgba(37, 99, 235, 0.16), transparent 28%),
                    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
                }
                .slide-stage-top {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  gap: 12px;
                }
                .slide-counter {
                  font-size: 13px;
                  font-weight: 800;
                  color: var(--muted);
                }
                .slide-stage-card h2 {
                  margin: 18px 0 10px;
                  font-size: clamp(26px, 4vw, 34px);
                  line-height: 1.2;
                }
                .slide-summary {
                  margin: 0;
                  max-width: 54ch;
                  color: var(--text);
                  line-height: 1.8;
                }
                .highlight-banner {
                  margin-top: 16px;
                  padding: 12px 14px;
                  border-radius: 14px;
                  background: linear-gradient(90deg, rgba(37, 99, 235, 0.10), rgba(56, 189, 248, 0.16));
                  color: var(--primary);
                  font-weight: 800;
                }
                .bullet-list {
                  margin: 18px 0 0;
                  padding-left: 20px;
                  display: grid;
                  gap: 10px;
                  color: var(--text);
                  line-height: 1.8;
                }
                .slide-tab-grid {
                  margin-top: 16px;
                  display: grid;
                  grid-template-columns: repeat(3, minmax(0, 1fr));
                  gap: 12px;
                }
                .slide-tab {
                  text-align: left;
                  padding: 14px;
                  border-radius: 16px;
                  border: 1px solid var(--border);
                  background: var(--panel);
                  box-shadow: var(--shadow);
                }
                .slide-tab.active {
                  border-color: var(--primary);
                  background: var(--primary-soft);
                  color: var(--primary);
                }
                .slide-tab-index {
                  display: inline-flex;
                  width: 28px;
                  height: 28px;
                  border-radius: 999px;
                  align-items: center;
                  justify-content: center;
                  background: rgba(37, 99, 235, 0.12);
                  font-size: 12px;
                  font-weight: 800;
                }
                .slide-tab-title {
                  margin-top: 12px;
                  font-size: 15px;
                  font-weight: 800;
                }
                .slide-tab-tag {
                  margin-top: 8px;
                  font-size: 12px;
                  color: var(--muted);
                }
                @media (max-width: 820px) {
                  .slide-tab-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                }
                @media (max-width: 560px) {
                  .slide-tab-grid { grid-template-columns: minmax(0, 1fr); }
                }
                """,
                """
                const slides = Array.isArray(spec.slides) ? spec.slides : [];
                const titleEl = document.getElementById('titleText');
                const subtitleEl = document.getElementById('subtitleText');
                const introText = document.getElementById('introText');
                const slideCounter = document.getElementById('slideCounter');
                const slideTag = document.getElementById('slideTag');
                const slideTitle = document.getElementById('slideTitle');
                const slideSummary = document.getElementById('slideSummary');
                const slideHighlight = document.getElementById('slideHighlight');
                const bulletList = document.getElementById('bulletList');
                const slideTabs = document.getElementById('slideTabs');
                const playBtn = document.getElementById('playBtn');
                const restartBtn = document.getElementById('restartBtn');

                titleEl.textContent = spec.title || '互动讲解页';
                subtitleEl.textContent = spec.subtitle || '按顺序浏览讲解卡片，用一个页面完成概念引入、展开与总结。';
                introText.textContent = spec.intro || '点击下方卡片，按节奏查看每一部分的讲解重点。';

                let activeIndex = 0;
                let timer = 0;

                function setActive(index) {
                  activeIndex = Math.max(0, Math.min(index, slides.length - 1));
                  Array.from(slideTabs.children).forEach((button, buttonIndex) => {
                    button.classList.toggle('active', buttonIndex === activeIndex);
                  });
                  const slide = slides[activeIndex] || { tag: '讲解', title: '内容', summary: '', bullets: [], highlight: '' };
                  slideCounter.textContent = '第 ' + String(activeIndex + 1) + ' / ' + String(Math.max(slides.length, 1)) + ' 页';
                  slideTag.textContent = slide.tag || '讲解';
                  slideTitle.textContent = slide.title || ('内容页 ' + String(activeIndex + 1));
                  slideSummary.textContent = slide.summary || '';
                  slideHighlight.textContent = slide.highlight || '围绕这一页的核心信息展开讲解。';
                  bulletList.innerHTML = '';
                  (Array.isArray(slide.bullets) ? slide.bullets : []).forEach((item) => {
                    const li = document.createElement('li');
                    li.textContent = item;
                    bulletList.appendChild(li);
                  });
                }

                function stopAutoPlay() {
                  window.clearInterval(timer);
                  timer = 0;
                  playBtn.textContent = '自动播放';
                }

                function toggleAutoPlay() {
                  if (timer) {
                    stopAutoPlay();
                    return;
                  }
                  playBtn.textContent = '暂停播放';
                  timer = window.setInterval(() => {
                    if (activeIndex >= slides.length - 1) {
                      stopAutoPlay();
                      return;
                    }
                    setActive(activeIndex + 1);
                  }, 2200);
                }

                slides.forEach((slide, index) => {
                  const button = document.createElement('button');
                  button.type = 'button';
                  button.className = 'slide-tab';
                  button.innerHTML =
                    '<div class="slide-tab-index">' + String(index + 1) + '</div>' +
                    '<div class="slide-tab-title">' + (slide.title || ('内容页 ' + String(index + 1))) + '</div>' +
                    '<div class="slide-tab-tag">' + (slide.tag || '讲解模块') + '</div>';
                  button.addEventListener('click', () => {
                    stopAutoPlay();
                    setActive(index);
                  });
                  slideTabs.appendChild(button);
                });

                if (!slides.length) {
                  const empty = document.createElement('div');
                  empty.className = 'page-card muted';
                  empty.textContent = '当前还没有可展示的讲解模块。';
                  slideTabs.appendChild(empty);
                }

                playBtn.addEventListener('click', toggleAutoPlay);
                restartBtn.addEventListener('click', () => {
                  stopAutoPlay();
                  setActive(0);
                });
                setActive(0);
                """);
    }

    private String renderFlowDemo(InteractiveTemplatePlan plan, InteractiveContext context) {
        Map<String, Object> spec = prepareSpec(plan, context, "点击流程节点，逐步查看关键阶段和变化关系。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell flow-demo-shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="row">
                      <button class="button button-primary" id="playBtn">自动演示</button>
                      <button class="button button-secondary" id="restartBtn">回到起点</button>
                    </div>
                  </header>

                  <section class="page-card flow-overview-card">
                    <div class="chip">流程演示</div>
                    <p id="overviewText" class="flow-overview-text"></p>
                  </section>

                  <section class="flow-layout">
                    <div class="page-card flow-rail" id="flowRail"></div>
                    <div class="page-card flow-detail-card">
                      <div class="flow-detail-top">
                        <div class="chip" id="flowKeyword"></div>
                        <div class="flow-progress" id="flowProgress"></div>
                      </div>
                      <h2 id="flowTitle"></h2>
                      <p id="flowDescription" class="flow-description"></p>
                    </div>
                  </section>
                </div>
                """,
                """
                .flow-overview-card { margin-bottom: 16px; }
                .flow-overview-text {
                  margin: 12px 0 0;
                  color: var(--text);
                  line-height: 1.8;
                }
                .flow-layout {
                  display: grid;
                  grid-template-columns: 320px minmax(0, 1fr);
                  gap: 16px;
                }
                .flow-rail {
                  display: flex;
                  flex-direction: column;
                  gap: 10px;
                  position: relative;
                }
                .flow-node {
                  text-align: left;
                  padding: 14px;
                  border-radius: 16px;
                  border: 1px solid var(--border);
                  background: #f8fafc;
                  color: var(--text);
                }
                .flow-node.active {
                  border-color: var(--primary);
                  background: var(--primary-soft);
                  color: var(--primary);
                }
                .flow-node-order {
                  display: inline-flex;
                  width: 28px;
                  height: 28px;
                  border-radius: 999px;
                  align-items: center;
                  justify-content: center;
                  background: rgba(37, 99, 235, 0.12);
                  font-size: 12px;
                  font-weight: 800;
                }
                .flow-node-title {
                  margin-top: 10px;
                  font-size: 15px;
                  font-weight: 800;
                }
                .flow-node-keyword {
                  margin-top: 6px;
                  font-size: 12px;
                  color: var(--muted);
                }
                .flow-connector {
                  width: 2px;
                  height: 24px;
                  margin: -2px auto -2px 14px;
                  background: linear-gradient(180deg, rgba(37, 99, 235, 0.25), rgba(37, 99, 235, 0.05));
                }
                .flow-detail-card {
                  display: flex;
                  flex-direction: column;
                  justify-content: center;
                  min-height: 380px;
                  background:
                    radial-gradient(circle at top right, rgba(56, 189, 248, 0.14), transparent 30%),
                    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
                }
                .flow-detail-top {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  gap: 12px;
                }
                .flow-progress {
                  font-size: 13px;
                  font-weight: 800;
                  color: var(--muted);
                }
                .flow-detail-card h2 {
                  margin: 18px 0 10px;
                  font-size: clamp(28px, 4vw, 36px);
                  line-height: 1.2;
                }
                .flow-description {
                  margin: 0;
                  max-width: 56ch;
                  color: var(--text);
                  line-height: 1.85;
                }
                @media (max-width: 820px) {
                  .flow-layout { grid-template-columns: minmax(0, 1fr); }
                }
                """,
                """
                const steps = Array.isArray(spec.steps) ? spec.steps : [];
                const titleEl = document.getElementById('titleText');
                const subtitleEl = document.getElementById('subtitleText');
                const overviewText = document.getElementById('overviewText');
                const flowRail = document.getElementById('flowRail');
                const flowKeyword = document.getElementById('flowKeyword');
                const flowProgress = document.getElementById('flowProgress');
                const flowTitle = document.getElementById('flowTitle');
                const flowDescription = document.getElementById('flowDescription');
                const playBtn = document.getElementById('playBtn');
                const restartBtn = document.getElementById('restartBtn');

                titleEl.textContent = spec.title || '流程演示页';
                subtitleEl.textContent = spec.subtitle || '点击流程节点，逐步查看关键阶段和变化关系。';
                overviewText.textContent = spec.overview || '这个页面适合讲解循环、流程、机制、时序或生命周期。';

                let activeIndex = 0;
                let timer = 0;

                function setActive(index) {
                  activeIndex = Math.max(0, Math.min(index, steps.length - 1));
                  Array.from(flowRail.querySelectorAll('.flow-node')).forEach((button, buttonIndex) => {
                    button.classList.toggle('active', buttonIndex === activeIndex);
                  });
                  const step = steps[activeIndex] || { title: '阶段', description: '', keyword: '流程节点' };
                  flowKeyword.textContent = step.keyword || '流程节点';
                  flowProgress.textContent = '第 ' + String(activeIndex + 1) + ' / ' + String(Math.max(steps.length, 1)) + ' 步';
                  flowTitle.textContent = step.title || ('阶段 ' + String(activeIndex + 1));
                  flowDescription.textContent = step.description || '';
                }

                function stopAutoPlay() {
                  window.clearInterval(timer);
                  timer = 0;
                  playBtn.textContent = '自动演示';
                }

                function toggleAutoPlay() {
                  if (timer) {
                    stopAutoPlay();
                    return;
                  }
                  playBtn.textContent = '暂停演示';
                  timer = window.setInterval(() => {
                    if (activeIndex >= steps.length - 1) {
                      stopAutoPlay();
                      return;
                    }
                    setActive(activeIndex + 1);
                  }, 2100);
                }

                steps.forEach((step, index) => {
                  const button = document.createElement('button');
                  button.type = 'button';
                  button.className = 'flow-node';
                  button.innerHTML =
                    '<div class="flow-node-order">' + String(index + 1) + '</div>' +
                    '<div class="flow-node-title">' + (step.title || ('阶段 ' + String(index + 1))) + '</div>' +
                    '<div class="flow-node-keyword">' + (step.keyword || '流程节点') + '</div>';
                  button.addEventListener('click', () => {
                    stopAutoPlay();
                    setActive(index);
                  });
                  flowRail.appendChild(button);
                  if (index < steps.length - 1) {
                    const connector = document.createElement('div');
                    connector.className = 'flow-connector';
                    flowRail.appendChild(connector);
                  }
                });

                if (!steps.length) {
                  const empty = document.createElement('div');
                  empty.className = 'muted';
                  empty.textContent = '当前还没有可展示的流程步骤。';
                  flowRail.appendChild(empty);
                }

                playBtn.addEventListener('click', toggleAutoPlay);
                restartBtn.addEventListener('click', () => {
                  stopAutoPlay();
                  setActive(0);
                });
                setActive(0);
                """);
    }

    private String renderAnimationStepper(InteractiveTemplatePlan plan, InteractiveContext context) {
        Map<String, Object> spec = prepareSpec(plan, context, "按照顺序浏览步骤，用一页完成讲解、演示与归纳。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell stepper-shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="row">
                      <button class="button button-primary" id="playBtn">自动播放</button>
                      <button class="button button-secondary" id="restartBtn">从头开始</button>
                    </div>
                  </header>

                  <section class="page-card intro-card">
                    <div class="chip">讲解流程</div>
                    <p id="introText" class="intro-text"></p>
                  </section>

                  <section class="step-layout">
                    <div class="page-card step-tabs" id="stepTabs"></div>
                    <div class="page-card step-stage">
                      <div class="progress-track"><div class="progress-bar" id="progressBar"></div></div>
                      <div class="stage-index" id="stageIndex"></div>
                      <h2 id="stepTitle"></h2>
                      <p id="stepText"></p>
                    </div>
                  </section>
                </div>
                """,
                """
                .intro-card { margin-bottom: 16px; }
                .intro-text { margin: 12px 0 0; color: var(--text); line-height: 1.75; }
                .step-layout {
                  display: grid;
                  grid-template-columns: 280px minmax(0, 1fr);
                  gap: 16px;
                }
                .step-tabs {
                  display: flex;
                  flex-direction: column;
                  gap: 10px;
                }
                .step-tab {
                  text-align: left;
                  border: 1px solid var(--border);
                  background: #f8fafc;
                  color: var(--text);
                }
                .step-tab.active {
                  background: var(--primary-soft);
                  border-color: var(--primary);
                  color: var(--primary);
                }
                .step-stage {
                  min-height: 360px;
                  display: flex;
                  flex-direction: column;
                  justify-content: center;
                  background:
                    radial-gradient(circle at top right, rgba(37, 99, 235, 0.12), transparent 30%),
                    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
                }
                .progress-track {
                  height: 10px;
                  border-radius: 999px;
                  background: #e2e8f0;
                  overflow: hidden;
                }
                .progress-bar {
                  height: 100%;
                  width: 0;
                  background: linear-gradient(90deg, #2563eb, #38bdf8);
                }
                .stage-index {
                  margin-top: 22px;
                  width: 70px;
                  height: 70px;
                  border-radius: 22px;
                  background: var(--primary-soft);
                  color: var(--primary);
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  font-size: 32px;
                  font-weight: 800;
                }
                .step-stage h2 {
                  margin: 18px 0 10px;
                  font-size: 28px;
                }
                .step-stage p {
                  margin: 0;
                  max-width: 52ch;
                  color: var(--text);
                  line-height: 1.8;
                  font-size: 16px;
                }
                @media (max-width: 760px) {
                  .step-layout { grid-template-columns: minmax(0, 1fr); }
                }
                """,
                """
                const steps = Array.isArray(spec.steps) ? spec.steps : [];
                const titleEl = document.getElementById('titleText');
                const subtitleEl = document.getElementById('subtitleText');
                const introText = document.getElementById('introText');
                const stepTabs = document.getElementById('stepTabs');
                const stepTitle = document.getElementById('stepTitle');
                const stepText = document.getElementById('stepText');
                const progressBar = document.getElementById('progressBar');
                const stageIndex = document.getElementById('stageIndex');
                const playBtn = document.getElementById('playBtn');
                const restartBtn = document.getElementById('restartBtn');

                titleEl.textContent = spec.title || '互动讲解页';
                subtitleEl.textContent = spec.subtitle || '按照顺序浏览步骤，用一页完成讲解、演示与归纳。';
                introText.textContent = spec.intro || '点击左侧步骤，逐步查看知识点。';

                let activeIndex = 0;
                let timer = 0;

                function setActive(index) {
                  activeIndex = Math.max(0, Math.min(index, steps.length - 1));
                  Array.from(stepTabs.children).forEach((button, buttonIndex) => {
                    button.classList.toggle('active', buttonIndex === activeIndex);
                  });
                  const step = steps[activeIndex] || { title: '步骤', text: '' };
                  stageIndex.textContent = String(activeIndex + 1);
                  stepTitle.textContent = step.title || ('步骤 ' + (activeIndex + 1));
                  stepText.textContent = step.text || '';
                  progressBar.style.width = (((activeIndex + 1) / Math.max(steps.length, 1)) * 100) + '%';
                }

                function stopAutoPlay() {
                  window.clearInterval(timer);
                  timer = 0;
                  playBtn.textContent = '自动播放';
                }

                function toggleAutoPlay() {
                  if (timer) {
                    stopAutoPlay();
                    return;
                  }
                  playBtn.textContent = '暂停播放';
                  timer = window.setInterval(() => {
                    if (activeIndex >= steps.length - 1) {
                      stopAutoPlay();
                      return;
                    }
                    setActive(activeIndex + 1);
                  }, 1800);
                }

                steps.forEach((step, index) => {
                  const button = document.createElement('button');
                  button.type = 'button';
                  button.className = 'button step-tab';
                  button.textContent = (index + 1) + '. ' + (step.title || ('步骤 ' + (index + 1)));
                  button.addEventListener('click', () => {
                    stopAutoPlay();
                    setActive(index);
                  });
                  stepTabs.appendChild(button);
                });

                if (!steps.length) {
                  const empty = document.createElement('div');
                  empty.className = 'muted';
                  empty.textContent = '当前还没有步骤内容。';
                  stepTabs.appendChild(empty);
                }

                playBtn.addEventListener('click', toggleAutoPlay);
                restartBtn.addEventListener('click', () => {
                  stopAutoPlay();
                  setActive(0);
                });
                setActive(0);
                """);
    }

    private String renderParameterExplorer(InteractiveTemplatePlan plan, InteractiveContext context) {
        Map<String, Object> spec = prepareSpec(plan, context, "拖动参数，观察结果数值和曲线如何随之变化。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell parameter-shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="chip">参数实验台</div>
                  </header>

                  <section class="page-card formula-card">
                    <div class="panel-title">公式说明</div>
                    <div id="formulaText" class="formula-text"></div>
                  </section>

                  <section class="explorer-layout">
                    <div class="page-card controls-panel" id="controlsPanel"></div>
                    <div class="page-card result-panel">
                      <div class="stats-grid" id="metricGrid"></div>
                      <canvas id="chartCanvas" height="280"></canvas>
                      <div id="chartHint" class="scene-hint"></div>
                    </div>
                  </section>
                </div>
                """,
                """
                .formula-card { margin-bottom: 16px; }
                .formula-text { color: var(--text); line-height: 1.8; }
                .explorer-layout {
                  display: grid;
                  grid-template-columns: 320px minmax(0, 1fr);
                  gap: 16px;
                }
                .controls-panel {
                  display: flex;
                  flex-direction: column;
                  gap: 12px;
                }
                .slider-card {
                  padding: 14px;
                  border-radius: 14px;
                  background: var(--panel-soft);
                  border: 1px solid var(--border);
                }
                .slider-head {
                  display: flex;
                  justify-content: space-between;
                  gap: 10px;
                  font-size: 14px;
                  font-weight: 700;
                }
                .slider-card input { width: 100%; margin-top: 10px; }
                .result-panel canvas {
                  width: 100%;
                  display: block;
                  margin-top: 16px;
                  border-radius: 16px;
                  border: 1px solid var(--border);
                  background: linear-gradient(180deg, #ffffff, #f8fbff);
                }
                @media (max-width: 820px) {
                  .explorer-layout { grid-template-columns: minmax(0, 1fr); }
                }
                """,
                """
                const parameters = Array.isArray(spec.parameters) ? spec.parameters : [];
                const metrics = Array.isArray(spec.metrics) ? spec.metrics : [];
                const chart = spec.chart || {};
                const titleEl = document.getElementById('titleText');
                const subtitleEl = document.getElementById('subtitleText');
                const formulaText = document.getElementById('formulaText');
                const controlsPanel = document.getElementById('controlsPanel');
                const metricGrid = document.getElementById('metricGrid');
                const chartHint = document.getElementById('chartHint');
                const canvas = document.getElementById('chartCanvas');
                const ctx = canvas.getContext('2d');
                const values = {};
                const valueLabels = {};
                const chartAxisKey = typeof chart.xKey === 'string' && chart.xKey.trim() ? chart.xKey.trim() : 'x';
                const chartAxisMin = Number.isFinite(Number(chart.xMin)) ? Number(chart.xMin) : -10;
                const chartAxisMax = Number.isFinite(Number(chart.xMax)) ? Number(chart.xMax) : 10;
                const chartAxisDefaultValue = Number.isFinite(Number(chart.defaultXValue)) ? Number(chart.defaultXValue) : 0;

                titleEl.textContent = spec.title || '参数变化演示';
                subtitleEl.textContent = spec.subtitle || '拖动参数，观察结果数值和曲线如何随之变化。';
                formulaText.textContent = spec.formulaText || '调整参数并观察结果。';

                function buildScope(overrideValues) {
                  const scope = { ...(overrideValues || {}) };
                  parameters.forEach((parameter) => {
                    if (!(parameter.key in scope)) {
                      scope[parameter.key] = values[parameter.key];
                    }
                  });
                  if (!(chartAxisKey in scope)) {
                    scope[chartAxisKey] = chartAxisDefaultValue;
                  }
                  return scope;
                }

                function buildEvaluator(expression, scope) {
                  const keys = Object.keys(scope);
                  return {
                    keys,
                    evaluator: new Function(...keys, 'Math', 'return (' + expression + ');')
                  };
                }

                function formatNumber(value, precision) {
                  if (!Number.isFinite(value)) {
                    return '--';
                  }
                  return Number(value).toFixed(precision ?? 2);
                }

                function resizeCanvas() {
                  const dpr = window.devicePixelRatio || 1;
                  const width = Math.max(320, canvas.clientWidth || 320);
                  const height = 280;
                  canvas.width = width * dpr;
                  canvas.height = height * dpr;
                  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                }

                function getWidth() {
                  return canvas.width / (window.devicePixelRatio || 1);
                }

                function evaluateExpression(expression, overrideValues) {
                  try {
                    const scope = buildScope(overrideValues);
                    const compiled = buildEvaluator(expression, scope);
                    const args = compiled.keys.map((key) => scope[key]);
                    const result = compiled.evaluator(...args, Math);
                    return Number(result);
                  } catch (error) {
                    return Number.NaN;
                  }
                }

                function renderMetrics() {
                  metricGrid.innerHTML = '';
                  metrics.forEach((metric) => {
                    const card = document.createElement('article');
                    card.className = 'stat-card';
                    const value = evaluateExpression(metric.expression);
                    const label = document.createElement('div');
                    label.className = 'stat-label';
                    label.textContent = metric.label || '结果';
                    const valueEl = document.createElement('div');
                    valueEl.className = 'stat-value';
                    valueEl.textContent = formatNumber(value, metric.precision ?? 2) + (metric.unit || '');
                    card.appendChild(label);
                    card.appendChild(valueEl);
                    metricGrid.appendChild(card);
                  });
                }

                function drawChart() {
                  resizeCanvas();
                  const width = getWidth();
                  const height = 280;
                  ctx.clearRect(0, 0, width, height);
                  ctx.strokeStyle = '#cbd5e1';
                  ctx.lineWidth = 1;
                  ctx.beginPath();
                  ctx.moveTo(46, 18);
                  ctx.lineTo(46, height - 34);
                  ctx.lineTo(width - 18, height - 34);
                  ctx.stroke();

                  const xParameter = parameters.find((parameter) => parameter.key === chartAxisKey);
                  const axisLabel = xParameter?.label || chartAxisKey;
                  const axisMin = xParameter ? Number(xParameter.min) : chartAxisMin;
                  const axisMax = xParameter ? Number(xParameter.max) : chartAxisMax;
                  if (!xParameter && !chartAxisKey) {
                    chartHint.textContent = '没有可用于绘图的参数。';
                    return;
                  }

                  if (!Number.isFinite(axisMin) || !Number.isFinite(axisMax) || axisMin >= axisMax) {
                    chartHint.textContent = 'Chart axis range is invalid.';
                    return;
                  }

                  const points = [];
                  const steps = 60;
                  for (let index = 0; index <= steps; index += 1) {
                    const ratio = index / steps;
                    const xValue = axisMin + (axisMax - axisMin) * ratio;
                    const current = { ...values, [chartAxisKey]: xValue };
                    const yValue = evaluateExpression(chart.yExpression || metrics[0]?.expression || '0', current);
                    if (Number.isFinite(yValue)) {
                      points.push({ xValue, yValue });
                    }
                  }
                  if (!points.length) {
                    chartHint.textContent = '当前表达式无法绘制曲线，请检查参数与公式。';
                    return;
                  }

                  const minY = Math.min(...points.map((point) => point.yValue));
                  const maxY = Math.max(...points.map((point) => point.yValue));
                  const ySpan = maxY - minY || 1;
                  chartHint.textContent = '横轴: ' + (xParameter.label || xParameter.key) + '，纵轴: ' + (chart.yLabel || '结果');

                  chartHint.textContent = '妯酱: ' + axisLabel + '锛岀旱杞? ' + (chart.yLabel || '缁撴灉');
                  ctx.strokeStyle = '#2563eb';
                  ctx.lineWidth = 3;
                  ctx.beginPath();
                  points.forEach((point, index) => {
                    const x = 46 + ((width - 64) * index) / Math.max(points.length - 1, 1);
                    const y = height - 34 - ((point.yValue - minY) / ySpan) * (height - 62);
                    if (index === 0) {
                      ctx.moveTo(x, y);
                    } else {
                      ctx.lineTo(x, y);
                    }
                  });
                  ctx.stroke();

                  ctx.fillStyle = '#64748b';
                  ctx.font = '12px Microsoft YaHei';
                  ctx.fillText(String(axisMin), 42, height - 12);
                  ctx.fillText(String(axisMax), width - 38, height - 12);
                  ctx.fillText(formatNumber(maxY, 2), 10, 28);
                  ctx.fillText(formatNumber(minY, 2), 10, height - 36);
                }

                function update() {
                  Object.entries(valueLabels).forEach(([key, label]) => {
                    const parameter = parameters.find((item) => item.key === key);
                    label.textContent = formatNumber(values[key], 2) + (parameter?.unit || '');
                  });
                  renderMetrics();
                  drawChart();
                }

                parameters.forEach((parameter) => {
                  values[parameter.key] = Number(parameter.defaultValue ?? 0);
                  const card = document.createElement('label');
                  card.className = 'slider-card';
                  const head = document.createElement('div');
                  head.className = 'slider-head';
                  const label = document.createElement('span');
                  label.textContent = parameter.label || parameter.key;
                  const value = document.createElement('strong');
                  head.appendChild(label);
                  head.appendChild(value);
                  card.appendChild(head);
                  const slider = document.createElement('input');
                  slider.type = 'range';
                  slider.min = parameter.min;
                  slider.max = parameter.max;
                  slider.step = parameter.step || 1;
                  slider.value = parameter.defaultValue ?? parameter.min;
                  slider.addEventListener('input', () => {
                    values[parameter.key] = Number(slider.value);
                    update();
                  });
                  card.appendChild(slider);
                  controlsPanel.appendChild(card);
                  valueLabels[parameter.key] = value;
                });

                window.addEventListener('resize', drawChart);
                update();
                """);
    }

    private String renderQuizPractice(InteractiveTemplatePlan plan, InteractiveContext context) {
        Map<String, Object> spec = prepareSpec(plan, context, "完成题目后立即查看得分、答案与解析。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell quiz-shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="row">
                      <button class="button button-primary" id="submitBtn">提交答案</button>
                      <button class="button button-secondary" id="resetBtn">重新作答</button>
                    </div>
                  </header>

                  <section class="page-card score-card" id="scoreCard">
                    <div class="chip">即时反馈</div>
                    <div class="score-text" id="scoreText">请先完成题目。</div>
                  </section>

                  <section class="quiz-list" id="quizList"></section>
                </div>
                """,
                """
                .score-card { margin-bottom: 16px; }
                .score-text {
                  margin-top: 12px;
                  font-size: 18px;
                  font-weight: 800;
                }
                .quiz-list {
                  display: flex;
                  flex-direction: column;
                  gap: 14px;
                }
                .quiz-card {
                  background: var(--panel);
                  border: 1px solid var(--border);
                  border-radius: var(--radius);
                  box-shadow: var(--shadow);
                  padding: 16px;
                }
                .quiz-card.correct { border-color: #10b981; }
                .quiz-card.wrong { border-color: #ef4444; }
                .quiz-card h3 {
                  margin: 0 0 12px;
                  font-size: 17px;
                }
                .options {
                  display: grid;
                  gap: 10px;
                }
                .option {
                  display: flex;
                  align-items: flex-start;
                  gap: 10px;
                  padding: 10px 12px;
                  border-radius: 12px;
                  border: 1px solid var(--border);
                  background: #f8fafc;
                }
                .option input { margin-top: 3px; }
                .short-answer {
                  width: 100%;
                  padding: 12px 14px;
                  border-radius: 12px;
                  border: 1px solid var(--border);
                  font-size: 15px;
                }
                .feedback {
                  margin-top: 12px;
                  padding: 12px 14px;
                  border-radius: 12px;
                  background: #f8fafc;
                  color: var(--muted);
                  display: none;
                }
                .feedback.show { display: block; }
                """,
                """
                const questions = Array.isArray(spec.questions) ? spec.questions : [];
                const titleEl = document.getElementById('titleText');
                const subtitleEl = document.getElementById('subtitleText');
                const quizList = document.getElementById('quizList');
                const scoreText = document.getElementById('scoreText');
                const submitBtn = document.getElementById('submitBtn');
                const resetBtn = document.getElementById('resetBtn');

                titleEl.textContent = spec.title || '互动练习';
                subtitleEl.textContent = spec.subtitle || '完成题目后立即查看得分、答案与解析。';

                function normalizeText(value) {
                  return String(value ?? '').trim().toLowerCase();
                }

                function renderQuestions() {
                  quizList.innerHTML = '';
                  questions.forEach((question, index) => {
                    const card = document.createElement('article');
                    card.className = 'quiz-card';
                    card.dataset.index = String(index);

                    const title = document.createElement('h3');
                    title.textContent = (index + 1) + '. ' + (question.prompt || '题目');
                    card.appendChild(title);

                    if (question.type === 'short_text') {
                      const input = document.createElement('input');
                      input.className = 'short-answer';
                      input.type = 'text';
                      input.placeholder = '请输入答案';
                      input.dataset.answerInput = 'true';
                      card.appendChild(input);
                    } else {
                      const optionsWrap = document.createElement('div');
                      optionsWrap.className = 'options';
                      const options = Array.isArray(question.options) ? question.options : [];
                      options.forEach((option, optionIndex) => {
                        const label = document.createElement('label');
                        label.className = 'option';
                        const input = document.createElement('input');
                        input.type = 'radio';
                        input.name = 'quiz_' + index;
                        input.value = option;
                        const text = document.createElement('span');
                        text.textContent = String.fromCharCode(65 + optionIndex) + '. ' + option;
                        label.appendChild(input);
                        label.appendChild(text);
                        optionsWrap.appendChild(label);
                      });
                      card.appendChild(optionsWrap);
                    }

                    const feedback = document.createElement('div');
                    feedback.className = 'feedback';
                    feedback.dataset.feedback = 'true';
                    card.appendChild(feedback);
                    quizList.appendChild(card);
                  });
                }

                function getUserAnswer(card, question, index) {
                  if (question.type === 'short_text') {
                    return card.querySelector('[data-answerInput="true"]')?.value
                      || card.querySelector('[data-answer-input="true"]')?.value
                      || '';
                  }
                  return card.querySelector('input[name="quiz_' + index + '"]:checked')?.value || '';
                }

                function grade() {
                  let correct = 0;
                  questions.forEach((question, index) => {
                    const card = quizList.children[index];
                    const userAnswer = getUserAnswer(card, question, index);
                    const expected = question.answer ?? '';
                    const isCorrect = normalizeText(userAnswer) === normalizeText(expected);
                    card.classList.remove('correct', 'wrong');
                    card.classList.add(isCorrect ? 'correct' : 'wrong');
                    if (isCorrect) {
                      correct += 1;
                    }
                    const feedback = card.querySelector('[data-feedback="true"]');
                    feedback.classList.add('show');
                    feedback.textContent = (isCorrect ? '回答正确。' : '回答有误。')
                      + ' 正确答案: ' + expected
                      + (question.explanation ? '。解析: ' + question.explanation : '');
                  });
                  scoreText.textContent = '本次得分: ' + correct + ' / ' + questions.length;
                }

                function reset() {
                  renderQuestions();
                  scoreText.textContent = '请先完成题目。';
                }

                submitBtn.addEventListener('click', grade);
                resetBtn.addEventListener('click', reset);
                renderQuestions();
                """);
    }

    private String renderDragMatch(InteractiveTemplatePlan plan, InteractiveContext context) {
        Map<String, Object> spec = prepareSpec(plan, context, "拖动左侧项目到右侧目标区，完成对应关系配对。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell drag-shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="row">
                      <button class="button button-primary" id="checkBtn">检查答案</button>
                      <button class="button button-secondary" id="clearBtn">清空配对</button>
                    </div>
                  </header>

                  <section class="drag-layout">
                    <div class="page-card bank-panel">
                      <div class="panel-title">可拖动项目</div>
                      <div class="card-bank" id="cardBank"></div>
                    </div>
                    <div class="page-card target-panel">
                      <div class="panel-title">目标区域</div>
                      <div class="drop-list" id="dropList"></div>
                    </div>
                  </section>

                  <section class="page-card">
                    <div class="note-box" id="resultBox">先完成拖拽，再检查答案。</div>
                  </section>
                </div>
                """,
                """
                .drag-layout {
                  display: grid;
                  grid-template-columns: 1fr 1fr;
                  gap: 16px;
                  margin-bottom: 16px;
                }
                .card-bank,
                .drop-list {
                  display: grid;
                  gap: 12px;
                }
                .drag-card,
                .drop-zone {
                  padding: 14px;
                  border-radius: 14px;
                  border: 1px solid var(--border);
                  background: #f8fafc;
                }
                .drag-card {
                  cursor: grab;
                  font-weight: 700;
                }
                .drag-card.used {
                  opacity: 0.45;
                }
                .drop-zone.over {
                  border-color: var(--primary);
                  background: var(--primary-soft);
                }
                .drop-label {
                  color: var(--muted);
                  font-size: 13px;
                  margin-bottom: 8px;
                }
                .drop-value {
                  min-height: 24px;
                  font-weight: 700;
                }
                .drop-zone.correct {
                  border-color: #10b981;
                }
                .drop-zone.wrong {
                  border-color: #ef4444;
                }
                @media (max-width: 760px) {
                  .drag-layout { grid-template-columns: minmax(0, 1fr); }
                }
                """,
                """
                const leftItems = Array.isArray(spec.leftItems) ? spec.leftItems : [];
                const rightItems = Array.isArray(spec.rightItems) ? spec.rightItems : [];
                const pairs = Array.isArray(spec.pairs) ? spec.pairs : [];
                const pairMap = new Map(pairs.map((pair) => [pair.rightId, pair.leftId]));
                const assignments = new Map();
                const titleEl = document.getElementById('titleText');
                const subtitleEl = document.getElementById('subtitleText');
                const cardBank = document.getElementById('cardBank');
                const dropList = document.getElementById('dropList');
                const resultBox = document.getElementById('resultBox');
                const checkBtn = document.getElementById('checkBtn');
                const clearBtn = document.getElementById('clearBtn');

                titleEl.textContent = spec.title || '拖拽配对';
                subtitleEl.textContent = spec.subtitle || '拖动左侧项目到右侧目标区，完成对应关系配对。';

                function renderBank() {
                  cardBank.innerHTML = '';
                  leftItems.forEach((item) => {
                    const card = document.createElement('div');
                    card.className = 'drag-card' + (Array.from(assignments.values()).includes(item.id) ? ' used' : '');
                    card.textContent = item.label || item.id;
                    card.draggable = true;
                    card.dataset.leftId = item.id;
                    card.addEventListener('dragstart', (event) => {
                      event.dataTransfer?.setData('text/plain', item.id);
                    });
                    cardBank.appendChild(card);
                  });
                }

                function renderTargets() {
                  dropList.innerHTML = '';
                  rightItems.forEach((item) => {
                    const zone = document.createElement('div');
                    zone.className = 'drop-zone';
                    zone.dataset.rightId = item.id;

                    const label = document.createElement('div');
                    label.className = 'drop-label';
                    label.textContent = item.label || item.id;

                    const value = document.createElement('div');
                    value.className = 'drop-value';
                    const leftId = assignments.get(item.id);
                    value.textContent = leftItems.find((left) => left.id === leftId)?.label || '拖到这里';

                    zone.addEventListener('dragover', (event) => {
                      event.preventDefault();
                      zone.classList.add('over');
                    });
                    zone.addEventListener('dragleave', () => zone.classList.remove('over'));
                    zone.addEventListener('drop', (event) => {
                      event.preventDefault();
                      zone.classList.remove('over');
                      const leftIdFromEvent = event.dataTransfer?.getData('text/plain');
                      if (!leftIdFromEvent) {
                        return;
                      }
                      Array.from(assignments.entries()).forEach(([rightId, leftId]) => {
                        if (leftId === leftIdFromEvent) {
                          assignments.delete(rightId);
                        }
                      });
                      assignments.set(item.id, leftIdFromEvent);
                      renderBank();
                      renderTargets();
                    });
                    zone.addEventListener('dblclick', () => {
                      assignments.delete(item.id);
                      renderBank();
                      renderTargets();
                    });

                    zone.appendChild(label);
                    zone.appendChild(value);
                    dropList.appendChild(zone);
                  });
                }

                function check() {
                  let correct = 0;
                  Array.from(dropList.children).forEach((zone) => {
                    zone.classList.remove('correct', 'wrong');
                    const rightId = zone.dataset.rightId;
                    const expectedLeftId = pairMap.get(rightId);
                    const currentLeftId = assignments.get(rightId);
                    if (expectedLeftId && currentLeftId === expectedLeftId) {
                      zone.classList.add('correct');
                      correct += 1;
                    } else if (currentLeftId) {
                      zone.classList.add('wrong');
                    }
                  });
                  resultBox.textContent = '配对正确 ' + correct + ' / ' + rightItems.length + '。双击目标框可清除该项。';
                  resultBox.classList.toggle('danger-box', correct !== rightItems.length);
                }

                function clearAssignments() {
                  assignments.clear();
                  resultBox.textContent = '先完成拖拽，再检查答案。';
                  resultBox.classList.remove('danger-box');
                  renderBank();
                  renderTargets();
                }

                checkBtn.addEventListener('click', check);
                clearBtn.addEventListener('click', clearAssignments);
                renderBank();
                renderTargets();
                """);
    }

    private String renderHotspotExplore(InteractiveTemplatePlan plan, InteractiveContext context) {
        Map<String, Object> spec = prepareSpec(plan, context, "点击画面中的热点，查看对应知识说明。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell hotspot-shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="chip" id="boardTitle"></div>
                  </header>

                  <section class="hotspot-layout">
                    <div class="page-card board-card">
                      <div class="explore-board" id="exploreBoard"></div>
                    </div>
                    <div class="page-card detail-card">
                      <div class="panel-title">热点说明</div>
                      <h2 id="detailTitle"></h2>
                      <p id="detailContent" class="detail-content"></p>
                      <div class="tag-list" id="tagList"></div>
                    </div>
                  </section>
                </div>
                """,
                """
                .hotspot-layout {
                  display: grid;
                  grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.9fr);
                  gap: 16px;
                }
                .explore-board {
                  position: relative;
                  min-height: 440px;
                  border-radius: 18px;
                  overflow: hidden;
                  background:
                    linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(14, 165, 233, 0.04)),
                    linear-gradient(0deg, rgba(148, 163, 184, 0.18) 1px, transparent 1px),
                    linear-gradient(90deg, rgba(148, 163, 184, 0.18) 1px, transparent 1px),
                    #ffffff;
                  background-size: auto, 28px 28px, 28px 28px, auto;
                  border: 1px solid var(--border);
                }
                .hotspot-pin {
                  position: absolute;
                  transform: translate(-50%, -50%);
                  width: 22px;
                  height: 22px;
                  border-radius: 999px;
                  border: 4px solid rgba(37, 99, 235, 0.25);
                  background: #2563eb;
                  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.25);
                }
                .hotspot-pin::after {
                  content: '';
                  position: absolute;
                  inset: -8px;
                  border-radius: 999px;
                  border: 1px dashed rgba(37, 99, 235, 0.35);
                }
                .hotspot-button {
                  position: absolute;
                  transform: translate(-50%, -50%);
                  background: transparent;
                  padding: 0;
                  border: none;
                }
                .hotspot-label {
                  margin-top: 32px;
                  padding: 6px 10px;
                  border-radius: 999px;
                  background: rgba(255, 255, 255, 0.92);
                  border: 1px solid var(--border);
                  white-space: nowrap;
                  font-size: 13px;
                  font-weight: 700;
                  color: var(--text);
                }
                .detail-card h2 { margin: 0 0 10px; }
                .detail-content { margin: 0; line-height: 1.8; color: var(--text); }
                .tag-list {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 8px;
                  margin-top: 18px;
                }
                .tag-chip {
                  border-radius: 999px;
                  padding: 8px 12px;
                  border: 1px solid var(--border);
                  background: #f8fafc;
                  cursor: pointer;
                  font-size: 13px;
                  font-weight: 700;
                }
                .tag-chip.active {
                  border-color: var(--primary);
                  background: var(--primary-soft);
                  color: var(--primary);
                }
                @media (max-width: 820px) {
                  .hotspot-layout { grid-template-columns: minmax(0, 1fr); }
                  .explore-board { min-height: 360px; }
                }
                """,
                """
                const hotspots = Array.isArray(spec.hotspots) ? spec.hotspots : [];
                const titleEl = document.getElementById('titleText');
                const subtitleEl = document.getElementById('subtitleText');
                const boardTitle = document.getElementById('boardTitle');
                const exploreBoard = document.getElementById('exploreBoard');
                const detailTitle = document.getElementById('detailTitle');
                const detailContent = document.getElementById('detailContent');
                const tagList = document.getElementById('tagList');

                titleEl.textContent = spec.title || '热点探索';
                subtitleEl.textContent = spec.subtitle || '点击画面中的热点，查看对应知识说明。';
                boardTitle.textContent = spec.boardTitle || '点击热点';

                let activeId = hotspots[0]?.id || '';

                function setActive(id) {
                  activeId = id;
                  const hotspot = hotspots.find((item) => item.id === id) || hotspots[0];
                  if (!hotspot) {
                    detailTitle.textContent = '暂无内容';
                    detailContent.textContent = '';
                    return;
                  }
                  detailTitle.textContent = hotspot.label || '热点';
                  detailContent.textContent = hotspot.content || '';
                  Array.from(tagList.children).forEach((chip) => {
                    chip.classList.toggle('active', chip.dataset.id === id);
                  });
                }

                hotspots.forEach((hotspot) => {
                  const button = document.createElement('button');
                  button.type = 'button';
                  button.className = 'hotspot-button';
                  button.style.left = (hotspot.x || 50) + '%';
                  button.style.top = (hotspot.y || 50) + '%';
                  const pin = document.createElement('span');
                  pin.className = 'hotspot-pin';
                  const label = document.createElement('span');
                  label.className = 'hotspot-label';
                  label.textContent = hotspot.label || '热点';
                  button.appendChild(pin);
                  button.appendChild(label);
                  button.addEventListener('click', () => setActive(hotspot.id));
                  exploreBoard.appendChild(button);

                  const chip = document.createElement('button');
                  chip.type = 'button';
                  chip.className = 'tag-chip';
                  chip.dataset.id = hotspot.id;
                  chip.textContent = hotspot.label || '热点';
                  chip.addEventListener('click', () => setActive(hotspot.id));
                  tagList.appendChild(chip);
                });

                setActive(activeId);
                """);
    }

    private String renderPlaceholder(InteractiveTemplatePlan plan, InteractiveContext context, String label) {
        Map<String, Object> spec = prepareSpec(plan, context, "模板内容正在渲染。");
        return renderDocument(
                resolveTitle(plan, context),
                spec,
                """
                <div class="shell">
                  <header class="page-header">
                    <div>
                      <h1 id="titleText"></h1>
                      <p class="page-subtitle" id="subtitleText"></p>
                    </div>
                    <div class="chip">%s</div>
                  </header>
                  <section class="page-card">
                    <pre id="specBox" style="white-space:pre-wrap;margin:0;font-size:13px;line-height:1.7;"></pre>
                  </section>
                </div>
                """.formatted(label),
                "",
                """
                document.getElementById('titleText').textContent = spec.title || '互动页面';
                document.getElementById('subtitleText').textContent = spec.subtitle || '模板内容正在渲染。';
                document.getElementById('specBox').textContent = JSON.stringify(spec, null, 2);
                """);
    }

    private Map<String, Object> prepareSpec(InteractiveTemplatePlan plan, InteractiveContext context, String subtitleFallback) {
        Map<String, Object> spec = copyMap(plan == null ? null : plan.getSpec());
        spec.putIfAbsent("title", resolveTitle(plan, context));
        spec.putIfAbsent("subtitle", subtitleFallback);
        return spec;
    }

    private String renderDocument(String title,
                                  Map<String, Object> spec,
                                  String body,
                                  String style,
                                  String script) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>%s</title>
                  <style>
                  %s
                  %s
                  </style>
                </head>
                <body>
                %s
                <script>
                const spec = %s;
                %s
                </script>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                COMMON_STYLE,
                style,
                body,
                toJsLiteral(spec),
                script
        );
    }

    private String resolveTitle(InteractiveTemplatePlan plan, InteractiveContext context) {
        if (plan != null && hasText(plan.getTitle())) {
            return plan.getTitle().trim();
        }
        if (plan != null && plan.getSpec() != null && hasText(String.valueOf(plan.getSpec().get("title")))) {
            return String.valueOf(plan.getSpec().get("title")).trim();
        }
        if (context != null && hasText(context.getTopic())) {
            return context.getTopic().trim();
        }
        return "互动页面";
    }

    private Map<String, Object> copyMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(raw);
    }

    private String toJsLiteral(Object value) {
        try {
            return objectMapper.writeValueAsString(value)
                    .replace("</script>", "<\\\\/script>");
        } catch (Exception e) {
            log.warn("Serialize interactive template config failed", e);
            return "{}";
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
