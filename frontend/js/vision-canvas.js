class VisionCanvas {
    constructor() {
        this.canvas = document.getElementById('visionCanvas');
        this.ctx = this.canvas.getContext('2d');
        this.currentData = null;
        this.terrainCache = null;
    }

    setTerrainData(terrainData) {
        this.terrainCache = terrainData;
    }

    update(visionResult) {
        this.currentData = visionResult;
        this.drawVisionMap();
        this.updateStats();
    }

    drawVisionMap() {
        const ctx = this.ctx;
        const w = this.canvas.width;
        const h = this.canvas.height;
        ctx.clearRect(0, 0, w, h);

        const data = this.currentData;
        if (!data) {
            this.drawEmpty();
            return;
        }

        const gridSize = 100;
        const cellW = w / gridSize;
        const cellH = h / gridSize;

        const elevationMap = {};
        const visibleSet = new Set();

        if (this.terrainCache) {
            this.terrainCache.forEach(te => {
                elevationMap[`${te.gridX},${te.gridY}`] = te.elevation;
            });
        }

        if (data.visibleGridPoints) {
            data.visibleGridPoints.forEach(p => visibleSet.add(`${p[0]},${p[1]}`));
        }

        let minElev = Infinity, maxElev = -Infinity;
        Object.values(elevationMap).forEach(e => {
            minElev = Math.min(minElev, e);
            maxElev = Math.max(maxElev, e);
        });
        const elevRange = maxElev - minElev || 1;

        for (let gx = 0; gx < gridSize; gx++) {
            for (let gy = 0; gy < gridSize; gy++) {
                const key = `${gx},${gy}`;
                const elev = elevationMap[key];
                const isVisible = visibleSet.has(key);

                const sx = gx * cellW;
                const sy = gy * cellH;

                if (elev !== undefined) {
                    const t = (elev - minElev) / elevRange;
                    if (isVisible) {
                        ctx.fillStyle = `rgba(6,182,212,${0.3 + t * 0.5})`;
                    } else {
                        const r = Math.floor(20 + t * 40);
                        const g = Math.floor(60 + t * 60);
                        const b = Math.floor(20 + t * 30);
                        ctx.fillStyle = `rgb(${r},${g},${b})`;
                    }
                } else {
                    ctx.fillStyle = '#0a0e17';
                }

                ctx.fillRect(sx, sy, cellW + 0.5, cellH + 0.5);
            }
        }

        const obsX = 50;
        const obsY = 50;
        const ox = obsX * cellW + cellW / 2;
        const oy = obsY * cellH + cellH / 2;

        ctx.fillStyle = '#ef4444';
        ctx.beginPath();
        ctx.arc(ox, oy, 6, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(ox, oy, 8, 0, Math.PI * 2);
        ctx.stroke();

        if (data.sectorAnalyses) {
            ctx.lineWidth = 1;
            data.sectorAnalyses.forEach(sector => {
                const angleRad = (sector.azimuth * Math.PI) / 180;
                const dist = Math.min(sector.visibleDistance / 10, 45);
                const endX = ox + Math.cos(angleRad) * dist * cellW;
                const endY = oy + Math.sin(angleRad) * dist * cellH;

                const grad = ctx.createLinearGradient(ox, oy, endX, endY);
                if (sector.isBlocked) {
                    grad.addColorStop(0, 'rgba(239,68,68,0.4)');
                    grad.addColorStop(1, 'rgba(239,68,68,0)');
                } else {
                    grad.addColorStop(0, 'rgba(6,182,212,0.4)');
                    grad.addColorStop(1, 'rgba(6,182,212,0)');
                }

                ctx.strokeStyle = grad;
                ctx.beginPath();
                ctx.moveTo(ox, oy);
                ctx.lineTo(endX, endY);
                ctx.stroke();
            });
        }

        const legendX = w - 120;
        const legendY = 20;
        ctx.fillStyle = 'rgba(17,24,39,0.85)';
        ctx.fillRect(legendX - 10, legendY - 5, 120, 80);
        ctx.strokeStyle = '#2d3a4a';
        ctx.strokeRect(legendX - 10, legendY - 5, 120, 80);

        ctx.fillStyle = '#8b95a5';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'left';
        ctx.fillText('图例', legendX, legendY + 8);

        ctx.fillStyle = 'rgba(6,182,212,0.6)';
        ctx.fillRect(legendX, legendY + 16, 12, 10);
        ctx.fillStyle = '#8b95a5';
        ctx.fillText('可见区域', legendX + 16, legendY + 25);

        ctx.fillStyle = 'rgb(40,80,40)';
        ctx.fillRect(legendX, legendY + 32, 12, 10);
        ctx.fillStyle = '#8b95a5';
        ctx.fillText('遮挡区域', legendX + 16, legendY + 41);

        ctx.fillStyle = '#ef4444';
        ctx.beginPath();
        ctx.arc(legendX + 6, legendY + 55, 4, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#8b95a5';
        ctx.fillText('观察点', legendX + 16, legendY + 58);

        ctx.fillStyle = '#8b95a5';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(`通视分析 | 高度: ${data.height}m | 覆盖率: ${(data.coverageRatio * 100).toFixed(1)}%`, w / 2, h - 10);
    }

    drawEmpty() {
        const ctx = this.ctx;
        const w = this.canvas.width;
        const h = this.canvas.height;

        ctx.fillStyle = '#0a0e17';
        ctx.fillRect(0, 0, w, h);

        ctx.strokeStyle = '#1a2332';
        ctx.lineWidth = 0.5;
        const step = 20;
        for (let x = 0; x < w; x += step) {
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, h);
            ctx.stroke();
        }
        for (let y = 0; y < h; y += step) {
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(w, y);
            ctx.stroke();
        }

        ctx.fillStyle = '#5a6577';
        ctx.font = '16px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('请运行视野分析以查看通视结果', w / 2, h / 2);
    }

    updateStats() {
        const data = this.currentData;
        if (!data) return;

        const el = (id) => document.getElementById(id);
        if (el('visiblePoints')) el('visiblePoints').textContent = data.visiblePoints || '--';
        if (el('totalPoints')) el('totalPoints').textContent = data.totalPoints || '--';
        if (el('coverageRatio')) el('coverageRatio').textContent =
            data.coverageRatio != null ? (data.coverageRatio * 100).toFixed(1) + '%' : '--';
        if (el('maxVisDist')) el('maxVisDist').textContent =
            data.maxVisibleDistance != null ? data.maxVisibleDistance.toFixed(0) + 'm' : '--';
        if (el('theoreticalHorizon')) el('theoreticalHorizon').textContent =
            data.theoreticalHorizon != null ? data.theoreticalHorizon.toFixed(0) + 'm' : '--';
    }
}

window.VisionCanvas = VisionCanvas;
