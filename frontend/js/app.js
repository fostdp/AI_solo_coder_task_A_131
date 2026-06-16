const API_BASE = 'http://localhost:8080/api';

class App {
    constructor() {
        this.nestcart3d = null;
        this.structureCanvas = null;
        this.visionPanel = null;
        this.stompClient = null;
        this.carts = [];
        this.selectedCartId = null;
        this.alerts = [];

        this.init();
    }

    async init() {
        this.structureCanvas = new StructureCanvas();
        this.visionPanel = new VisibilityPanel(new VisionCanvas());

        this.setupTabs();
        this.setupControls();
        this.setupWebSocket();

        await this.loadCarts();
        this.visionPanel.setSelectedCart(this.selectedCartId);

        const container = document.getElementById('threejs-container');
        if (container) {
            this.nestcart3d = new NestCart3D(container);
        }
    }

    setupTabs() {
        document.querySelectorAll('.tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                tab.classList.add('active');
                const tabId = tab.getAttribute('data-tab');
                document.getElementById(`tab-${tabId}`).classList.add('active');

                if (tabId === '3d' && this.nestcart3d) {
                    this.nestcart3d.onResize();
                }
            });
        });
    }

    setupControls() {
        const cartSelect = document.getElementById('cartSelect');
        cartSelect.addEventListener('change', () => {
            this.selectedCartId = cartSelect.value || null;
            if (this.visionPanel) {
                this.visionPanel.setSelectedCart(this.selectedCartId);
            }
        });

        const rangeInputs = [
            { id: 'simHeight', valId: 'simHeightVal', decimals: 1 },
            { id: 'simWindSpeed', valId: 'simWindSpeedVal', decimals: 1 },
            { id: 'simWindDir', valId: 'simWindDirVal', decimals: 0 }
        ];

        rangeInputs.forEach(({ id, valId, decimals }) => {
            const input = document.getElementById(id);
            const val = document.getElementById(valId);
            input.addEventListener('input', () => {
                val.textContent = parseFloat(input.value).toFixed(decimals);
                if (id === 'simHeight' && this.nestcart3d) {
                    this.nestcart3d.updateHeight(parseFloat(input.value));
                    this.nestcart3d.currentHeight = parseFloat(input.value);
                }
                if (id === 'simWindSpeed' && this.nestcart3d) {
                    this.nestcart3d.windSpeed = parseFloat(input.value);
                }
            });
        });

        document.getElementById('runSimulation').addEventListener('click', () => this.runSimulation());
        document.getElementById('runVision').addEventListener('click', () => {
            if (this.visionPanel) {
                this.visionPanel.runAnalysis();
            }
        });

        document.getElementById('showVisionCone').addEventListener('change', (e) => {
            if (this.nestcart3d) this.nestcart3d.showVisionCone = e.target.checked;
        });
        document.getElementById('showWireframe').addEventListener('change', (e) => {
            if (this.nestcart3d) this.nestcart3d.showWireframe = e.target.checked;
        });
        document.getElementById('showTerrain').addEventListener('change', (e) => {
            if (this.nestcart3d) this.nestcart3d.showTerrain = e.target.checked;
        });
    }

    setupWebSocket() {
        const socket = new SockJS('http://localhost:8080/ws/alerts');
        this.stompClient = Stomp.over(socket);

        this.stompClient.connect({}, () => {
            this.updateConnectionStatus(true);

            this.stompClient.subscribe('/topic/alerts', (message) => {
                const alert = JSON.parse(message.body);
                this.addAlert(alert);
            });

            this.stompClient.subscribe('/topic/alerts/' + this.selectedCartId, (message) => {
                const alert = JSON.parse(message.body);
                this.addAlert(alert);
            });
        }, () => {
            this.updateConnectionStatus(false);
            setTimeout(() => this.setupWebSocket(), 5000);
        });
    }

    updateConnectionStatus(connected) {
        const indicator = document.getElementById('connectionStatus');
        if (connected) {
            indicator.classList.add('connected');
            indicator.querySelector('.text').textContent = '已连接';
        } else {
            indicator.classList.remove('connected');
            indicator.querySelector('.text').textContent = '未连接';
        }
    }

    async loadCarts() {
        try {
            const resp = await fetch(`${API_BASE}/carts`);
            this.carts = await resp.json();
            const select = document.getElementById('cartSelect');
            this.carts.forEach(cart => {
                const opt = document.createElement('option');
                opt.value = cart.id;
                opt.textContent = cart.name;
                select.appendChild(opt);
            });

            if (this.carts.length > 0) {
                this.selectedCartId = this.carts[0].id;
                select.value = this.selectedCartId;
            }
        } catch (e) {
            console.error('加载巢车列表失败:', e);
        }
    }

    async runSimulation() {
        if (!this.selectedCartId) {
            alert('请先选择巢车');
            return;
        }

        const height = document.getElementById('simHeight').value;
        const windSpeed = document.getElementById('simWindSpeed').value;
        const windDir = document.getElementById('simWindDir').value;

        try {
            const resp = await fetch(
                `${API_BASE}/simulation/structure/${this.selectedCartId}?height=${height}&windSpeed=${windSpeed}&windDirection=${windDir}`,
                { method: 'POST' }
            );
            const result = await resp.json();

            this.structureCanvas.update(result);
            this.updateStructureStatus(result);
            this.updateSimSummary(result);

            if (this.nestcart3d) {
                this.nestcart3d.updateSimulation(result);
            }

            document.querySelector('[data-tab="structure"]').click();

        } catch (e) {
            console.error('运行仿真失败:', e);
        }
    }

    updateStructureStatus(result) {
        const stressEl = document.getElementById('stressStatus');
        const stabilityEl = document.getElementById('stabilityStatus');
        const safetyEl = document.getElementById('safetyFactor');
        const ratioEl = document.getElementById('stressRatio');

        const statusMap = {
            'NORMAL': { text: '正常', class: 'normal' },
            'WARNING': { text: '警告', class: 'warning' },
            'CRITICAL': { text: '危险', class: 'critical' },
            'STABLE': { text: '稳定', class: 'normal' },
            'MARGINAL': { text: '临界', class: 'warning' },
            'UNSTABLE': { text: '不稳定', class: 'critical' }
        };

        const ss = statusMap[result.stressStatus] || statusMap['NORMAL'];
        stressEl.textContent = ss.text;
        stressEl.className = 'status-badge ' + ss.class;

        const st = statusMap[result.stabilityStatus] || statusMap['STABLE'];
        stabilityEl.textContent = st.text;
        stabilityEl.className = 'status-badge ' + st.class;

        if (result.safetyFactor != null) {
            safetyEl.textContent = result.safetyFactor.toFixed(2);
            safetyEl.style.color = result.safetyFactor < 1.5 ? '#ef4444' : '#10b981';
        }

        if (result.stressRatio != null) {
            ratioEl.textContent = (result.stressRatio * 100).toFixed(1) + '%';
            ratioEl.style.color = result.stressRatio > 0.95 ? '#ef4444' : result.stressRatio > 0.8 ? '#f59e0b' : '#10b981';
        }
    }

    updateSimSummary(result) {
        const container = document.getElementById('simSummary');
        container.innerHTML = `
            <div class="summary-row"><span class="summary-label">总应力</span><span class="summary-value">${(result.totalStress / 1e6).toFixed(2)} MPa</span></div>
            <div class="summary-row"><span class="summary-label">重力应力</span><span class="summary-value">${(result.gravityStress / 1e6).toFixed(2)} MPa</span></div>
            <div class="summary-row"><span class="summary-label">风载应力</span><span class="summary-value">${(result.windStress / 1e6).toFixed(2)} MPa</span></div>
            <div class="summary-row"><span class="summary-label">总挠度</span><span class="summary-value">${(result.totalDeflection * 1000).toFixed(2)} mm</span></div>
            <div class="summary-row"><span class="summary-label">安全系数</span><span class="summary-value">${result.safetyFactor.toFixed(2)}</span></div>
            <div class="summary-row"><span class="summary-label">应力状态</span><span class="summary-value">${result.stressStatus}</span></div>
            <div class="summary-row"><span class="summary-label">稳定状态</span><span class="summary-value">${result.stabilityStatus}</span></div>
        `;
    }

    addAlert(alert) {
        this.alerts.unshift(alert);
        if (this.alerts.length > 50) this.alerts.pop();

        const counter = document.getElementById('alertCounter');
        const badge = counter.querySelector('.badge');
        const unacked = this.alerts.length;
        badge.textContent = unacked;
        counter.style.display = 'flex';

        this.renderAlerts();

        if (alert.alertType === 'STRESS_OVERLIMIT') {
            const stressEl = document.getElementById('boomStress');
            if (stressEl) {
                stressEl.style.color = '#ef4444';
                setTimeout(() => { stressEl.style.color = ''; }, 3000);
            }
        }
    }

    renderAlerts() {
        const list = document.getElementById('alertList');
        if (this.alerts.length === 0) {
            list.innerHTML = '<div class="no-alerts">暂无告警</div>';
            return;
        }

        list.innerHTML = this.alerts.map(alert => `
            <div class="alert-item ${alert.severity === 'CRITICAL' ? 'critical' : ''}">
                <div class="alert-header">
                    <span class="alert-type">${alert.alertType === 'STRESS_OVERLIMIT' ? '⚠️ 应力超限' : '⚠️ 晃动超限'}</span>
                    <span class="alert-time">${new Date(alert.createdAt).toLocaleTimeString()}</span>
                </div>
                <div class="alert-msg">${alert.message}</div>
            </div>
        `).join('');
    }

    updateSensorDisplay(data) {
        const el = (id) => document.getElementById(id);
        if (el('boomStress') && data.boomStress != null) {
            el('boomStress').textContent = (data.boomStress / 1e6).toFixed(2);
        }
        if (el('basketSway') && data.basketSway != null) {
            el('basketSway').textContent = data.basketSway.toFixed(3);
        }
        if (el('currentHeight') && data.height != null) {
            el('currentHeight').textContent = data.height.toFixed(1);
        }
        if (el('obsDistance') && data.observationDistance != null) {
            el('obsDistance').textContent = data.observationDistance.toFixed(0);
        }
        if (el('windSpeed') && data.windSpeed != null) {
            el('windSpeed').textContent = data.windSpeed.toFixed(1);
        }
        if (el('windDir') && data.windDirection != null) {
            el('windDir').textContent = data.windDirection.toFixed(0);
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.app = new App();
});
