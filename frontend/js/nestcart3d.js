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

        this.transparentObjects = [];
        this.opaqueScene = null;

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

        this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
        this.renderer.setSize(w, h);
        this.renderer.setPixelRatio(window.devicePixelRatio);
        this.renderer.shadowMap.enabled = true;
        this.renderer.autoClear = true;
        this.renderer.sortObjects = false;
        this.container.appendChild(this.renderer.domElement);

        this.setupOIT(w, h);

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

    createOITMaterial(color, opacity, useFresnel = true) {
        return new THREE.ShaderMaterial({
            uniforms: {
                uColor: { value: new THREE.Color(color) },
                uOpacity: { value: opacity }
            },
            vertexShader: `
                varying vec3 vNormal;
                varying vec3 vViewDir;
                varying float vDepth;
                void main() {
                    vNormal = normalize(normalMatrix * normal);
                    vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
                    vViewDir = normalize(-mvPosition.xyz);
                    vDepth = -mvPosition.z;
                    gl_Position = projectionMatrix * mvPosition;
                }
            `,
            fragmentShader: `
                uniform vec3 uColor;
                uniform float uOpacity;
                varying vec3 vNormal;
                varying vec3 vViewDir;
                varying float vDepth;

                void main() {
                    float alpha = uOpacity;
                    float fresnel = pow(1.0 - abs(dot(vNormal, vViewDir)), 2.0);
                    alpha = uOpacity * (0.6 + fresnel * 0.4);

                    float z = gl_FragCoord.z;
                    float weight = pow(1.0 - z, 4.0) * 100.0;

                    vec3 accumColor = uColor * alpha * weight;
                    float reveal = alpha * weight;

                    gl_FragColor = vec4(accumColor, reveal);
                }
            `,
            transparent: true,
            blending: THREE.AdditiveBlending,
            depthWrite: false,
            side: THREE.DoubleSide
        });
    }

    createOITLineMaterial(color, opacity) {
        return new THREE.ShaderMaterial({
            uniforms: {
                uColor: { value: new THREE.Color(color) },
                uOpacity: { value: opacity }
            },
            vertexShader: `
                varying float vDepth;
                void main() {
                    vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
                    vDepth = -mvPosition.z;
                    gl_Position = projectionMatrix * mvPosition;
                }
            `,
            fragmentShader: `
                uniform vec3 uColor;
                uniform float uOpacity;
                varying float vDepth;

                void main() {
                    float alpha = uOpacity;

                    float z = gl_FragCoord.z;
                    float weight = pow(1.0 - z, 4.0) * 100.0;

                    vec3 accumColor = uColor * alpha * weight;
                    float reveal = alpha * weight;

                    gl_FragColor = vec4(accumColor, reveal);
                }
            `,
            transparent: true,
            blending: THREE.AdditiveBlending,
            depthWrite: false
        });
    }

    setupOIT(w, h) {
        const dpr = window.devicePixelRatio || 1;
        const width = Math.floor(w * dpr);
        const height = Math.floor(h * dpr);

        this.opaqueTarget = new THREE.WebGLRenderTarget(width, height, {
            minFilter: THREE.LinearFilter,
            magFilter: THREE.LinearFilter,
            format: THREE.RGBAFormat,
            type: THREE.HalfFloatType,
            depthBuffer: true
        });

        this.accumulateTarget = new THREE.WebGLRenderTarget(width, height, {
            minFilter: THREE.LinearFilter,
            magFilter: THREE.LinearFilter,
            format: THREE.RGBAFormat,
            type: THREE.HalfFloatType,
            depthBuffer: false
        });

        this.oitCompositeMaterial = new THREE.ShaderMaterial({
            uniforms: {
                tAccum: { value: this.accumulateTarget.texture },
                tOpaque: { value: this.opaqueTarget.texture }
            },
            vertexShader: `
                varying vec2 vUv;
                void main() {
                    vUv = uv;
                    gl_Position = vec4(position.xy, 0.0, 1.0);
                }
            `,
            fragmentShader: `
                uniform sampler2D tAccum;
                uniform sampler2D tOpaque;
                varying vec2 vUv;

                void main() {
                    vec4 accum = texture2D(tAccum, vUv);
                    float revealage = accum.a;
                    vec4 opaqueColor = texture2D(tOpaque, vUv);

                    vec3 transColor = accum.rgb / max(revealage, 0.001);

                    vec3 finalColor = mix(opaqueColor.rgb, transColor, 1.0 - revealage);

                    gl_FragColor = vec4(finalColor, 1.0);
                }
            `,
            depthTest: false,
            depthWrite: false
        });

        this.orthoScene = new THREE.Scene();
        const quadGeo = new THREE.PlaneGeometry(2, 2);
        this.quad = new THREE.Mesh(quadGeo, this.oitCompositeMaterial);
        this.orthoScene.add(this.quad);

        this.orthoCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
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
        baseMesh.renderOrder = 0;
        this.cartGroup.add(baseMesh);

        for (let i = 0; i < 4; i++) {
            const wheelGeo = new THREE.CylinderGeometry(0.5, 0.5, 0.2, 16);
            const wheel = new THREE.Mesh(wheelGeo, woodMat);
            wheel.rotation.z = Math.PI / 2;
            const xPos = (i < 2 ? -1.5 : 1.5);
            const zPos = (i % 2 === 0 ? -1.2 : 1.2);
            wheel.position.set(xPos, 0.5, zPos);
            wheel.renderOrder = 0;
            this.cartGroup.add(wheel);
        }

        const mastGeo = new THREE.CylinderGeometry(0.12, 0.15, this.currentHeight, 8);
        const mast = new THREE.Mesh(mastGeo, darkWoodMat);
        mast.position.y = 0.3 + this.currentHeight / 2;
        mast.castShadow = true;
        mast.name = 'mast';
        mast.renderOrder = 0;
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
            brace.renderOrder = 0;
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
        boom.renderOrder = 0;
        boomPivot.add(boom);

        const boomCounterGeo = new THREE.CylinderGeometry(0.05, 0.06, 2, 8);
        const counterBoom = new THREE.Mesh(boomCounterGeo, metalMat);
        counterBoom.rotation.z = Math.PI / 2;
        counterBoom.position.x = -1;
        counterBoom.renderOrder = 0;
        boomPivot.add(counterBoom);

        const counterWeightGeo = new THREE.BoxGeometry(0.6, 0.6, 0.6);
        const counterWeight = new THREE.Mesh(counterWeightGeo, darkWoodMat);
        counterWeight.position.set(-2, 0, 0);
        counterWeight.renderOrder = 0;
        boomPivot.add(counterWeight);

        const ropeGeo = new THREE.CylinderGeometry(0.02, 0.02, 2, 6);
        const ropeMat = new THREE.MeshPhongMaterial({ color: 0xAA8844 });
        const rope1 = new THREE.Mesh(ropeGeo, ropeMat);
        rope1.position.set(boomLength - 0.5, -1.2, 0.3);
        rope1.renderOrder = 0;
        boomPivot.add(rope1);
        const rope2 = new THREE.Mesh(ropeGeo, ropeMat);
        rope2.position.set(boomLength - 0.5, -1.2, -0.3);
        rope2.renderOrder = 0;
        boomPivot.add(rope2);

        const basketGroup = new THREE.Group();
        basketGroup.position.set(boomLength, -2, 0);
        basketGroup.name = 'basket';

        const basketGeo = new THREE.BoxGeometry(1.5, 1.0, 1.5);
        const basketMat = this.createOITMaterial(0xA0782C, 0.5);
        const basket = new THREE.Mesh(basketGeo, basketMat);
        basket.position.y = -0.5;
        basket.renderOrder = 10;
        basketGroup.add(basket);
        this.registerTransparent(basket);

        const railGeo = new THREE.BoxGeometry(1.6, 0.08, 1.6);
        const rail = new THREE.Mesh(railGeo, woodMat);
        rail.position.y = 0.05;
        rail.renderOrder = 0;
        basketGroup.add(rail);

        boomPivot.add(basketGroup);
        this.cartGroup.add(boomPivot);

        this.buildWireframe(boomLength, pivotY);

        this.scene.add(this.cartGroup);
    }

    buildWireframe(boomLength, pivotY) {
        if (this.wireframeGroup) {
            this.wireframeGroup.traverse(obj => {
                if (obj.isLine) {
                    const idx = this.transparentObjects.indexOf(obj);
                    if (idx >= 0) this.transparentObjects.splice(idx, 1);
                }
            });
            this.cartGroup.remove(this.wireframeGroup);
        }

        this.wireframeGroup = new THREE.Group();
        this.wireframeGroup.name = 'wireframe';

        const lineMat = this.createOITLineMaterial(0x3b82f6, 0.6);

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
            const line1 = new THREE.Line(vertGeo, lineMat);
            line1.renderOrder = 5;
            this.wireframeGroup.add(line1);

            if (i < segments) {
                const topGeo = new THREE.BufferGeometry();
                topGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                    x, topY, 0, x + segLength, topY, 0
                ], 3));
                const line2 = new THREE.Line(topGeo, lineMat);
                line2.renderOrder = 5;
                this.wireframeGroup.add(line2);

                const botGeo = new THREE.BufferGeometry();
                botGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                    x, botY, 0, x + segLength, botY, 0
                ], 3));
                const line3 = new THREE.Line(botGeo, lineMat);
                line3.renderOrder = 5;
                this.wireframeGroup.add(line3);

                const diagGeo = new THREE.BufferGeometry();
                diagGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                    x, topY, 0, x + segLength, botY, 0
                ], 3));
                const line4 = new THREE.Line(diagGeo, lineMat);
                line4.renderOrder = 5;
                this.wireframeGroup.add(line4);
            }
        }

        const stressMat = this.createOITLineMaterial(0x10b981, 0.8);

        this.stressIndicators = [];
        for (let i = 0; i < segments; i++) {
            const x = (i + 0.5) * segLength;
            const indicatorGeo = new THREE.BufferGeometry();
            indicatorGeo.setAttribute('position', new THREE.Float32BufferAttribute([
                x, pivotY, 0, x, pivotY + 0.5, 0
            ], 3));
            const indicator = new THREE.Line(indicatorGeo, stressMat.clone());
            indicator.renderOrder = 5;
            this.stressIndicators.push(indicator);
            this.wireframeGroup.add(indicator);
            this.registerTransparent(indicator);
        }

        this.wireframeGroup.traverse(obj => {
            if (obj.isLine && obj !== this.stressIndicators.find(i => i === obj)) {
                this.registerTransparent(obj);
            }
        });

        this.cartGroup.add(this.wireframeGroup);
    }

    registerTransparent(mesh) {
        this.transparentObjects.push(mesh);
    }

    buildVisionCone() {
        if (this.visionCone) {
            this.visionCone.traverse(obj => {
                const idx = this.transparentObjects.indexOf(obj);
                if (idx >= 0) this.transparentObjects.splice(idx, 1);
            });
            this.scene.remove(this.visionCone);
        }

        const height = this.currentHeight;
        const radius = Math.sqrt(2 * 6371000 * height) * 0.003;
        const coneHeight = height * 0.85;

        const coneGeo = new THREE.ConeGeometry(
            Math.max(0.1, radius),
            Math.max(0.1, coneHeight),
            64, 6, true
        );

        const coneMat = this.createOITMaterial(0x06b6d4, 0.18);
        this.visionCone = new THREE.Mesh(coneGeo, coneMat);
        this.visionCone.position.set(8, height * 0.85 + 0.3, 0);
        this.visionCone.rotation.x = Math.PI;
        this.visionCone.renderOrder = 100;
        this.registerTransparent(this.visionCone);

        const edgeMat = this.createOITLineMaterial(0x06b6d4, 0.3);
        const edgeGeo = new THREE.EdgesGeometry(coneGeo);
        const edgeLines = new THREE.LineSegments(edgeGeo, edgeMat);
        edgeLines.renderOrder = 101;
        this.visionCone.add(edgeLines);
        this.registerTransparent(edgeLines);

        const innerConeGeo = new THREE.ConeGeometry(
            Math.max(0.05, radius * 0.5),
            Math.max(0.1, coneHeight),
            32, 4, true
        );
        const innerConeMat = this.createOITMaterial(0xffffff, 0.06);
        const innerCone = new THREE.Mesh(innerConeGeo, innerConeMat);
        innerCone.renderOrder = 99;
        this.visionCone.add(innerCone);
        this.registerTransparent(innerCone);

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
        this.terrainMesh.renderOrder = 0;
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
            boomPivot.userData.targetSway = swayRad;
        }

        if (data.beamElements) {
            this.updateStressIndicators(data.beamElements);
        }
    }

    updateStressIndicators(beamElements) {
        if (!this.stressIndicators || !beamElements) return;

        beamElements.forEach((elem, i) => {
            if (i >= this.stressIndicators.length) return;
            const ratio = elem.stress / 8e6;
            const indicator = this.stressIndicators[i];
            const mat = indicator.material;

            let colorHex = 0x10b981;
            if (ratio > 0.95) {
                colorHex = 0xef4444;
            } else if (ratio > 0.8) {
                colorHex = 0xf59e0b;
            }

            if (mat.uniforms && mat.uniforms.uColor) {
                mat.uniforms.uColor.value.setHex(colorHex);
            } else if (mat.color) {
                mat.color.setHex(colorHex);
            }

            const positions = indicator.geometry.attributes.position.array;
            positions[4] = 0.08 + ratio * 0.8;
            positions[5] = 0;
            indicator.geometry.attributes.position.needsUpdate = true;
        });
    }

    renderOIT() {
        const prevAutoClear = this.renderer.autoClear;
        const prevSortObjects = this.renderer.sortObjects;
        this.renderer.sortObjects = false;

        this.renderer.setRenderTarget(this.opaqueTarget);
        this.renderer.setClearColor(0x050810, 1);
        this.renderer.clear();

        this.transparentObjects.forEach(mesh => {
            mesh.userData.wasVisible = mesh.visible;
            mesh.visible = false;
        });

        this.renderer.autoClear = true;
        this.renderer.render(this.scene, this.camera);

        this.transparentObjects.forEach(mesh => {
            mesh.visible = mesh.userData.wasVisible !== false;
        });

        this.renderer.setRenderTarget(this.accumulateTarget);
        this.renderer.setClearColor(0, 0, 0, 0);
        this.renderer.clear();

        this.renderer.autoClear = false;
        this.renderer.setBlending(THREE.AdditiveBlending);

        this.transparentObjects.forEach(mesh => {
            if (mesh.visible && mesh.material && mesh.material.uniforms) {
                this.renderer.render(mesh, this.camera);
            }
        });

        this.renderer.setBlending(THREE.NormalBlending);
        this.renderer.autoClear = prevAutoClear;
        this.renderer.sortObjects = prevSortObjects;

        this.renderer.setRenderTarget(null);
        this.renderer.setClearColor(0x050810, 1);
        this.renderer.render(this.orthoScene, this.orthoCamera);
    }

    animate() {
        this.animationId = requestAnimationFrame(() => this.animate());

        const time = Date.now() * 0.001;
        this.swayAngle = Math.sin(time * 0.5) * 0.01 * (1 + this.windSpeed * 0.02);

        const boomPivot = this.cartGroup && this.cartGroup.getObjectByName('boomPivot');
        if (boomPivot) {
            const targetSway = boomPivot.userData.targetSway || this.swayAngle;
            boomPivot.rotation.z += (targetSway * Math.sin(time) - boomPivot.rotation.z) * 0.05;
            boomPivot.rotation.x = Math.sin(time * 0.3) * 0.005 * this.windSpeed;
        }

        const basket = this.cartGroup && this.cartGroup.getObjectByName('basket');
        if (basket) {
            basket.rotation.y = Math.sin(time * 0.7) * 0.02 * (1 + this.windSpeed * 0.1);
        }

        if (this.visionCone) {
            this.visionCone.visible = this.showVisionCone;
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

        this.renderOIT();
    }

    onResize() {
        const w = this.container.clientWidth;
        const h = this.container.clientHeight;
        this.camera.aspect = w / h;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(w, h);

        const dpr = window.devicePixelRatio || 1;
        const width = Math.floor(w * dpr);
        const height = Math.floor(h * dpr);

        this.opaqueTarget.setSize(width, height);
        this.accumulateTarget.setSize(width, height);
        this.revealageTarget.setSize(width, height);
    }

    destroy() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
        }
    }
}

window.NestCart3D = NestCart3D;
