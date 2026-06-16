class NestCart3D {
    constructor(container) {
        this.container = container;
        this.scene = null;
        this.camera = null;
        this.renderer = null;
        this.cartGroup = null;
        this.visionCone = null;
        this.wireframeGroup = null;
        this.terrainMesh = null;
        this.animationId = null;
        this.showVisionCone = true;
        this.showWireframe = true;
        this.showTerrain = true;
        this.currentHeight = 10;
        this.windSpeed = 5;
        this.windDirection = 0;
        this.swayAngle = 0;
        this.init();
    }

    init() {
        const w = this.container.clientWidth;
        const h = this.container.clientHeight;

        this.scene = new THREE.Scene();
        this.scene.background = new THREE.Color(0x050810);
        this.scene.fog = new THREE.FogExp2(0x050810, 0.015);

        this.camera = new THREE.PerspectiveCamera(50, w / h, 0.1, 1000);
        this.camera.position.set(15, 12, 15);
        this.camera.lookAt(0, 5, 0);

        this.renderer = new THREE.WebGLRenderer({ antialias: true });
        this.renderer.setSize(w, h);
        this.renderer.setPixelRatio(window.devicePixelRatio);
        this.renderer.shadowMap.enabled = true;
        this.container.appendChild(this.renderer.domElement);

        this.addLights();
        this.addGrid();
        this.buildCartModel();
        this.buildVisionCone();
        this.buildTerrain();

        this.mouseDown = false;
        this.mouseX = 0;
        this.mouseY = 0;
        this.rotX = 0;
        this.rotY = 0;
        this.setupControls();

        window.addEventListener('resize', () => this.onResize());
        this.animate();
    }

    addLights() {
        const ambient = new THREE.AmbientLight(0x334466, 0.6);
        this.scene.add(ambient);

        const dirLight = new THREE.DirectionalLight(0xffeedd, 1.0);
        dirLight.position.set(10, 20, 10);
        dirLight.castShadow = true;
        this.scene.add(dirLight);

        const pointLight = new THREE.PointLight(0x3b82f6, 0.5, 50);
        pointLight.position.set(0, 15, 0);
        this.scene.add(pointLight);
    }

    addGrid() {
        const grid = new THREE.GridHelper(60, 60, 0x1a2332, 0x111827);
        this.scene.add(grid);
    }

    buildCartModel() {
        this.cartGroup = new THREE.Group();

        const woodMat = new THREE.MeshPhongMaterial({
            color: 0x8B6914,
            shininess: 30
        });
        const darkWoodMat = new THREE.MeshPhongMaterial({
            color: 0x5C4033,
            shininess: 20
        });
        const metalMat = new THREE.MeshPhongMaterial({
            color: 0x888899,
            shininess: 80
        });

        const baseGeo = new THREE.BoxGeometry(4, 0.3, 3);
        const baseMesh = new THREE.Mesh(baseGeo, darkWoodMat);
        baseMesh.position.y = 0.15;
        baseMesh.castShadow = true;
        this.cartGroup.add(baseMesh);

        for (let i = 0; i < 4; i++) {
            const wheelGeo = new THREE.CylinderGeometry(0.5, 0.5, 0.2, 16);
            const wheel = new THREE.Mesh(wheelGeo, woodMat);
            wheel.rotation.z = Math.PI / 2;
            const xPos = (i < 2 ? -1.5 : 1.5);
            const zPos = (i % 2 === 0 ? -1.2 : 1.2);
            wheel.position.set(xPos, 0.5, zPos);
            this.cartGroup.add(wheel);

            const spokeGeo = new THREE.BoxGeometry(0.8, 0.05, 0.05);
            for (let s = 0; s < 4; s++) {
                const spoke = new THREE.Mesh(spokeGeo, darkWoodMat);
                spoke.rotation.x = (s * Math.PI) / 4;
                spoke.position.copy(wheel.position);
                spoke.rotation.z = Math.PI / 2;
                spoke.rotation.y = (s * Math.PI) / 4;
                this.cartGroup.add(spoke);
            }
        }

        const mastGeo = new THREE.CylinderGeometry(0.12, 0.15, this.currentHeight, 8);
        const mast = new THREE.Mesh(mastGeo, darkWoodMat);
        mast.position.y = 0.3 + this.currentHeight / 2;
        mast.castShadow = true;
        mast.name = 'mast';
        this.cartGroup.add(mast);

        for (let i = 1; i <= 3; i++) {
            const braceGeo = new THREE.CylinderGeometry(0.04, 0.04, 3, 6);
            const brace = new THREE.Mesh(braceGeo, woodMat);
            const angle = (i * 2 * Math.PI) / 3;
            const braceH = this.currentHeight * 0.4;
            brace.position.set(
                Math.cos(angle) * 1.2,
                braceH,
                Math.sin(angle) * 1.2
            );
            brace.rotation.z = Math.cos(angle) * 0.3;
            brace.rotation.x = Math.sin(angle) * 0.3;
            brace.name = 'brace';
            this.cartGroup.add(brace);
        }

        const boomLength = 8;
        const pivotY = 0.3 + this.currentHeight * 0.85;
        const boomPivot = new THREE.Group();
        boomPivot.position.set(0, pivotY, 0);
        boomPivot.name = 'boomPivot';

        const boomGeo = new THREE.CylinderGeometry(0.06, 0.08, boomLength, 8);
        const boom = new THREE.Mesh(boomGeo, metalMat);
        boom.rotation.z = -Math.PI / 2;
        boom.position.x = boomLength / 2;
        boom.castShadow = true;
        boomPivot.add(boom);

        const boomCounterGeo = new THREE.CylinderGeometry(0.05, 0.06, 2, 8);
        const counterBoom = new THREE.Mesh(boomCounterGeo, metalMat);
        counterBoom.rotation.z = Math.PI / 2;
        counterBoom.position.x = -1;
        boomPivot.add(counterBoom);

        const counterWeightGeo = new THREE.BoxGeometry(0.6, 0.6, 0.6);
        const counterWeight = new THREE.Mesh(counterWeightGeo, darkWoodMat);
        counterWeight.position.set(-2, 0, 0);
        boomPivot.add(counterWeight);

        const ropeGeo = new THREE.CylinderGeometry(0.02, 0.02, 2, 6);
        const ropeMat = new THREE.MeshPhongMaterial({ color: 0xAA8844 });
        const rope1 = new THREE.Mesh(ropeGeo, ropeMat);
        rope1.position.set(boomLength - 0.5, -1.2, 0.3);
        boomPivot.add(rope1);
        const rope2 = new THREE.Mesh(ropeGeo, ropeMat);
        rope2.position.set(boomLength - 0.5, -1.2, -0.3);
        boomPivot.add(rope2);

        const basketGroup = new THREE.Group();
        basketGroup.position.set(boomLength, -2, 0);
        basketGroup.name = 'basket';

        const basketGeo = new THREE.BoxGeometry(1.5, 1.0, 1.5);
        const basketMat = new THREE.MeshPhongMaterial({
            color: 0xA0782C,
            transparent: true,
            opacity: 0.7,
            side: THREE.DoubleSide
        });
        const basket = new THREE.Mesh(basketGeo, basketMat);
        basket.position.y = -0.5;
        basketGroup.add(basket);

        const railGeo = new THREE.BoxGeometry(1.6, 0.08, 1.6);
        const rail = new THREE.Mesh(railGeo, woodMat);
        rail.position.y = 0.05;
        basketGroup.add(rail);

        boomPivot.add(basketGroup);
        this.cartGroup.add(boomPivot);

        this.buildWireframe(boomLength, pivotY);

        this.scene.add(this.cartGroup);
    }

    buildWireframe(boomLength, pivotY) {
        if (this.wireframeGroup) {
            this.cartGroup.remove(this.wireframeGroup);
        }

        this.wireframeGroup = new THREE.Group();
        this.wireframeGroup.name = 'wireframe';

        const lineMat = new THREE.LineBasicMaterial({
            color: 0x3b82f6,
            transparent: true,
            opacity: 0.6
        });

        const segments = 20;
        const segLength = boomLength / segments;

        for (let i = 0; i <= segments; i++) {
            const x = i * segLength;
            const topY = pivotY + 0.08;
            const botY = pivotY - 0.08;

            const vertGeo = new THREE.BufferGeometry();
            vertGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                x, topY, 0, x, botY, 0
            ], 3));
            this.wireframeGroup.add(new THREE.Line(vertGeo, lineMat));

            if (i < segments) {
                const topGeo = new THREE.BufferGeometry();
                topGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                    x, topY, 0, x + segLength, topY, 0
                ], 3));
                this.wireframeGroup.add(new THREE.Line(topGeo, lineMat));

                const botGeo = new THREE.BufferGeometry();
                botGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                    x, botY, 0, x + segLength, botY, 0
                ], 3));
                this.wireframeGroup.add(new THREE.Line(botGeo, lineMat));

                const diagGeo = new THREE.BufferGeometry();
                diagGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                    x, topY, 0, x + segLength, botY, 0
                ], 3));
                this.wireframeGroup.add(new THREE.Line(diagGeo, lineMat));
            }
        }

        const stressMat = new THREE.LineBasicMaterial({
            color: 0x10b981,
            transparent: true,
            opacity: 0.8
        });

        this.stressIndicators = [];
        for (let i = 0; i < segments; i++) {
            const x = (i + 0.5) * segLength;
            const indicatorGeo = new THREE.BufferGeometry();
            indicatorGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                x, pivotY, 0, x, pivotY + 0.5, 0
            ], 3));
            const indicator = new THREE.Line(indicatorGeo, stressMat.clone());
            this.stressIndicators.push(indicator);
            this.wireframeGroup.add(indicator);
        }

        this.cartGroup.add(this.wireframeGroup);
    }

    updateStressIndicators(beamElements) {
        if (!this.stressIndicators || !beamElements) return;

        beamElements.forEach((elem, i) => {
            if (i >= this.stressIndicators.length) return;
            const ratio = elem.stress / 8e6;
            const indicator = this.stressIndicators[i];
            const mat = indicator.material;

            if (ratio > 0.95) {
                mat.color.setHex(0xef4444);
            } else if (ratio > 0.8) {
                mat.color.setHex(0xf59e0b);
            } else {
                mat.color.setHex(0x10b981);
            }

            const positions = indicator.geometry.attributes.position.array;
            positions[4] = 0.08 + ratio * 0.8;
            positions[5] = 0;
            indicator.geometry.attributes.position.needsUpdate = true;
        });
    }

    buildVisionCone() {
        if (this.visionCone) {
            this.scene.remove(this.visionCone);
        }

        const height = this.currentHeight;
        const radius = Math.sqrt(2 * 6371000 * height) * 0.003;
        const coneHeight = height * 0.85;

        const coneGeo = new THREE.ConeGeometry(
            Math.max(0.1, radius),
            Math.max(0.1, coneHeight),
            36, 1, true
        );

        const coneMat = new THREE.MeshPhongMaterial({
            color: 0x06b6d4,
            transparent: true,
            opacity: 0.15,
            side: THREE.DoubleSide,
            depthWrite: false
        });

        this.visionCone = new THREE.Mesh(coneGeo, coneMat);
        this.visionCone.position.set(8, height * 0.85 + 0.3, 0);
        this.visionCone.rotation.x = Math.PI;

        const edgeMat = new THREE.LineBasicMaterial({
            color: 0x06b6d4,
            transparent: true,
            opacity: 0.4
        });
        const edgeGeo = new THREE.EdgesGeometry(coneGeo);
        const edgeLines = new THREE.LineSegments(edgeGeo, edgeMat);
        this.visionCone.add(edgeLines);

        this.scene.add(this.visionCone);
    }

    buildTerrain() {
        const size = 60;
        const segments = 60;
        const terrainGeo = new THREE.PlaneGeometry(size, size, segments, segments);
        terrainGeo.rotateX(-Math.PI / 2);

        const positions = terrainGeo.attributes.position.array;
        const colors = new Float32Array(positions.length);

        for (let i = 0; i < positions.length; i += 3) {
            const x = positions[i];
            const z = positions[i + 2];
            const elev = 0.8 * Math.sin(x * 0.2) * Math.cos(z * 0.2)
                + 0.4 * Math.sin(x * 0.4 + 1) * Math.cos(z * 0.3 + 2)
                + 0.2 * Math.random();
            positions[i + 1] = elev - 0.5;

            const h = (elev + 1) / 3;
            colors[i] = h * 0.2;
            colors[i + 1] = h * 0.5 + 0.1;
            colors[i + 2] = h * 0.2 + 0.05;
        }

        terrainGeo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
        terrainGeo.computeVertexNormals();

        const terrainMat = new THREE.MeshPhongMaterial({
            vertexColors: true,
            transparent: true,
            opacity: 0.6,
            flatShading: true
        });

        this.terrainMesh = new THREE.Mesh(terrainGeo, terrainMat);
        this.terrainMesh.position.y = -0.5;
        this.scene.add(this.terrainMesh);
    }

    setupControls() {
        const canvas = this.renderer.domElement;

        canvas.addEventListener('mousedown', (e) => {
            this.mouseDown = true;
            this.mouseX = e.clientX;
            this.mouseY = e.clientY;
        });

        canvas.addEventListener('mousemove', (e) => {
            if (!this.mouseDown) return;
            const dx = e.clientX - this.mouseX;
            const dy = e.clientY - this.mouseY;
            this.rotY += dx * 0.005;
            this.rotX += dy * 0.005;
            this.rotX = Math.max(-Math.PI / 3, Math.min(Math.PI / 3, this.rotX));
            this.mouseX = e.clientX;
            this.mouseY = e.clientY;
        });

        canvas.addEventListener('mouseup', () => { this.mouseDown = false; });
        canvas.addEventListener('mouseleave', () => { this.mouseDown = false; });

        canvas.addEventListener('wheel', (e) => {
            const dist = this.camera.position.length();
            const newDist = dist + e.deltaY * 0.01;
            this.camera.position.setLength(Math.max(5, Math.min(50, newDist)));
        });
    }

    updateHeight(h) {
        this.currentHeight = h;
        const mast = this.cartGroup.getObjectByName('mast');
        if (mast) {
            mast.scale.y = h / 10;
            mast.position.y = 0.3 + h / 2;
        }

        const boomPivot = this.cartGroup.getObjectByName('boomPivot');
        if (boomPivot) {
            boomPivot.position.y = 0.3 + h * 0.85;
        }

        this.buildVisionCone();
    }

    updateSimulation(data) {
        if (!data) return;

        const boomPivot = this.cartGroup.getObjectByName('boomPivot');
        if (boomPivot && data.totalDeflection) {
            const swayRad = Math.atan2(data.totalDeflection, 8) * 0.5;
            boomPivot.rotation.z = swayRad * Math.sin(Date.now() * 0.001);
        }

        if (data.beamElements) {
            this.updateStressIndicators(data.beamElements);
        }
    }

    animate() {
        this.animationId = requestAnimationFrame(() => this.animate());

        const time = Date.now() * 0.001;
        this.swayAngle = Math.sin(time * 0.5) * 0.01 * (1 + this.windSpeed * 0.02);

        const boomPivot = this.cartGroup.getObjectByName('boomPivot');
        if (boomPivot) {
            boomPivot.rotation.z += (this.swayAngle - boomPivot.rotation.z) * 0.05;
            boomPivot.rotation.x = Math.sin(time * 0.3) * 0.005 * this.windSpeed;
        }

        const basket = this.cartGroup.getObjectByName('basket');
        if (basket) {
            basket.rotation.y = Math.sin(time * 0.7) * 0.02 * (1 + this.windSpeed * 0.1);
        }

        if (this.visionCone) {
            this.visionCone.visible = this.showVisionCone;
            this.visionCone.material.opacity = 0.1 + Math.sin(time) * 0.03;
        }

        if (this.wireframeGroup) {
            this.wireframeGroup.visible = this.showWireframe;
        }

        if (this.terrainMesh) {
            this.terrainMesh.visible = this.showTerrain;
        }

        const dist = this.camera.position.length();
        this.camera.position.x = dist * Math.sin(this.rotY) * Math.cos(this.rotX);
        this.camera.position.y = dist * Math.sin(this.rotX) + 5;
        this.camera.position.z = dist * Math.cos(this.rotY) * Math.cos(this.rotX);
        this.camera.lookAt(0, 5, 0);

        this.renderer.render(this.scene, this.camera);
    }

    onResize() {
        const w = this.container.clientWidth;
        const h = this.container.clientHeight;
        this.camera.aspect = w / h;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(w, h);
    }

    destroy() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
        }
    }
}

window.NestCart3D = NestCart3D;
