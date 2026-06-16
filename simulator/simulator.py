#!/usr/bin/env python3
"""
巢车传感器模拟器 - MQTT + HTTP 双通道

支持两种上报方式：
1. MQTT (默认, docker-compose 使用): 主题 nestcart/sensor/{cart_id}/data
2. HTTP: POST 到 /api/carts/{cart_id}/sensor-data

可配置项（环境变量或命令行参数）：
- 高度：--height-base / HEIGHT_BASE (基础高度)
- 高度范围：--height-min / HEIGHT_MIN, --height-max / HEIGHT_MAX
- 地形区域：--terrain-region / TERRAIN_REGION
- 地形对高度的影响：--terrain-affect / TERRAIN_AFFECT (true/false)
"""

import argparse
import json
import math
import os
import random
import sys
import time
from datetime import datetime

try:
    import requests
except ImportError:
    print("请先安装依赖: pip install requests paho-mqtt")
    sys.exit(1)

try:
    import paho.mqtt.client as mqtt
except ImportError:
    mqtt = None


class TerrainProfile:
    FLAT = 'flat'
    HILLY = 'hilly'
    MOUNTAIN = 'mountain'
    VALLEY = 'valley'

    PROFILES = {
        'flat': {'base_elev': 0, 'amplitude': 0, 'wavelength': 100},
        'hilly': {'base_elev': 20, 'amplitude': 30, 'wavelength': 200},
        'mountain': {'base_elev': 100, 'amplitude': 150, 'wavelength': 300},
        'valley': {'base_elev': -50, 'amplitude': 80, 'wavelength': 250},
    }

    @classmethod
    def get_elevation(cls, profile, distance_m, tick):
        p = cls.PROFILES.get(profile, cls.PROFILES['flat'])
        phase = tick * 0.02 + distance_m / p['wavelength']
        return p['base_elev'] + p['amplitude'] * (
            0.6 * math.sin(phase) +
            0.3 * math.sin(phase * 2.3 + 1.5) +
            0.1 * math.sin(phase * 4.7)
        )


class NestCartSimulator:

    def __init__(self, **kwargs):
        self.api_url = kwargs.get('api_url', 'http://localhost:8080').rstrip('/')
        self.cart_id = kwargs['cart_id']
        self.interval = kwargs.get('interval', 60)
        self.tick = 0

        self.base_height = kwargs.get('height_base', 10.0)
        self.height_min = kwargs.get('height_min', 4.0)
        self.height_max = kwargs.get('height_max', 18.0)
        self.current_height = self.base_height
        self.height_target = self.base_height

        self.base_wind_speed = kwargs.get('wind_base', 3.0)
        self.wind_phase = random.uniform(0, 2 * math.pi)

        self.terrain_region = kwargs.get('terrain_region', 'default_battlefield')
        self.terrain_profile = kwargs.get('terrain_profile', TerrainProfile.HILLY)
        self.terrain_affect = kwargs.get('terrain_affect', False)

        self.stress_limit = kwargs.get('stress_limit', 8.0e6)
        self.sway_limit = kwargs.get('sway_limit', 0.5)
        self.boom_length = kwargs.get('boom_length', 8.0)
        self.boom_area = kwargs.get('boom_area', 0.01)
        self.boom_inertia = kwargs.get('boom_inertia', 8.33e-6)
        self.boom_elastic = kwargs.get('boom_elastic', 1.2e10)
        self.basket_weight = kwargs.get('basket_weight', 150.0)

        self.use_mqtt = kwargs.get('use_mqtt', True)
        self.mqtt_client = None
        self.mqtt_broker = kwargs.get('mqtt_broker', 'localhost')
        self.mqtt_port = kwargs.get('mqtt_port', 1883)
        self.mqtt_username = kwargs.get('mqtt_username')
        self.mqtt_password = kwargs.get('mqtt_password')
        self.mqtt_topic_template = kwargs.get('mqtt_topic_template', 'nestcart/sensor/{cart_id}/data')
        self.mqtt_connected = False

    def _on_mqtt_connect(self, client, userdata, flags, rc):
        if rc == 0:
            self.mqtt_connected = True
            print(f'  [MQTT] 已连接到 {self.mqtt_broker}:{self.mqtt_port}')
        else:
            self.mqtt_connected = False
            print(f'  [MQTT] 连接失败, code={rc}')

    def _on_mqtt_disconnect(self, client, userdata, rc):
        self.mqtt_connected = False
        print(f'  [MQTT] 连接已断开, code={rc}')

    def connect_mqtt(self):
        if not mqtt or not self.use_mqtt:
            return
        client_id = f'simulator-{self.cart_id[:8]}-{int(time.time())}'
        self.mqtt_client = mqtt.Client(client_id=client_id, clean_session=True)
        if self.mqtt_username:
            self.mqtt_client.username_pw_set(self.mqtt_username, self.mqtt_password)
        self.mqtt_client.on_connect = self._on_mqtt_connect
        self.mqtt_client.on_disconnect = self._on_mqtt_disconnect
        try:
            self.mqtt_client.connect(self.mqtt_broker, self.mqtt_port, keepalive=30)
            self.mqtt_client.loop_start()
        except Exception as e:
            print(f'  [MQTT] 连接异常: {e}')

    def generate_sensor_data(self):
        self.tick += 1
        self.wind_phase += 0.1

        wind_speed = self.base_wind_speed + 5.0 * math.sin(self.wind_phase) + random.gauss(0, 1.5)
        wind_speed = max(0, wind_speed)
        wind_direction = (45 + 30 * math.sin(self.wind_phase * 0.3) + random.gauss(0, 10)) % 360

        if self.tick % 10 == 0:
            self.height_target = random.uniform(self.height_min, self.height_max)
        height_diff = self.height_target - self.current_height
        self.current_height += height_diff * 0.1 + random.gauss(0, 0.05)
        self.current_height = max(self.height_min, min(self.height_max, self.current_height))

        effective_height = self.current_height
        if self.terrain_affect:
            terrain_elev = TerrainProfile.get_elevation(self.terrain_profile, self.current_height * 5, self.tick)
            effective_height = self.current_height + terrain_elev
            effective_height = max(self.height_min, min(self.height_max + 50, effective_height))

        gravity_stress = (self.basket_weight * 9.81 * self.boom_length
                          * math.sqrt(self.boom_area / math.pi) / self.boom_inertia)
        wind_force_per_length = (0.5 * 1.225 * wind_speed ** 2 * 1.2
                                 * math.sqrt(self.boom_area) * 2)
        wind_moment = wind_force_per_length * self.boom_length ** 2 / 2
        wind_stress = wind_moment * math.sqrt(self.boom_area / math.pi) / self.boom_inertia
        total_stress = gravity_stress + wind_stress
        boom_stress = total_stress * (1 + random.gauss(0, 0.05))
        boom_stress = max(0, boom_stress)

        gravity_deflection = ((self.basket_weight * 9.81 * self.boom_length ** 3)
                              / (3 * self.boom_elastic * self.boom_inertia))
        wind_deflection = ((wind_force_per_length * self.boom_length ** 4)
                           / (8 * self.boom_elastic * self.boom_inertia))
        basket_sway = (gravity_deflection + wind_deflection) * (1 + random.gauss(0, 0.1))
        basket_sway = max(0, basket_sway)

        horizon_dist = math.sqrt(2 * 6371000 * effective_height)
        observation_distance = horizon_dist * random.uniform(0.6, 0.95)

        temperature = 20 + 5 * math.sin(self.wind_phase * 0.01) + random.gauss(0, 2)

        data = {
            'cartId': self.cart_id,
            'boomStress': round(boom_stress, 2),
            'basketSway': round(basket_sway, 4),
            'height': round(effective_height, 2),
            'observationDistance': round(observation_distance, 1),
            'windSpeed': round(wind_speed, 2),
            'windDirection': round(wind_direction, 1),
            'temperature': round(temperature, 1)
        }
        return data

    def send_mqtt(self, data):
        if not self.use_mqtt or not self.mqtt_client or not self.mqtt_connected:
            return False
        try:
            topic = self.mqtt_topic_template.format(cart_id=self.cart_id)
            payload = json.dumps(data, ensure_ascii=False)
            result = self.mqtt_client.publish(topic, payload, qos=1)
            return result.rc == mqtt.MQTT_ERR_SUCCESS
        except Exception as e:
            print(f'  [MQTT] 发送失败: {e}')
            return False

    def send_http(self, data):
        url = f'{self.api_url}/api/carts/{self.cart_id}/sensor-data'
        try:
            resp = requests.post(url, json=data, timeout=10)
            return resp.status_code == 200
        except requests.exceptions.ConnectionError:
            print(f'  [HTTP] 无法连接到 {self.api_url}')
            return False
        except Exception as e:
            print(f'  [HTTP] 错误: {e}')
            return False

    def run(self):
        print('=' * 65)
        print('  🏛️  巢车传感器模拟器')
        print(f'  巢车ID:        {self.cart_id}')
        print(f'  上报间隔:      {self.interval} 秒')
        print(f'  基础高度:      {self.base_height} m (范围 {self.height_min}~{self.height_max})')
        print(f'  基础风速:      {self.base_wind_speed} m/s')
        print(f'  地形区域:      {self.terrain_region} ({self.terrain_profile})')
        print(f'  地形影响高度:  {"是" if self.terrain_affect else "否"}')
        if self.use_mqtt:
            print(f'  MQTT Broker:   {self.mqtt_broker}:{self.mqtt_port}')
            print(f'  MQTT 主题:     {self.mqtt_topic_template.format(cart_id=self.cart_id)}')
            self.connect_mqtt()
        print(f'  HTTP API:      {self.api_url}')
        print('=' * 65)
        print()

        while True:
            data = self.generate_sensor_data()
            timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

            stress_ratio = data['boomStress'] / self.stress_limit
            sway_ratio = data['basketSway'] / self.sway_limit
            status = '正常'
            if stress_ratio > 0.95 or sway_ratio > 0.9:
                status = '⚠️ 危险'
            elif stress_ratio > 0.8 or sway_ratio > 0.7:
                status = '⚡ 警告'

            print(f'[{timestamp}] Tick #{self.tick:04d} [{status}]')
            print(f'  应力: {data["boomStress"]/1e6:.2f} MPa ({stress_ratio*100:5.1f}%)')
            print(f'  晃动: {data["basketSway"]*1000:5.1f} mm   ({sway_ratio*100:5.1f}%)')
            print(f'  高度: {data["height"]:5.1f} m   观察距离: {data["observationDistance"]:.0f} m')
            print(f'  风速: {data["windSpeed"]:4.1f} m/s   风向: {data["windDirection"]:.0f}°  温度: {data["temperature"]:.1f}°C')

            mqtt_ok, http_ok = False, False
            if self.use_mqtt:
                mqtt_ok = self.send_mqtt(data)
                print(f'  MQTT: {"✅" if mqtt_ok else "❌"}')
            http_ok = self.send_http(data)
            print(f'  HTTP: {"✅" if http_ok else "❌"}')
            print()

            time.sleep(self.interval)


def parse_args():
    parser = argparse.ArgumentParser(description='巢车传感器模拟器 (支持 MQTT + HTTP)')
    parser.add_argument('--api-url', default=os.getenv('API_URL', 'http://localhost:8080'),
                        help='后端 API 地址 (默认: http://localhost:8080)')
    parser.add_argument('--cart-id', default=os.getenv('CART_ID', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'),
                        help='巢车 ID')
    parser.add_argument('--interval', type=int, default=int(os.getenv('INTERVAL', '60')),
                        help='上报间隔 (秒)')
    parser.add_argument('--wind-base', type=float, default=float(os.getenv('WIND_BASE', '3.0')),
                        help='基础风速 m/s')
    parser.add_argument('--height-base', type=float, default=float(os.getenv('HEIGHT_BASE', '10.0')),
                        help='基础高度 m')
    parser.add_argument('--height-min', type=float, default=float(os.getenv('HEIGHT_MIN', '4.0')),
                        help='最小高度 m')
    parser.add_argument('--height-max', type=float, default=float(os.getenv('HEIGHT_MAX', '18.0')),
                        help='最大高度 m')
    parser.add_argument('--terrain-region', default=os.getenv('TERRAIN_REGION', 'default_battlefield'),
                        help='地形区域名称')
    parser.add_argument('--terrain-profile',
                        default=os.getenv('TERRAIN_PROFILE', TerrainProfile.HILLY),
                        choices=list(TerrainProfile.PROFILES.keys()),
                        help='地形轮廓: flat/hilly/mountain/valley')
    parser.add_argument('--terrain-affect', action='store_true',
                        default=os.getenv('TERRAIN_AFFECT', 'false').lower() == 'true',
                        help='地形是否影响有效观察高度')
    parser.add_argument('--no-mqtt', action='store_true',
                        default=os.getenv('DISABLE_MQTT', 'false').lower() == 'true',
                        help='禁用 MQTT, 仅使用 HTTP')
    parser.add_argument('--mqtt-broker', default=os.getenv('MQTT_BROKER', 'localhost'),
                        help='MQTT Broker 地址')
    parser.add_argument('--mqtt-port', type=int, default=int(os.getenv('MQTT_PORT', '1883')),
                        help='MQTT Broker 端口')
    parser.add_argument('--mqtt-username', default=os.getenv('MQTT_USERNAME'),
                        help='MQTT 用户名')
    parser.add_argument('--mqtt-password', default=os.getenv('MQTT_PASSWORD'),
                        help='MQTT 密码')
    return parser.parse_args()


def main():
    args = parse_args()
    sim = NestCartSimulator(
        api_url=args.api_url,
        cart_id=args.cart_id,
        interval=args.interval,
        wind_base=args.wind_base,
        height_base=args.height_base,
        height_min=args.height_min,
        height_max=args.height_max,
        terrain_region=args.terrain_region,
        terrain_profile=args.terrain_profile,
        terrain_affect=args.terrain_affect,
        use_mqtt=not args.no_mqtt,
        mqtt_broker=args.mqtt_broker,
        mqtt_port=args.mqtt_port,
        mqtt_username=args.mqtt_username,
        mqtt_password=args.mqtt_password,
    )
    try:
        sim.run()
    except KeyboardInterrupt:
        print('\n模拟器已停止')


if __name__ == '__main__':
    main()
