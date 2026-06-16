const VISION_API_BASE = 'http://localhost:8080/api';

class VisibilityPanel {
    constructor(visionCanvas) {
        this.visionCanvas = visionCanvas;
        this.selectedCartId = null;
        this.terrainData = null;
        this.lastResult = null;

        this.init();
    }

    init() {
        this.visionCanvas.drawEmpty();
        this.setupControls();
        this.loadTerrain();
    }

    setupControls() {
        document.getElementById('runVision').addEventListener('click', () => {
            this.runAnalysis();
        });
    }

    setSelectedCart(cartId) {
        this.selectedCartId = cartId;
    }

    async loadTerrain(regionName = 'default_battlefield') {
        try {
            const resp = await fetch(`${VISION_API_BASE}/terrain/${regionName}`);
            this.terrainData = await resp.json();
            this.visionCanvas.setTerrainData(this.terrainData);
        } catch (e) {
            console.error('加载地形数据失败:', e);
        }
    }

    async runAnalysis() {
        if (!this.selectedCartId) {
            alert('请先选择巢车');
            return;
        }

        const height = document.getElementById('simHeight').value;

        try {
            const resp = await fetch(
                `${VISION_API_BASE}/simulation/vision/${this.selectedCartId}?height=${height}&regionName=default_battlefield&observerGridX=50&observerGridY=50`,
                { method: 'POST' }
            );
            const result = await resp.json();

            this.lastResult = result;
            this.visionCanvas.update(result);
            this.updateSummary(result);

            document.querySelector('[data-tab="vision"]').click();

            return result;
        } catch (e) {
            console.error('运行视野分析失败:', e);
        }
    }

    updateSummary(result) {
        const container = document.getElementById('visionSummary');
        if (!container) return;

        const height = result.observerHeight || result.height || 0;
        container.innerHTML = `
            <div class="summary-row"><span class="summary-label">观察高度</span><span class="summary-value">${height.toFixed(1)} m</span></div>
            <div class="summary-row"><span class="summary-label">可见点数</span><span class="summary-value">${result.visiblePoints || 0}</span></div>
            <div class="summary-row"><span class="summary-label">总点数</span><span class="summary-value">${result.totalPoints || 0}</span></div>
            <div class="summary-row"><span class="summary-label">覆盖率</span><span class="summary-value">${((result.coverageRatio || 0) * 100).toFixed(1)}%</span></div>
            <div class="summary-row"><span class="summary-label">最大可视距离</span><span class="summary-value">${(result.maxVisibleDistance || 0).toFixed(0)} m</span></div>
            <div class="summary-row"><span class="summary-label">理论视距</span><span class="summary-value">${(result.theoreticalHorizon || 0).toFixed(0)} m</span></div>
        `;
    }

    updateHeight(height) {
        if (this.visionCanvas && typeof this.visionCanvas.updateHeight === 'function') {
            this.visionCanvas.updateHeight(height);
        }
    }

    getLastResult() {
        return this.lastResult;
    }
}

window.VisibilityPanel = VisibilityPanel;
