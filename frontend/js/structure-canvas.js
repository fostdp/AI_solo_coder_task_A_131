class StructureCanvas {
    constructor() {
        this.stressCanvas = document.getElementById('stressCanvas');
        this.deflectionCanvas = document.getElementById('deflectionCanvas');
        this.momentShearCanvas = document.getElementById('momentShearCanvas');
        this.windLoadCanvas = document.getElementById('windLoadCanvas');
        this.currentData = null;
    }

    update(simulationResult) {
        this.currentData = simulationResult;
        this.drawStressDistribution();
        this.drawDeflectionDistribution();
        this.drawMomentShear();
        this.drawWindLoad();
    }

    drawStressDistribution() {
        const canvas = this.stressCanvas;
        const ctx = canvas.getContext('2d');
        const w = canvas.width;
        const h = canvas.height;
        ctx.clearRect(0, 0, w, h);

        if (!this.currentData || !this.currentData.beamElements) return;

        const elements = this.currentData.beamElements;
        const maxStress = Math.max(...elements.map(e => e.stress));
        const stressLimit = this.currentData.stressRatio > 0 ? maxStress / this.currentData.stressRatio : maxStress * 1.2;

        const pad = { top: 30, right: 30, bottom: 50, left: 70 };
        const plotW = w - pad.left - pad.right;
        const plotH = h - pad.top - pad.bottom;

        ctx.fillStyle = '#8b95a5';
        ctx.font = '12px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('悬臂应力分布 (Von Mises)', w / 2, 20);

        ctx.strokeStyle = '#2d3a4a';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(pad.left, pad.top);
        ctx.lineTo(pad.left, pad.top + plotH);
        ctx.lineTo(pad.left + plotW, pad.top + plotH);
        ctx.stroke();

        const yTicks = 5;
        for (let i = 0; i <= yTicks; i++) {
            const y = pad.top + plotH - (i / yTicks) * plotH;
            const val = (i / yTicks) * stressLimit;
            ctx.strokeStyle = '#1a2332';
            ctx.beginPath();
            ctx.moveTo(pad.left, y);
            ctx.lineTo(pad.left + plotW, y);
            ctx.stroke();

            ctx.fillStyle = '#5a6577';
            ctx.font = '10px sans-serif';
            ctx.textAlign = 'right';
            ctx.fillText((val / 1e6).toFixed(1), pad.left - 5, y + 3);
        }

        ctx.fillStyle = '#8b95a5';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('位置 (m)', pad.left + plotW / 2, h - 10);
        ctx.save();
        ctx.translate(15, pad.top + plotH / 2);
        ctx.rotate(-Math.PI / 2);
        ctx.fillText('应力 (MPa)', 0, 0);
        ctx.restore();

        ctx.beginPath();
        ctx.strokeStyle = '#ef4444';
        ctx.lineWidth = 1;
        ctx.setLineDash([5, 5]);
        const limitY = pad.top + plotH - (stressLimit / stressLimit) * plotH;
        ctx.moveTo(pad.left, limitY);
        ctx.lineTo(pad.left + plotW, limitY);
        ctx.stroke();
        ctx.setLineDash([]);

        ctx.fillStyle = '#ef4444';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'left';
        ctx.fillText('应力限值', pad.left + plotW + 2, limitY + 3);

        const grad = ctx.createLinearGradient(pad.left, pad.top, pad.left, pad.top + plotH);
        grad.addColorStop(0, 'rgba(239,68,68,0.3)');
        grad.addColorStop(0.5, 'rgba(245,158,11,0.3)');
        grad.addColorStop(1, 'rgba(16,185,129,0.3)');

        ctx.beginPath();
        ctx.moveTo(pad.left, pad.top + plotH);
        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const y = pad.top + plotH - (elem.stress / stressLimit) * plotH;
            if (i === 0) ctx.lineTo(x, y);
            else ctx.lineTo(x, y);
        });
        ctx.lineTo(pad.left + plotW, pad.top + plotH);
        ctx.closePath();
        ctx.fillStyle = grad;
        ctx.fill();

        ctx.beginPath();
        ctx.strokeStyle = '#3b82f6';
        ctx.lineWidth = 2;
        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const y = pad.top + plotH - (elem.stress / stressLimit) * plotH;
            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
        });
        ctx.stroke();

        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const y = pad.top + plotH - (elem.stress / stressLimit) * plotH;
            const ratio = elem.stress / stressLimit;
            ctx.beginPath();
            ctx.arc(x, y, 3, 0, Math.PI * 2);
            ctx.fillStyle = ratio > 0.95 ? '#ef4444' : ratio > 0.8 ? '#f59e0b' : '#10b981';
            ctx.fill();
        });

        const xTicks = 5;
        for (let i = 0; i <= xTicks; i++) {
            const x = pad.left + (i / xTicks) * plotW;
            const val = (i / xTicks) * 8;
            ctx.fillStyle = '#5a6577';
            ctx.font = '10px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText(val.toFixed(1), x, pad.top + plotH + 15);
        }
    }

    drawDeflectionDistribution() {
        const canvas = this.deflectionCanvas;
        const ctx = canvas.getContext('2d');
        const w = canvas.width;
        const h = canvas.height;
        ctx.clearRect(0, 0, w, h);

        if (!this.currentData || !this.currentData.beamElements) return;

        const elements = this.currentData.beamElements;
        const maxDefl = Math.max(...elements.map(e => e.deflection));
        const scale = maxDefl > 0 ? maxDefl * 1.3 : 1;

        const pad = { top: 30, right: 30, bottom: 50, left: 70 };
        const plotW = w - pad.left - pad.right;
        const plotH = h - pad.top - pad.bottom;

        ctx.fillStyle = '#8b95a5';
        ctx.font = '12px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('悬臂挠度分布', w / 2, 20);

        ctx.strokeStyle = '#2d3a4a';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(pad.left, pad.top);
        ctx.lineTo(pad.left, pad.top + plotH);
        ctx.lineTo(pad.left + plotW, pad.top + plotH);
        ctx.stroke();

        const yTicks = 5;
        for (let i = 0; i <= yTicks; i++) {
            const y = pad.top + plotH - (i / yTicks) * plotH;
            const val = (i / yTicks) * scale;
            ctx.strokeStyle = '#1a2332';
            ctx.beginPath();
            ctx.moveTo(pad.left, y);
            ctx.lineTo(pad.left + plotW, y);
            ctx.stroke();

            ctx.fillStyle = '#5a6577';
            ctx.font = '10px sans-serif';
            ctx.textAlign = 'right';
            ctx.fillText((val * 1000).toFixed(1), pad.left - 5, y + 3);
        }

        const boomY = pad.top + plotH * 0.5;
        ctx.strokeStyle = '#5a6577';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(pad.left, boomY);
        ctx.lineTo(pad.left + plotW, boomY);
        ctx.stroke();

        ctx.strokeStyle = '#3b82f6';
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.moveTo(pad.left, boomY);
        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const defl = elem.deflection / scale;
            const y = boomY - defl * plotH * 0.4;
            ctx.lineTo(x, y);
        });
        ctx.stroke();

        ctx.strokeStyle = '#8b5cf6';
        ctx.lineWidth = 1.5;
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        ctx.moveTo(pad.left, boomY);
        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const defl = elem.deflection / scale;
            const y = boomY - defl * plotH * 0.4;
            ctx.lineTo(x, y);
        });
        ctx.stroke();
        ctx.setLineDash([]);

        ctx.fillStyle = '#8b95a5';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('悬臂长度 (m)', pad.left + plotW / 2, h - 10);
        ctx.save();
        ctx.translate(15, pad.top + plotH / 2);
        ctx.rotate(-Math.PI / 2);
        ctx.fillText('挠度 (mm)', 0, 0);
        ctx.restore();

        const xTicks = 5;
        for (let i = 0; i <= xTicks; i++) {
            const x = pad.left + (i / xTicks) * plotW;
            const val = (i / xTicks) * 8;
            ctx.fillStyle = '#5a6577';
            ctx.font = '10px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText(val.toFixed(1), x, pad.top + plotH + 15);
        }
    }

    drawMomentShear() {
        const canvas = this.momentShearCanvas;
        const ctx = canvas.getContext('2d');
        const w = canvas.width;
        const h = canvas.height;
        ctx.clearRect(0, 0, w, h);

        if (!this.currentData || !this.currentData.beamElements) return;

        const elements = this.currentData.beamElements;
        const halfH = h / 2;

        const pad = { top: 15, right: 20, bottom: 30, left: 60 };
        const plotW = w - pad.left - pad.right;

        ctx.fillStyle = '#8b95a5';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('弯矩分布 (N·m)', w / 2, 12);

        const maxMoment = Math.max(...elements.map(e => Math.abs(e.bendingMoment)));
        const momentScale = maxMoment > 0 ? maxMoment * 1.2 : 1;

        const midY1 = pad.top + (halfH - pad.top - 10) / 2;
        ctx.strokeStyle = '#1a2332';
        ctx.beginPath();
        ctx.moveTo(pad.left, midY1);
        ctx.lineTo(pad.left + plotW, midY1);
        ctx.stroke();

        ctx.strokeStyle = '#3b82f6';
        ctx.lineWidth = 2;
        ctx.beginPath();
        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const y = midY1 - (elem.bendingMoment / momentScale) * (halfH - pad.top - 10) / 2;
            if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        });
        ctx.stroke();

        const grad1 = ctx.createLinearGradient(0, pad.top, 0, halfH - 10);
        grad1.addColorStop(0, 'rgba(59,130,246,0.2)');
        grad1.addColorStop(1, 'rgba(59,130,246,0)');
        ctx.beginPath();
        ctx.moveTo(pad.left, midY1);
        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const y = midY1 - (elem.bendingMoment / momentScale) * (halfH - pad.top - 10) / 2;
            ctx.lineTo(x, y);
        });
        ctx.lineTo(pad.left + plotW, midY1);
        ctx.closePath();
        ctx.fillStyle = grad1;
        ctx.fill();

        ctx.fillStyle = '#8b95a5';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('剪力分布 (N)', w / 2, halfH + 12);

        const maxShear = Math.max(...elements.map(e => Math.abs(e.shearForce)));
        const shearScale = maxShear > 0 ? maxShear * 1.2 : 1;

        const midY2 = halfH + 20 + (h - halfH - 20 - pad.bottom) / 2;
        ctx.strokeStyle = '#1a2332';
        ctx.beginPath();
        ctx.moveTo(pad.left, midY2);
        ctx.lineTo(pad.left + plotW, midY2);
        ctx.stroke();

        ctx.strokeStyle = '#10b981';
        ctx.lineWidth = 2;
        ctx.beginPath();
        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const y = midY2 - (elem.shearForce / shearScale) * (h - halfH - 30 - pad.bottom) / 2;
            if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        });
        ctx.stroke();

        const grad2 = ctx.createLinearGradient(0, halfH + 20, 0, h - pad.bottom);
        grad2.addColorStop(0, 'rgba(16,185,129,0.2)');
        grad2.addColorStop(1, 'rgba(16,185,129,0)');
        ctx.beginPath();
        ctx.moveTo(pad.left, midY2);
        elements.forEach((elem, i) => {
            const x = pad.left + (i / (elements.length - 1)) * plotW;
            const y = midY2 - (elem.shearForce / shearScale) * (h - halfH - 30 - pad.bottom) / 2;
            ctx.lineTo(x, y);
        });
        ctx.lineTo(pad.left + plotW, midY2);
        ctx.closePath();
        ctx.fillStyle = grad2;
        ctx.fill();
    }

    drawWindLoad() {
        const canvas = this.windLoadCanvas;
        const ctx = canvas.getContext('2d');
        const w = canvas.width;
        const h = canvas.height;
        ctx.clearRect(0, 0, w, h);

        if (!this.currentData) return;

        const windSpeed = this.currentData.windSpeed || 0;
        const windDir = this.currentData.windDirection || 0;
        const pad = { top: 30, right: 30, bottom: 50, left: 70 };
        const plotW = w - pad.left - pad.right;
        const plotH = h - pad.top - pad.bottom;
        const cx = pad.left + plotW / 2;
        const cy = pad.top + plotH / 2;
        const radius = Math.min(plotW, plotH) / 2 - 20;

        ctx.fillStyle = '#8b95a5';
        ctx.font = '12px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('风荷载方向与强度', w / 2, 20);

        ctx.strokeStyle = '#2d3a4a';
        ctx.lineWidth = 1;
        for (let i = 0; i < 12; i++) {
            const angle = (i * 30 * Math.PI) / 180;
            ctx.beginPath();
            ctx.moveTo(cx + Math.cos(angle) * (radius - 5), cy + Math.sin(angle) * (radius - 5));
            ctx.lineTo(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius);
            ctx.stroke();

            const labelR = radius + 15;
            ctx.fillStyle = '#5a6577';
            ctx.font = '9px sans-serif';
            ctx.fillText(i * 30 + '°', cx + Math.cos(angle) * labelR, cy + Math.sin(angle) * labelR + 3);
        }

        ctx.strokeStyle = '#1a2332';
        ctx.lineWidth = 0.5;
        for (let r = 1; r <= 4; r++) {
            ctx.beginPath();
            ctx.arc(cx, cy, (r / 4) * radius, 0, Math.PI * 2);
            ctx.stroke();
        }

        ctx.strokeStyle = '#2d3a4a';
        ctx.beginPath();
        ctx.arc(cx, cy, radius, 0, Math.PI * 2);
        ctx.stroke();

        if (windSpeed > 0) {
            const windRad = (windDir * Math.PI) / 180;
            const arrowLen = (windSpeed / 30) * radius;

            ctx.strokeStyle = '#ef4444';
            ctx.lineWidth = 3;
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.lineTo(cx + Math.cos(windRad) * arrowLen, cy + Math.sin(windRad) * arrowLen);
            ctx.stroke();

            const tipX = cx + Math.cos(windRad) * arrowLen;
            const tipY = cy + Math.sin(windRad) * arrowLen;
            const headLen = 12;
            ctx.beginPath();
            ctx.moveTo(tipX, tipY);
            ctx.lineTo(tipX - headLen * Math.cos(windRad - 0.4), tipY - headLen * Math.sin(windRad - 0.4));
            ctx.moveTo(tipX, tipY);
            ctx.lineTo(tipX - headLen * Math.cos(windRad + 0.4), tipY - headLen * Math.sin(windRad + 0.4));
            ctx.stroke();

            const pressure = 0.5 * 1.225 * windSpeed * windSpeed * 1.2;
            ctx.fillStyle = '#ef4444';
            ctx.font = 'bold 14px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText(`风速: ${windSpeed.toFixed(1)} m/s`, cx, pad.top + plotH + 20);
            ctx.fillStyle = '#8b95a5';
            ctx.font = '11px sans-serif';
            ctx.fillText(`风压: ${pressure.toFixed(1)} Pa`, cx, pad.top + plotH + 35);
        } else {
            ctx.fillStyle = '#5a6577';
            ctx.font = '14px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('无风荷载', cx, cy);
        }

        ctx.fillStyle = '#10b981';
        ctx.beginPath();
        ctx.arc(cx, cy, 4, 0, Math.PI * 2);
        ctx.fill();

        const nAngle = -Math.PI / 2;
        ctx.fillStyle = '#ef4444';
        ctx.font = 'bold 10px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('N', cx + Math.cos(nAngle) * (radius + 25), cy + Math.sin(nAngle) * (radius + 25) + 3);
    }
}

window.StructureCanvas = StructureCanvas;
